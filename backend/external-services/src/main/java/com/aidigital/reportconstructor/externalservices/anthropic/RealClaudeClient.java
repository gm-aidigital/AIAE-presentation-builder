package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.TacticInsight;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Anthropic Messages API implementation — a faithful port of PHP
 * {@code claude_api.php} (batches A/B/C) plus {@code resolveGeoFromTab}, with an added Batch D
 * compression pass that asks Claude to shrink oversized placeholder text before the hard-truncation
 * safety net runs. Activated only when {@code ANTHROPIC_API_KEY} is set; otherwise
 * {@link StubClaudeClient} is the sole candidate.
 *
 * <p>Every batch returns the empty DTO on any error/timeout/parse failure,
 * exactly like the PHP functions return {@code []} — the resolvers then fall
 * back to manual/sheet values or {@code "—"}.
 */
@Slf4j
@Component
@Primary
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class RealClaudeClient implements ClaudeClient {

	private static final int STRATEGIC_POINT_LIMIT = 22;
	private static final int STRATEGIC_OVERVIEW_LIMIT = 240;
	private static final int RESULTS_OVERVIEW_LIMIT = 380;
	private static final int THOUGHT_LIMIT = 220;
	private static final int TACTIC_OVERVIEW_LIMIT = 210;
	private static final int RECOMMENDATION_TITLE_LIMIT = 30;
	private static final int RECOMMENDATION_TEXT_LIMIT = 130;
	private static final int F_OPPORTUNITY_LIMIT = 180;
	private static final int F_FACT_LIMIT = 140;
	private static final int F_STORYTELLING_LIMIT = 320;

	/** First run of digits in a Batch C map key, used to recover the slot number from a drifted key. */
	private static final Pattern KEY_NUMBER = Pattern.compile("\\d+");

	/**
	 * Per-tactic bullet batches ask for a {@code "tactic_<n>"} key and Claude does not always spell it back
	 * that way ({@code "tactic 1"}, {@code "Tactic_1"}, plain {@code "1"} all show up) — the same drift
	 * Batch C already defends against with {@link #KEY_NUMBER}. Looking the reply up by exact key means one
	 * spelling change silently blanks every bullet on the slide, which is indistinguishable from the user
	 * having filled nothing in, so the tactic number is recovered from the key's digits instead.
	 */
	private static final Pattern TACTIC_KEY = Pattern.compile("^\\D*(\\d+)\\D*$");

	/** Character budget of one {@code {{publishers_observation_N_x}}} bullet on the slide. */
	private static final int PUBLISHER_OBSERVATION_LIMIT = 155;

	/** Bullets per tactic on the "Top Publishers" slide. */
	private static final int PUBLISHER_OBSERVATION_COUNT = 4;

	/**
	 * Tactics per publisher-observations call. Small chunks keep each reply far inside the output budget:
	 * a single call covering all 28 tactics would repeat Batch C's failure, where an over-long reply was
	 * truncated, failed to parse, and lost every field at once. A chunk that fails costs only its own tactics
	 * — and even that is retried one tactic at a time, see {@link #publisherObservationsResilient}.
	 */
	private static final int PUBLISHER_OBSERVATION_CHUNK = 5;

	/**
	 * Output budget per publisher-observations chunk: 4 bullets × 5 tactics plus JSON overhead, with room
	 * for the model writing past the character limit it was asked for (it cannot count characters, so it
	 * routinely does). Cheap to over-provision — unused output tokens are not billed — and running out is
	 * what blanks a whole slide.
	 */
	private static final int PUBLISHER_OBSERVATION_MAX_TOKENS = 4000;

	/** Character budget of the first three {@code {{cr_takeaway_tactic N_x}}} bullets on the slide. */
	private static final int CREATIVE_TAKEAWAY_LIMIT = 100;

	/**
	 * Character budget of the fourth {@code {{cr_takeaway_tactic N_4}}} bullet. Wider than the other three
	 * because it carries the mid-flight optimisation <em>and</em> its result, which does not fit in 100.
	 */
	private static final int CREATIVE_RECO_LIMIT = 140;

	/** Bullets per tactic on the "Creative analysis" slide; the last one is the recommendation. */
	private static final int CREATIVE_TAKEAWAY_COUNT = 4;

	/** Tactics per creative-takeaways call, chunked for the same reason publisher observations are. */
	private static final int CREATIVE_TAKEAWAY_CHUNK = 5;

	/**
	 * Output budget per creative-takeaways chunk, over-provisioned for the same reason
	 * {@link #PUBLISHER_OBSERVATION_MAX_TOKENS} is.
	 */
	private static final int CREATIVE_TAKEAWAY_MAX_TOKENS = 4000;

	// Batch C emits one results-overview per tactic group plus one tactic-overview PER TACTIC (up to 28), on
	// top of thoughts, four recommendations and the frequency copy — all in a single JSON reply. A fixed cap
	// truncated the reply once a campaign carried ~20+ tactics, so the JSON failed to parse and the whole
	// batch fell back to empty (every {{Our results overview N}} and {{tactic n overview}} rendered blank).
	// Scale the output budget with the tactic count instead: fixed base for the shared copy + per-tactic room.
	private static final int BATCH_C_BASE_TOKENS = 2500;
	private static final int BATCH_C_TOKENS_PER_TACTIC = 170;
	private static final int BATCH_C_MAX_TOKENS_CAP = 8000;

	private final AnthropicMessagesClient messagesClient;
	private final ClaudeBatchPromptBuilder promptBuilder;
	private final ClaudeResponseNormalizer normalizer;
	private final ClaudeCompressionService compressionService;
	private final ReportClaudeDefaults claudeDefaults;

	public RealClaudeClient(
			AnthropicMessagesClient messagesClient,
			ClaudeBatchPromptBuilder promptBuilder,
			ClaudeResponseNormalizer normalizer,
			ClaudeCompressionService compressionService,
			ReportClaudeDefaults claudeDefaults) {
		this.messagesClient = messagesClient;
		this.promptBuilder = promptBuilder;
		this.normalizer = normalizer;
		this.compressionService = compressionService;
		this.claudeDefaults = claudeDefaults;
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public ClaudeStrategic batchStrategic(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchAPrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyStrategic();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 2000, 60, "BatchA", false);
		if (parsed == null) {
			return claudeDefaults.emptyStrategic();
		}

		String age = normalizer.textOrNull(parsed.get("audience_age"));
		if (age != null) {
			age = age.replaceAll("\\s+", " ").trim();
			if ("not specified".equals(age.toLowerCase(Locale.ROOT))) {
				age = null;
			}
		}

		String seg = normalizer.limitAudienceSegments(normalizer.textOrNull(parsed.get("audience_segments")));
		String overview = normalizer.normalizeProposal(normalizer.textOrNull(parsed.get("proposal_overview")), 400);

		JsonNode arr = parsed.get("strategic_insights");
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			JsonNode item = (arr != null && arr.isArray() && i < arr.size()) ? arr.get(i) : null;
			String rawPoint = item == null ? "" : item.path("point").asText("").trim();
			String rawOverview = item == null ? "" : item.path("overview").asText("").trim();
			compressionFields.add(new ClaudeCompressionField("point_" + i, rawPoint, STRATEGIC_POINT_LIMIT));
			compressionFields.add(new ClaudeCompressionField("overview_" + i, rawOverview, STRATEGIC_OVERVIEW_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Strategic");

		List<StrategicInsight> insights = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String point = normalizer.limitStrategicPoint(compressed.get("point_" + i));
			String ov = normalizer.limitStrategicOverview(compressed.get("overview_" + i));
			insights.add(new StrategicInsight(point, ov));
		}

		return new ClaudeStrategic(age, seg, overview, insights);
	}

	@Override
	public ClaudeStrategic batchStrategicNarrative(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchStrategicNarrativePrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyStrategic();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 2000, 60, "BatchAStrategic", false);
		if (parsed == null) {
			return claudeDefaults.emptyStrategic();
		}

		String overview = normalizer.normalizeProposal(normalizer.textOrNull(parsed.get("proposal_overview")), 400);

		JsonNode arr = parsed.get("strategic_insights");
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			JsonNode item = (arr != null && arr.isArray() && i < arr.size()) ? arr.get(i) : null;
			String rawPoint = item == null ? "" : item.path("point").asText("").trim();
			String rawOverview = item == null ? "" : item.path("overview").asText("").trim();
			compressionFields.add(new ClaudeCompressionField("point_" + i, rawPoint, STRATEGIC_POINT_LIMIT));
			compressionFields.add(new ClaudeCompressionField("overview_" + i, rawOverview, STRATEGIC_OVERVIEW_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Strategic");

		List<StrategicInsight> insights = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String point = normalizer.limitStrategicPoint(compressed.get("point_" + i));
			String ov = normalizer.limitStrategicOverview(compressed.get("overview_" + i));
			insights.add(new StrategicInsight(point, ov));
		}

		// Audience fields are intentionally null: the sheet flow already carries them from step 1.
		return new ClaudeStrategic(null, null, overview, insights);
	}

	@Override
	public ClaudeTactical batchTactical(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchBPrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyTactical();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 500, 30, "BatchB", false);
		if (parsed == null) {
			return claudeDefaults.emptyTactical();
		}

		Map<Integer, TacticInsight> result = new LinkedHashMap<>();
		var fields = parsed.fields();
		while (fields.hasNext()) {
			var ent = fields.next();
			int n;
			try {
				n = Integer.parseInt(ent.getKey().trim());
			} catch (NumberFormatException ex) {
				continue;
			}
			JsonNode vals = ent.getValue();
			if (!data.tactics().containsKey(n) || vals == null || !vals.isObject()) {
				continue;
			}
			int male = Math.clamp(vals.path("male").asInt(50), 0, 100);
			int female = 100 - male;
			String weekdays = normalizer.textOrNull(vals.get("weekdays_peak"));
			String weekends = normalizer.textOrNull(vals.get("weekends_peak"));
			if (weekdays != null) {
				weekdays = weekdays.trim();
			}
			if (weekends != null) {
				weekends = weekends.trim();
			}
			result.put(n, new TacticInsight(male, female, weekdays, weekends));
		}
		return new ClaudeTactical(result);
	}

	@Override
	public ClaudeSheetBatch batchSheet(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchSheetPrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptySheetBatch();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 2000, 60, "BatchSheet", false);
		if (parsed == null) {
			return claudeDefaults.emptySheetBatch();
		}

		String age = normalizer.textOrNull(parsed.get("audience_age"));
		if (age != null) {
			age = age.replaceAll("\\s+", " ").trim();
			if ("not specified".equals(age.toLowerCase(Locale.ROOT))) {
				age = null;
			}
		}

		String seg = normalizer.limitAudienceSegments(normalizer.textOrNull(parsed.get("audience_segments")));

		Map<Integer, TacticInsight> result = new LinkedHashMap<>();
		JsonNode tactics = parsed.get("tactics");
		if (tactics != null && tactics.isObject()) {
			var fields = tactics.fields();
			while (fields.hasNext()) {
				var ent = fields.next();
				int n;
				try {
					n = Integer.parseInt(ent.getKey().trim());
				} catch (NumberFormatException ex) {
					continue;
				}
				JsonNode vals = ent.getValue();
				if (!data.tactics().containsKey(n) || vals == null || !vals.isObject()) {
					continue;
				}
				int male = Math.clamp(vals.path("male").asInt(50), 0, 100);
				int female = 100 - male;
				String weekdays = normalizer.textOrNull(vals.get("weekdays_peak"));
				String weekends = normalizer.textOrNull(vals.get("weekends_peak"));
				if (weekdays != null) {
					weekdays = weekdays.trim();
				}
				if (weekends != null) {
					weekends = weekends.trim();
				}
				result.put(n, new TacticInsight(male, female, weekdays, weekends));
			}
		}
		return new ClaudeSheetBatch(age, seg, result);
	}

	@Override
	public ClaudeResults batchResults(CampaignData data, String brief, CampaignFrequencies frequencies) {
		var prompt = promptBuilder.buildBatchCPrompt(data, brief, frequencies);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyResults();
		}
		int tacticCount = data.tactics() == null ? 0 : data.tactics().size();
		int maxTokens = Math.min(BATCH_C_MAX_TOKENS_CAP,
				BATCH_C_BASE_TOKENS + BATCH_C_TOKENS_PER_TACTIC * tacticCount);
		// A larger reply also streams longer, so give big decks more HTTP head-room than the 60s that was
		// enough for the old fixed cap.
		int timeoutSec = tacticCount > 10 ? 120 : 60;
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), maxTokens, timeoutSec, "BatchC", true);
		if (parsed == null) {
			return claudeDefaults.emptyResults();
		}

		Map<Integer, String> rawResultsOverviews = parseNumberedTextMap(parsed.get("results_overviews"));
		List<String> rawThoughts =
				normalizer.normalizeThoughts(normalizer.textOrNull(parsed.get("thoughts_on_performance")));

		Map<Integer, String> rawTacticOverviews = parseNumberedTextMap(parsed.get("tactic_overviews"));

		JsonNode recArr = parsed.get("optimization_recommendations");
		String[] rawRecTitles = new String[4];
		String[] rawRecTexts = new String[4];
		for (int i = 0; i < 4; i++) {
			JsonNode item = (recArr != null && recArr.isArray() && i < recArr.size()) ? recArr.get(i) : null;
			rawRecTitles[i] = item == null ? "" : item.path("title").asText("").trim();
			rawRecTexts[i] = item == null ? "" : item.path("text").asText("").trim();
		}

		String rawFOpportunity = normalizer.textOrNull(parsed.get("f_opportunity"));
		String rawFFact = normalizer.textOrNull(parsed.get("f_fact"));
		String rawFStorytelling = normalizer.textOrNull(parsed.get("f_storytelling"));

		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (Map.Entry<Integer, String> e : rawResultsOverviews.entrySet()) {
			compressionFields.add(new ClaudeCompressionField(
					"results_overview_" + e.getKey(), e.getValue(), RESULTS_OVERVIEW_LIMIT));
		}
		if (rawFOpportunity != null) {
			compressionFields.add(new ClaudeCompressionField("f_opportunity", rawFOpportunity, F_OPPORTUNITY_LIMIT));
		}
		if (rawFFact != null) {
			compressionFields.add(new ClaudeCompressionField("f_fact", rawFFact, F_FACT_LIMIT));
		}
		if (rawFStorytelling != null) {
			compressionFields.add(new ClaudeCompressionField("f_storytelling", rawFStorytelling, F_STORYTELLING_LIMIT));
		}
		for (int i = 0; i < rawThoughts.size(); i++) {
			String thought = rawThoughts.get(i);
			if (thought != null) {
				compressionFields.add(new ClaudeCompressionField("thought_" + i, thought, THOUGHT_LIMIT));
			}
		}
		for (Map.Entry<Integer, String> e : rawTacticOverviews.entrySet()) {
			compressionFields.add(
					new ClaudeCompressionField("tactic_overview_" + e.getKey(), e.getValue(), TACTIC_OVERVIEW_LIMIT));
		}
		for (int i = 0; i < 4; i++) {
			compressionFields.add(
					new ClaudeCompressionField("rec_title_" + i, rawRecTitles[i], RECOMMENDATION_TITLE_LIMIT));
			compressionFields.add(
					new ClaudeCompressionField("rec_text_" + i, rawRecTexts[i], RECOMMENDATION_TEXT_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Results");

		Map<Integer, String> resultsOverviews = new LinkedHashMap<>();
		for (Integer group : rawResultsOverviews.keySet()) {
			resultsOverviews.put(group,
					normalizer.limitResultsOverview(compressed.get("results_overview_" + group)));
		}

		List<String> thoughts = new ArrayList<>();
		for (int i = 0; i < rawThoughts.size(); i++) {
			String thought = rawThoughts.get(i);
			thoughts.add(thought == null ? null : normalizer.normalizeC(compressed.get("thought_" + i), THOUGHT_LIMIT));
		}

		Map<Integer, String> tacticOverviews = new LinkedHashMap<>();
		for (Integer tacticNumber : rawTacticOverviews.keySet()) {
			tacticOverviews.put(tacticNumber,
					normalizer.limitTacticOverview(compressed.get("tactic_overview_" + tacticNumber)));
		}

		List<Recommendation> recommendations = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String title = normalizer.limitRecommendationTitle(compressed.get("rec_title_" + i));
			String text = normalizer.limitRecommendationText(compressed.get("rec_text_" + i));
			recommendations.add(new Recommendation(title, text));
		}

		String fOpportunity = rawFOpportunity == null
				? null
				: normalizer.limitFOpportunity(compressed.get("f_opportunity"));
		String fFact = rawFFact == null ? null : normalizer.limitFFact(compressed.get("f_fact"));
		String fStorytelling = rawFStorytelling == null
				? null
				: normalizer.limitFStorytelling(compressed.get("f_storytelling"));

		return new ClaudeResults(resultsOverviews, thoughts, tacticOverviews, recommendations,
				fOpportunity, fFact, fStorytelling);
	}

	/**
	 * Parses a Batch C {@code {"1": text, "2": text, …}} object into a number-keyed map, tolerating the
	 * key-format drift the model occasionally produces. A key is mapped to the first integer it contains
	 * ({@code "1"}, {@code "group 1"}, {@code "G1"} all → {@code 1}); a key with no digits (e.g. the schema's
	 * literal {@code "G"}/{@code "N"} template placeholder echoed verbatim) falls back to its 1-based
	 * encounter position, so a single mis-keyed entry still lands on slot 1 rather than being silently
	 * dropped — which is what left every {@code {{Our results overview N}}} blank. First writer wins per slot.
	 *
	 * @param node the raw JSON value for the {@code results_overviews}/{@code tactic_overviews} field (may be null)
	 * @return a map from 1-based slot number to its text, in encounter order (empty when {@code node} is absent
	 * or not an object)
	 */
	Map<Integer, String> parseNumberedTextMap(JsonNode node) {
		Map<Integer, String> out = new LinkedHashMap<>();
		if (node == null || !node.isObject()) {
			return out;
		}
		var it = node.fields();
		int position = 0;
		while (it.hasNext()) {
			var ent = it.next();
			position++;
			Matcher m = KEY_NUMBER.matcher(ent.getKey());
			int slot = m.find() ? Integer.parseInt(m.group()) : position;
			if (slot > 0) {
				out.putIfAbsent(slot, ent.getValue().asText(""));
			}
		}
		return out;
	}

	@Override
	public Map<Integer, List<String>> batchPublisherObservations(List<PublisherObservationInput> inputs, String brief) {
		Map<Integer, List<String>> observations = new LinkedHashMap<>();
		if (inputs == null || inputs.isEmpty()) {
			return observations;
		}
		for (int start = 0; start < inputs.size(); start += PUBLISHER_OBSERVATION_CHUNK) {
			List<PublisherObservationInput> chunk =
					inputs.subList(start, Math.min(start + PUBLISHER_OBSERVATION_CHUNK, inputs.size()));
			observations.putAll(publisherObservationsResilient(chunk, brief));
		}
		return observations;
	}

	/**
	 * Runs one publisher-observations chunk and, when it comes back with nothing at all, tries again rather
	 * than shipping the whole chunk's slides with blank bullets.
	 *
	 * <p>A chunk of several tactics is retried one tactic at a time: whatever cost the first reply its
	 * bullets — a rate limit, an unparseable reply, an over-long answer — the single-tactic calls are
	 * smaller, independent, and a tactic that fails again can no longer take its neighbours down with it. A
	 * chunk already down to one tactic simply gets one more attempt, since these failures are usually
	 * transient. A chunk that came back partially filled is left alone: {@link #bulletsByTactic} has
	 * already logged which tactics the reply skipped.
	 *
	 * @param chunk the tactics to cover
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return tactic number → its four bullets; empty only when every attempt failed
	 */
	Map<Integer, List<String>> publisherObservationsResilient(List<PublisherObservationInput> chunk, String brief) {
		Map<Integer, List<String>> observations = publisherObservationsChunk(chunk, brief);
		if (!observations.isEmpty() || chunk.isEmpty()) {
			return observations;
		}
		if (chunk.size() == 1) {
			log.warn("[claude:BatchPublishers] tactic {} came back with no bullets — retrying once",
					chunk.getFirst().tacticNum());
			return publisherObservationsChunk(chunk, brief);
		}
		log.warn("[claude:BatchPublishers] chunk {} came back empty — retrying one tactic per call",
				chunk.stream().map(PublisherObservationInput::tacticNum).toList());
		Map<Integer, List<String>> perTactic = new LinkedHashMap<>();
		for (PublisherObservationInput input : chunk) {
			perTactic.putAll(publisherObservationsResilient(List.of(input), brief));
		}
		return perTactic;
	}

	/**
	 * Runs one publisher-observations chunk: prompt, parse, compress the over-budget bullets, then apply
	 * the truncation safety net. Returns an empty map — never partial or invented copy — when the call or
	 * the parse fails, so only this chunk's tactics lose their bullets.
	 *
	 * @param chunk the tactics to cover in this single call
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return tactic number → its four bullets; empty when the chunk produced no usable reply
	 */
	Map<Integer, List<String>> publisherObservationsChunk(List<PublisherObservationInput> chunk, String brief) {
		Map<Integer, List<String>> observations = new LinkedHashMap<>();
		var prompt = promptBuilder.buildPublisherObservationsPrompt(chunk, brief, PUBLISHER_OBSERVATION_LIMIT);
		if (prompt.isEmpty()) {
			return observations;
		}
		// allowPartial: a reply that ran past the budget still carries whole bullets for the tactics it did
		// answer, and salvaging those beats blanking the chunk over its unfinished tail.
		JsonNode parsed = messagesClient.callJsonObject(
				prompt.get(), PUBLISHER_OBSERVATION_MAX_TOKENS, 60, "BatchPublishers", true);
		if (parsed == null) {
			return observations;
		}

		// Collect every bullet across the chunk's tactics first, so the whole chunk's over-budget text is
		// compressed in one Batch D call rather than one per tactic.
		Map<Integer, JsonNode> byTactic = bulletsByTactic(parsed, "BatchPublishers");
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (PublisherObservationInput input : chunk) {
			JsonNode arr = byTactic.get(input.tacticNum());
			if (arr == null) {
				log.warn("[claude:BatchPublishers] reply carries no bullets for tactic {} (keys: {})",
						input.tacticNum(), byTactic.keySet());
				continue;
			}
			for (int i = 0; i < PUBLISHER_OBSERVATION_COUNT; i++) {
				String raw = i < arr.size() ? arr.get(i).asText("").trim() : "";
				compressionFields.add(new ClaudeCompressionField(
						input.tacticNum() + "_" + i, raw, PUBLISHER_OBSERVATION_LIMIT));
			}
		}
		if (compressionFields.isEmpty()) {
			return observations;
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Publishers");

		for (PublisherObservationInput input : chunk) {
			if (byTactic.get(input.tacticNum()) == null) {
				continue;
			}
			List<String> bullets = new ArrayList<>(PUBLISHER_OBSERVATION_COUNT);
			for (int i = 0; i < PUBLISHER_OBSERVATION_COUNT; i++) {
				bullets.add(normalizer.normalizeC(
						compressed.get(input.tacticNum() + "_" + i), PUBLISHER_OBSERVATION_LIMIT));
			}
			observations.put(input.tacticNum(), bullets);
		}
		return observations;
	}

	@Override
	public Map<Integer, List<String>> batchCreativeTakeaways(List<CreativeTakeawayInput> inputs, String brief) {
		Map<Integer, List<String>> takeaways = new LinkedHashMap<>();
		if (inputs == null || inputs.isEmpty()) {
			return takeaways;
		}
		for (int start = 0; start < inputs.size(); start += CREATIVE_TAKEAWAY_CHUNK) {
			List<CreativeTakeawayInput> chunk =
					inputs.subList(start, Math.min(start + CREATIVE_TAKEAWAY_CHUNK, inputs.size()));
			takeaways.putAll(creativeTakeawaysResilient(chunk, brief));
		}
		return takeaways;
	}

	/**
	 * Runs one creative-takeaways chunk and retries an empty result, mirroring
	 * {@link #publisherObservationsResilient} — see it for why a multi-tactic chunk is retried one tactic
	 * at a time.
	 *
	 * @param chunk the tactics to cover
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return tactic number → its four bullets; empty only when every attempt failed
	 */
	Map<Integer, List<String>> creativeTakeawaysResilient(List<CreativeTakeawayInput> chunk, String brief) {
		Map<Integer, List<String>> takeaways = creativeTakeawaysChunk(chunk, brief);
		if (!takeaways.isEmpty() || chunk.isEmpty()) {
			return takeaways;
		}
		if (chunk.size() == 1) {
			log.warn("[claude:BatchCreatives] tactic {} came back with no bullets — retrying once",
					chunk.getFirst().tacticNum());
			return creativeTakeawaysChunk(chunk, brief);
		}
		log.warn("[claude:BatchCreatives] chunk {} came back empty — retrying one tactic per call",
				chunk.stream().map(CreativeTakeawayInput::tacticNum).toList());
		Map<Integer, List<String>> perTactic = new LinkedHashMap<>();
		for (CreativeTakeawayInput input : chunk) {
			perTactic.putAll(creativeTakeawaysResilient(List.of(input), brief));
		}
		return perTactic;
	}

	/**
	 * Runs one creative-takeaways chunk: prompt, parse, compress the over-budget bullets, then apply the
	 * truncation safety net. Returns an empty map — never partial or invented copy — when the call or the
	 * parse fails, so only this chunk's tactics lose their bullets.
	 *
	 * @param chunk the tactics to cover in this single call
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return tactic number → its four bullets; empty when the chunk produced no usable reply
	 */
	Map<Integer, List<String>> creativeTakeawaysChunk(List<CreativeTakeawayInput> chunk, String brief) {
		Map<Integer, List<String>> takeaways = new LinkedHashMap<>();
		var prompt = promptBuilder.buildCreativeTakeawaysPrompt(
				chunk, brief, CREATIVE_TAKEAWAY_LIMIT, CREATIVE_RECO_LIMIT);
		if (prompt.isEmpty()) {
			return takeaways;
		}
		// allowPartial for the same reason publisher observations use it.
		JsonNode parsed = messagesClient.callJsonObject(
				prompt.get(), CREATIVE_TAKEAWAY_MAX_TOKENS, 60, "BatchCreatives", true);
		if (parsed == null) {
			return takeaways;
		}

		// Collect every bullet across the chunk's tactics first, so the whole chunk's over-budget text is
		// compressed in one Batch D call rather than one per tactic.
		Map<Integer, JsonNode> byTactic = bulletsByTactic(parsed, "BatchCreatives");
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (CreativeTakeawayInput input : chunk) {
			JsonNode arr = byTactic.get(input.tacticNum());
			if (arr == null) {
				log.warn("[claude:BatchCreatives] reply carries no bullets for tactic {} (keys: {})",
						input.tacticNum(), byTactic.keySet());
				continue;
			}
			for (int i = 0; i < CREATIVE_TAKEAWAY_COUNT; i++) {
				String raw = i < arr.size() ? arr.get(i).asText("").trim() : "";
				compressionFields.add(new ClaudeCompressionField(
						input.tacticNum() + "_" + i, raw, creativeTakeawayLimit(i)));
			}
		}
		if (compressionFields.isEmpty()) {
			return takeaways;
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Creatives");

		for (CreativeTakeawayInput input : chunk) {
			if (byTactic.get(input.tacticNum()) == null) {
				continue;
			}
			List<String> bullets = new ArrayList<>(CREATIVE_TAKEAWAY_COUNT);
			for (int i = 0; i < CREATIVE_TAKEAWAY_COUNT; i++) {
				bullets.add(normalizer.normalizeC(
						compressed.get(input.tacticNum() + "_" + i), creativeTakeawayLimit(i)));
			}
			takeaways.put(input.tacticNum(), bullets);
		}
		return takeaways;
	}

	/**
	 * Returns the character budget of one creative takeaway by its zero-based slide position: the last
	 * bullet is the mid-flight optimisation note and gets the wider {@link #CREATIVE_RECO_LIMIT}.
	 *
	 * @param index zero-based bullet index within the tactic's four takeaways
	 * @return the bullet's character budget
	 */
	int creativeTakeawayLimit(int index) {
		return index == CREATIVE_TAKEAWAY_COUNT - 1 ? CREATIVE_RECO_LIMIT : CREATIVE_TAKEAWAY_LIMIT;
	}

	/**
	 * Indexes a per-tactic bullet reply by tactic number, recovering the number from each key's digits
	 * rather than demanding the exact {@code "tactic_<n>"} spelling that was asked for. Keys carrying no
	 * digits, or no bullet array, are dropped; the first key claiming a tactic wins, so a duplicate cannot
	 * silently replace a good answer.
	 *
	 * @param parsed the reply object, keyed by whatever Claude called each tactic
	 * @param label  short tag identifying the batch in log messages
	 * @return tactic number → its bullet array (empty when the reply carried no usable key)
	 */
	Map<Integer, JsonNode> bulletsByTactic(JsonNode parsed, String label) {
		Map<Integer, JsonNode> byTactic = new LinkedHashMap<>();
		var fields = parsed.fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			Matcher matcher = TACTIC_KEY.matcher(entry.getKey().trim());
			if (!matcher.matches() || !entry.getValue().isArray()) {
				log.warn("[claude:{}] reply key '{}' carries no tactic number or no bullet array — ignoring",
						label, entry.getKey());
				continue;
			}
			byTactic.putIfAbsent(Integer.parseInt(matcher.group(1)), entry.getValue());
		}
		return byTactic;
	}

	@Override
	public String summarizeGeo(List<List<String>> geoRows) {
		if (geoRows == null || geoRows.isEmpty()) {
			return null;
		}
		String prompt = promptBuilder.buildGeoPrompt(geoRows);
		JsonNode resp = messagesClient.callRaw(prompt, 60, 30, "Geo");
		if (resp == null) {
			return null;
		}
		return normalizer.limitGeoSummary(normalizer.extractText(resp));
	}

	@Override
	public String summarizeFunnelStages(List<List<String>> geoRows) {
		if (geoRows == null || geoRows.isEmpty()) {
			return null;
		}
		String prompt = promptBuilder.buildFunnelPrompt(geoRows);
		JsonNode resp = messagesClient.callRaw(prompt, 60, 30, "Funnel");
		if (resp == null) {
			return null;
		}
		String text = normalizer.extractText(resp);
		return text == null || text.isBlank() ? null : text.trim();
	}

	@Override
	public String summarizePrimaryKpis(CampaignData data) {
		var prompt = promptBuilder.buildPrimaryKpisPrompt(data);
		if (prompt.isEmpty()) {
			return null;
		}
		JsonNode resp = messagesClient.callRaw(prompt.get(), 60, 30, "PrimaryKpis");
		if (resp == null) {
			return null;
		}
		return normalizer.limitPrimaryKpis(normalizer.extractText(resp));
	}
}
