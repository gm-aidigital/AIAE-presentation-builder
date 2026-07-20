package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Claude-backed line-item disambiguation. Activated only when {@code ANTHROPIC_API_KEY} is set;
 * otherwise {@link StubLineItemMatchAssistant} is the sole candidate.
 *
 * <p>Sends the ambiguous tactics and candidate line items to Claude and asks for a
 * tactic-number&rarr;line-item-id JSON map. Every returned pair is re-validated against the inputs
 * (known tactic number, known id, same channel, no id reused) before it is trusted, and any
 * error/timeout/parse failure yields an empty map so matching degrades to manual drag-and-drop.
 */
@Slf4j
@Component
@Primary
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class RealLineItemMatchAssistant implements LineItemMatchAssistant {

	private static final int MAX_TOKENS = 1024;
	private static final int TIMEOUT_SECONDS = 30;
	private static final int MAX_CONTEXT_CHARS = 300;

	/**
	 * Token budget for the whole disambiguation prompt. The candidate list is every unassigned line item in
	 * the ambiguous channels, which on a large advertiser runs to thousands of rows — enough on its own to
	 * push the request past the model's context window. Candidates are dropped until the prompt fits (see
	 * {@link #fitOptions}); tactics whose real line item fell outside the budget simply stay unmatched and
	 * land in the manual drag-and-drop, which is what already happens when the assistant is not confident.
	 */
	private static final int PROMPT_MAX_TOKENS = 2000;

	private final AnthropicMessagesClient messagesClient;
	private final PromptTokenEstimator tokenEstimator;

	public RealLineItemMatchAssistant(AnthropicMessagesClient messagesClient, PromptTokenEstimator tokenEstimator) {
		this.messagesClient = messagesClient;
		this.tokenEstimator = tokenEstimator;
	}

	@Override
	public Map<Integer, String> match(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		if (tactics == null || tactics.isEmpty() || options == null || options.isEmpty()) {
			return Map.of();
		}
		List<LineItemMatchOption> fitted = fitOptions(tactics, options);
		if (fitted.isEmpty()) {
			return Map.of();
		}
		String prompt = buildPrompt(tactics, fitted);
		JsonNode parsed = messagesClient.callJsonObject(prompt, MAX_TOKENS, TIMEOUT_SECONDS, "LineItemMatch", false);
		if (parsed == null) {
			return Map.of();
		}
		return parseAssignments(parsed, tactics, options);
	}

	/**
	 * Builds the disambiguation prompt: the tactic list, the line-item list, the same-channel and
	 * one-to-one rules, and a strict JSON-only output contract.
	 *
	 * @param tactics the ambiguous tactics to place
	 * @param options the candidate line items
	 * @return the full user prompt
	 */
	String buildPrompt(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		StringBuilder tb = new StringBuilder();
		for (LineItemMatchTactic t : tactics) {
			tb.append(renderTactic(t));
		}
		StringBuilder ob = new StringBuilder();
		for (LineItemMatchOption o : options) {
			ob.append(renderOption(o));
		}
		return "You match media-plan tactics to BigQuery line items. Each tactic and each line item shows its "
				+ "channel in [brackets].\n\n"
				+ "TACTICS (media plan):\n" + tb + "\n"
				+ "LINE ITEMS (BigQuery):\n" + ob + "\n"
				+ "Rules:\n"
				+ "- Only assign a line item to a tactic in the SAME channel.\n"
				+ "- Match on meaning between the tactic context and the line-item naming tail — e.g. "
				+ "'Whitelist Strategy' ↔ 'WhiteList', 'Grapevine Vintage Railroad' ↔ 'GVRR', "
				+ "'3P, Contextual' ↔ 'Contextual', 'Evergreen' ↔ 'Evergreen-Branded'.\n"
				+ "- Each line item id maps to at most one tactic, and each tactic to at most one id.\n"
				+ "- Omit a tactic entirely when you are not confident.\n"
				+ "- Return ONLY a JSON object mapping tactic number (string) to line item id (string) — no markdown, "
				+ "no backticks, no explanation.\n"
				+ "Example: {\"1\":\"616641\",\"2\":\"616642\"}";
	}

	/**
	 * Renders one tactic's prompt line.
	 *
	 * @param tactic the ambiguous tactic
	 * @return the rendered line, newline included
	 */
	String renderTactic(LineItemMatchTactic tactic) {
		return "  #" + tactic.tacticNum()
				+ " [" + tactic.channel() + "] "
				+ tactic.name()
				+ " — " + clip(tactic.context())
				+ "\n";
	}

	/**
	 * Renders one candidate line item's prompt line.
	 *
	 * @param option the candidate line item
	 * @return the rendered line, newline included
	 */
	String renderOption(LineItemMatchOption option) {
		return "  " + option.id()
				+ " [" + option.channel() + "] "
				+ clip(option.naming())
				+ "\n";
	}

	/**
	 * Selects the candidate line items that fit inside {@link #PROMPT_MAX_TOKENS}, most promising first.
	 *
	 * <p>Candidates are ranked by how many words their naming shares with a same-channel tactic's name and
	 * context — the very signal the model is asked to weigh — so when a big advertiser's channel carries
	 * thousands of line items, the ones actually worth considering survive the cut and the long tail of
	 * unrelated inventory is what gets dropped. The whole candidate list is kept when it already fits.
	 *
	 * @param tactics the ambiguous tactics being placed
	 * @param options every unassigned candidate line item in those tactics' channels
	 * @return the candidates to send, in ranked order; empty when not even one fits
	 */
	List<LineItemMatchOption> fitOptions(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		int remaining = tokenEstimator.maxCharsFor(PROMPT_MAX_TOKENS) - buildPrompt(tactics, List.of()).length();
		if (remaining <= 0) {
			return List.of();
		}
		Map<String, Set<String>> channelWords = new LinkedHashMap<>();
		for (LineItemMatchTactic tactic : tactics) {
			channelWords.computeIfAbsent(tactic.channel(), channel -> new HashSet<>())
					.addAll(words(tactic.name() + " " + clip(tactic.context())));
		}
		List<LineItemMatchOption> ranked = new ArrayList<>(options);
		ranked.sort(Comparator.comparingInt((LineItemMatchOption o) -> -relevance(o, channelWords)));

		List<LineItemMatchOption> kept = new ArrayList<>();
		for (LineItemMatchOption option : ranked) {
			int cost = renderOption(option).length();
			if (cost > remaining) {
				continue;
			}
			kept.add(option);
			remaining -= cost;
		}
		if (kept.size() < options.size()) {
			log.warn("[claude:LineItemMatch] {} of {} candidate line items dropped to fit the {}-token prompt budget",
					options.size() - kept.size(), options.size(), PROMPT_MAX_TOKENS);
		}
		return kept;
	}

	/**
	 * Scores one candidate by how many distinct words its naming shares with the same-channel tactics.
	 *
	 * @param option       the candidate line item
	 * @param channelWords channel &rarr; the words of every ambiguous tactic in it
	 * @return the number of shared words; {@code 0} when the channel has no ambiguous tactic
	 */
	int relevance(LineItemMatchOption option, Map<String, Set<String>> channelWords) {
		Set<String> wanted = channelWords.get(option.channel());
		if (wanted == null) {
			return 0;
		}
		int score = 0;
		for (String word : words(clip(option.naming()))) {
			if (wanted.contains(word)) {
				score++;
			}
		}
		return score;
	}

	/**
	 * Splits text into lower-cased alphanumeric words, dropping one- and two-character fragments that match
	 * everywhere and carry no signal.
	 *
	 * @param value the raw text
	 * @return the distinct words found
	 */
	Set<String> words(String value) {
		Set<String> out = new HashSet<>();
		if (value == null) {
			return out;
		}
		for (String word : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
			if (word.length() > 2) {
				out.add(word);
			}
		}
		return out;
	}

	/**
	 * Re-validates Claude's raw tactic&rarr;id map against the inputs, keeping only known tactic numbers,
	 * known ids, same-channel pairs and first-wins unique ids.
	 *
	 * @param parsed  the JSON object Claude returned
	 * @param tactics the ambiguous tactics that were sent
	 * @param options the candidate line items that were sent
	 * @return the trusted tactic number &rarr; id assignments
	 */
	Map<Integer, String> parseAssignments(
			JsonNode parsed, List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		Map<Integer, String> tacticChannel = new LinkedHashMap<>();
		for (LineItemMatchTactic t : tactics) {
			tacticChannel.put(t.tacticNum(), t.channel());
		}
		Map<String, String> optionChannel = new LinkedHashMap<>();
		for (LineItemMatchOption o : options) {
			optionChannel.put(o.id(), o.channel());
		}

		Map<Integer, String> out = new LinkedHashMap<>();
		Set<String> usedIds = new HashSet<>();
		var fields = parsed.fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			int num;
			try {
				num = Integer.parseInt(entry.getKey().trim());
			} catch (NumberFormatException ex) {
				continue;
			}
			String id = entry.getValue().asText("").trim();
			String tChannel = tacticChannel.get(num);
			String oChannel = optionChannel.get(id);
			if (tChannel == null || oChannel == null || !tChannel.equals(oChannel)) {
				continue;
			}
			if (usedIds.add(id)) {
				out.put(num, id);
			}
		}
		return out;
	}

	/**
	 * Collapses whitespace and caps a context/naming string so the prompt stays bounded.
	 *
	 * @param value the raw context or naming text
	 * @return the whitespace-collapsed, length-capped value (never null)
	 */
	String clip(String value) {
		if (value == null) {
			return "";
		}
		String v = value.replaceAll("\\s+", " ").trim();
		return v.length() > MAX_CONTEXT_CHARS ? v.substring(0, MAX_CONTEXT_CHARS) : v;
	}
}
