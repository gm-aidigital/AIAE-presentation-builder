package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
@Component
@Primary
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class RealLineItemMatchAssistant implements LineItemMatchAssistant {

	private static final int MAX_TOKENS = 400;
	private static final int TIMEOUT_SECONDS = 30;
	private static final int MAX_CONTEXT_CHARS = 300;

	private final AnthropicMessagesClient messagesClient;

	public RealLineItemMatchAssistant(AnthropicMessagesClient messagesClient) {
		this.messagesClient = messagesClient;
	}

	@Override
	public Map<Integer, String> match(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		if (tactics == null || tactics.isEmpty() || options == null || options.isEmpty()) {
			return Map.of();
		}
		String prompt = buildPrompt(tactics, options);
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
			tb.append("  #").append(t.tacticNum())
					.append(" [").append(t.channel()).append("] ")
					.append(t.name())
					.append(" — ").append(clip(t.context()))
					.append('\n');
		}
		StringBuilder ob = new StringBuilder();
		for (LineItemMatchOption o : options) {
			ob.append("  ").append(o.id())
					.append(" [").append(o.channel()).append("] ")
					.append(clip(o.naming()))
					.append('\n');
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
