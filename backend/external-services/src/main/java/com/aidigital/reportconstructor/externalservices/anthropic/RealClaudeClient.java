package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusionInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticInsight;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

	private static final int PROPOSAL_LIMIT = 400;
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

	/** Character budget of the first three {@code {{cr_takeaway_tactic N_x}}} bullets on the slide. */
	private static final int CREATIVE_TAKEAWAY_LIMIT = 100;

	/**
	 * Character budget of the fourth {@code {{cr_takeaway_tactic N_4}}} bullet. Wider than the other three
	 * because it carries the mid-flight optimisation <em>and</em> its result, which does not fit in 100.
	 */
	private static final int CREATIVE_RECO_LIMIT = 140;

	/** Bullets per tactic on the "Creative analysis" slide; the last one is the recommendation. */
	private static final int CREATIVE_TAKEAWAY_COUNT = 4;

	/**
	 * Character budget of each geo string on the slide — the four {@code {{geo_insight_N.x}}} bullets and
	 * the {@code {{geo_N_reco}}} recommendation all share the same 140-character budget.
	 */
	private static final int GEO_INSIGHT_LIMIT = 140;

	/**
	 * Strings per tactic on the "Geo analysis" slide: four "what the map tells us" insight bullets plus one
	 * forward-looking recommendation, in that order.
	 */
	private static final int GEO_BULLET_COUNT = 5;

	/**
	 * Character budget of the "Audience analysis" slide's key takeaway ({@code {{aud_N_takeaway}}}), the
	 * widest of the four audience fields.
	 */
	private static final int AUDIENCE_TAKEAWAY_LIMIT = 256;

	/**
	 * Character budget of the "Audience analysis" slide's three shorter fields — "what worked"
	 * ({@code {{aud_N_worked}}}), the watch-out ({@code {{aud_N_flag}}}) and the recommended action
	 * ({@code {{aud_N_reco}}}).
	 */
	private static final int AUDIENCE_SHORT_LIMIT = 120;

	/**
	 * Strings per tactic on the "Audience analysis" slide: the key takeaway, "what worked", the watch-out
	 * and the recommended action, in that order.
	 */
	private static final int AUDIENCE_FIELD_COUNT = 4;

	/**
	 * Output budget for one per-section call. The valid output is small — geo's five ~140-char strings or
	 * audience/device's ~256+3×120, a few hundred tokens as a bare JSON array — but the budget is set well above
	 * that so a reply the model opens with a little extra text (which the prompt forbids but a model may still
	 * add) still closes its last string instead of truncating mid-array and being rejected by {@code max_tokens}.
	 */
	private static final int SECTION_MAX_TOKENS = 1500;

	/**
	 * Character budget of the "Device breakdown" slide's key takeaway ({@code {{dev_N_takeaway}}}), the
	 * widest of the four device fields.
	 */
	private static final int DEVICE_TAKEAWAY_LIMIT = 256;

	/**
	 * Character budget of the "Device breakdown" slide's three shorter fields — "what worked"
	 * ({@code {{dev_N_worked}}}), the watch-out ({@code {{dev_N_flag}}}) and the recommended action
	 * ({@code {{dev_N_reco}}}).
	 */
	private static final int DEVICE_SHORT_LIMIT = 120;

	/**
	 * Strings per tactic on the "Device breakdown" slide: the key takeaway, "what worked", the watch-out
	 * and the recommended action, in that order.
	 */
	private static final int DEVICE_FIELD_COUNT = 4;

	// Batch C emits one results-overview per tactic group plus one tactic-overview PER TACTIC (up to 28), on
	// top of thoughts, four recommendations and the frequency copy — all in a single JSON reply. A fixed cap
	// truncated the reply once a campaign carried ~20+ tactics, so the JSON failed to parse and the whole
	// batch fell back to empty (every {{Our results overview N}} and {{tactic n overview}} rendered blank).
	// Scale the output budget with the tactic count instead: fixed base for the shared copy + per-tactic room.
	private static final int BATCH_C_BASE_TOKENS = 2500;
	private static final int BATCH_C_TOKENS_PER_TACTIC = 170;
	private static final int BATCH_C_MAX_TOKENS_CAP = 8000;

	/**
	 * Output budget for the Step-2 combined conclusions call. Each tactic can carry an overview plus up to five
	 * sections (~21 strings), so the per-tactic allowance is generous; the base covers JSON overhead and the cap
	 * bounds a large chunk. A reply that still overruns salvages the tactics it finished (allowPartial).
	 */
	private static final int CONCLUSIONS_BASE_TOKENS = 1500;
	private static final int CONCLUSIONS_TOKENS_PER_TACTIC = 1200;
	private static final int CONCLUSIONS_MAX_TOKENS_CAP = 8000;

	/** The per-tactic "thoughts on tactic performance" slide holds exactly four thought strings. */
	private static final int TACTIC_THOUGHTS_COUNT = 4;
	/** Output budget for one tactic's thoughts call: four ~220-char thoughts plus JSON overhead. */
	private static final int TACTIC_THOUGHTS_MAX_TOKENS = 900;

	/**
	 * Output budget for the Step-4 campaign-results call. Its output is largely fixed (grouped overviews, four
	 * thoughts, four recommendations, frequency) and scales with tactic GROUPS, not tactics, so the per-tactic
	 * term is small; the base covers the fixed copy and the cap bounds a many-group deck.
	 */
	private static final int CAMPAIGN_RESULTS_BASE_TOKENS = 2500;
	private static final int CAMPAIGN_RESULTS_TOKENS_PER_TACTIC = 80;
	private static final int CAMPAIGN_RESULTS_MAX_TOKENS_CAP = 6000;

	// Batch D (narrative alignment) only ever rewrites the bounded campaign-level copy — the proposal, four
	// strategic insights, up to four group results overviews, up to four thoughts and three frequency strings —
	// never the per-tactic overviews or breakdown bullets. Its output therefore does not scale with tactic
	// count, so a fixed budget is safe and cannot hit Batch C's truncation-at-scale failure mode.
	private static final int ALIGN_MAX_TOKENS = 3000;
	private static final int ALIGN_TIMEOUT_SEC = 90;

	/**
	 * Per-request HTTP timeout for every per-tactic breakdown chunk (publishers, creatives, geo, audience,
	 * device). Raised from the earlier 60s because a chunk that streamed slowly occasionally timed out,
	 * came back empty, and left a slide's bullets blank; 90s gives the same head-room the {@link
	 * #ALIGN_TIMEOUT_SEC align pass} already uses. A timeout still costs only its own chunk — the resilient
	 * retry re-runs it one tactic at a time.
	 */
	private static final int BREAKDOWN_TIMEOUT_SEC = 90;

	/**
	 * Token budget for the geo prompt. The workbook is filtered down to its geography-bearing rows before
	 * it is sent, and a plan that still does not fit in this budget is not worth a request: the answer is a
	 * ≤40-character string, so anything larger means the filter matched half the plan and the reply would be
	 * a guess. The summary is skipped in that case and {@code {{geo_locations}}} falls back to a dash, which
	 * the user can fill in on the review sheet.
	 */
	private static final int GEO_PROMPT_MAX_TOKENS = 2000;

	/** Character budget of the brief digest every later batch reads in place of the raw brief. */
	private static final int BRIEF_DIGEST_LIMIT = 2000;

	/**
	 * Output budget for the brief digest: {@link #BRIEF_DIGEST_LIMIT} characters of dense prose, with the
	 * usual head-room for the model writing past the character limit it was asked for.
	 */
	private static final int BRIEF_DIGEST_MAX_TOKENS = 1200;

	/** Per-request HTTP timeout for the brief digest. */
	private static final int BRIEF_DIGEST_TIMEOUT_SEC = 60;

	private final AnthropicMessagesClient messagesClient;
	private final ClaudeBatchPromptBuilder promptBuilder;
	private final ClaudeResponseNormalizer normalizer;
	private final ClaudeCompressionService compressionService;
	private final ReportClaudeDefaults claudeDefaults;
	private final WorkbookGeoFilter geoFilter;
	private final PromptTokenEstimator tokenEstimator;
	/** Tactics per Step-2 combined conclusions call; bound from config, clamped to at least 1. */
	private final int breakdownChunkSize;
	/** Whether each breakdown section is produced by its own dedicated per-tactic call; bound from config. */
	private final boolean perSectionCallsEnabled;
	/** Extra attempts a per-section call makes on a contract failure; bound from config, clamped to ≥ 0. */
	private final int sectionRetries;

	public RealClaudeClient(
			AnthropicMessagesClient messagesClient,
			ClaudeBatchPromptBuilder promptBuilder,
			ClaudeResponseNormalizer normalizer,
			ClaudeCompressionService compressionService,
			ReportClaudeDefaults claudeDefaults,
			WorkbookGeoFilter geoFilter,
			PromptTokenEstimator tokenEstimator,
			AnthropicProperties anthropicProperties) {
		this.messagesClient = messagesClient;
		this.promptBuilder = promptBuilder;
		this.normalizer = normalizer;
		this.compressionService = compressionService;
		this.claudeDefaults = claudeDefaults;
		this.geoFilter = geoFilter;
		this.tokenEstimator = tokenEstimator;
		this.breakdownChunkSize = Math.max(1, anthropicProperties.getBreakdownChunkSize());
		this.perSectionCallsEnabled = anthropicProperties.isPerSectionCallsEnabled();
		this.sectionRetries = Math.max(0, anthropicProperties.getSectionRetries());
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public boolean perSectionCallsEnabled() {
		return perSectionCallsEnabled;
	}

	@Override
	public List<String> publisherSection(CampaignData data, PublisherObservationInput input, String brief) {
		if (input == null) {
			return List.of();
		}
		return runSection("PublisherSection", input.tacticNum(),
				promptBuilder.buildPublisherSectionPrompt(input, data, brief, PUBLISHER_OBSERVATION_LIMIT),
				PUBLISHER_OBSERVATION_COUNT, i -> PUBLISHER_OBSERVATION_LIMIT);
	}

	@Override
	public List<String> creativeSection(CampaignData data, CreativeTakeawayInput input, String brief) {
		if (input == null) {
			return List.of();
		}
		return runSection("CreativeSection", input.tacticNum(),
				promptBuilder.buildCreativeSectionPrompt(input, data, brief, CREATIVE_TAKEAWAY_LIMIT, CREATIVE_RECO_LIMIT),
				CREATIVE_TAKEAWAY_COUNT,
				i -> i == CREATIVE_TAKEAWAY_COUNT - 1 ? CREATIVE_RECO_LIMIT : CREATIVE_TAKEAWAY_LIMIT);
	}

	@Override
	public List<String> geoSection(CampaignData data, GeoInsightInput input, String brief) {
		if (input == null) {
			return List.of();
		}
		return runSection("GeoSection", input.tacticNum(),
				promptBuilder.buildGeoSectionPrompt(input, data, brief, GEO_INSIGHT_LIMIT),
				GEO_BULLET_COUNT, i -> GEO_INSIGHT_LIMIT);
	}

	@Override
	public List<String> audienceSection(CampaignData data, AudienceInsightInput input, String brief) {
		if (input == null) {
			return List.of();
		}
		return runSection("AudienceSection", input.tacticNum(),
				promptBuilder.buildAudienceSectionPrompt(input, data, brief, AUDIENCE_TAKEAWAY_LIMIT, AUDIENCE_SHORT_LIMIT),
				AUDIENCE_FIELD_COUNT, i -> i == 0 ? AUDIENCE_TAKEAWAY_LIMIT : AUDIENCE_SHORT_LIMIT);
	}

	@Override
	public List<String> deviceSection(CampaignData data, DeviceInsightInput input, String brief) {
		if (input == null) {
			return List.of();
		}
		return runSection("DeviceSection", input.tacticNum(),
				promptBuilder.buildDeviceSectionPrompt(input, data, brief, DEVICE_TAKEAWAY_LIMIT, DEVICE_SHORT_LIMIT),
				DEVICE_FIELD_COUNT, i -> i == 0 ? DEVICE_TAKEAWAY_LIMIT : DEVICE_SHORT_LIMIT);
	}

	/**
	 * Runs one section's dedicated per-tactic call with the shared accept/retry contract used by every
	 * per-section method. It sends the prompt, accepts the reply only when {@link #sectionOnce} returns the
	 * full set of strings (a JSON array of exactly {@code count} non-blank items), and otherwise retries up to
	 * {@link #sectionRetries} times before giving up and returning an empty list so the tactic's section ships
	 * blank (and the caller surfaces that) rather than carrying a partial or invented reply.
	 *
	 * @param label     short tag identifying the section in log messages (e.g. {@code "GeoSection"})
	 * @param tacticNum the tactic number, for log messages and compression-field keys
	 * @param prompt    the built section prompt, or empty when the tactic has no data to reason over
	 * @param count     the exact number of non-blank strings the reply must carry
	 * @param limitAt   the character budget for the field at a given index (sections vary which index is widest)
	 * @return the {@code count} normalized strings in slide order, or an empty list when no attempt satisfied the contract
	 */
	List<String> runSection(
			String label, int tacticNum, java.util.Optional<String> prompt, int count,
			java.util.function.IntUnaryOperator limitAt) {
		if (prompt.isEmpty()) {
			return List.of();
		}
		int attempts = sectionRetries + 1;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			List<String> res = sectionOnce(label, tacticNum, prompt.get(), count, limitAt);
			if (!res.isEmpty()) {
				return res;
			}
			if (attempt < attempts) {
				log.warn("[claude:{}] tactic {} reply failed the {}-string contract — retry {}/{}",
						label, tacticNum, count, attempt, sectionRetries);
			}
		}
		log.warn("[claude:{}] tactic {} produced no usable copy after {} attempt(s) — its fields ship blank",
				label, tacticNum, attempts);
		return List.of();
	}

	/**
	 * Runs one section call once and enforces the positional contract: the reply is accepted only when it parses
	 * as a JSON array of exactly {@code count} non-blank strings. Anything else — a short array, a non-array, a
	 * blank field, or a failed call — returns an empty list so {@link #runSection} retries rather than accepting
	 * a partial reply. Accepted strings are compressed and normalized to the same per-index budgets the combined
	 * path uses, so the per-section and combined paths yield identical field shapes.
	 *
	 * @param label     short tag identifying the section in log/compression messages
	 * @param tacticNum the tactic number, used to key the compression fields
	 * @param prompt    the built section prompt text
	 * @param count     the exact number of non-blank strings the reply must carry
	 * @param limitAt   the character budget for the field at a given index
	 * @return the {@code count} normalized strings, or an empty list when the reply did not satisfy the contract
	 */
	List<String> sectionOnce(
			String label, int tacticNum, String prompt, int count, java.util.function.IntUnaryOperator limitAt) {
		// allowPartial lets the transport repair a reply the model never closed — the same salvage every other
		// batch already gets. Nothing partial slips through: the exact-count check below still rejects an array
		// that lost an item to the repair, so the attempt is retried rather than shipped short.
		JsonNode arr = messagesClient.callJsonArray(
				prompt, SECTION_MAX_TOKENS, BREAKDOWN_TIMEOUT_SEC, label, true);
		if (arr == null || !arr.isArray()) {
			// The call itself failed or the reply was not an array; the transport already logged the cause, so
			// this line only ties that cause to the section and tactic whose fields are about to ship blank.
			log.warn("[claude:{}] tactic {} rejected: no JSON array in the reply", label, tacticNum);
			return List.of();
		}
		// Tolerate an accidental one-level wrapper array — the model sometimes returns [[...]] (an array whose
		// only element is the real array of strings) instead of a flat [...]. Unwrap it before the count check;
		// the strict "exactly count non-blank strings" contract still applies to the unwrapped array.
		if (arr.size() == 1 && arr.get(0).isArray()) {
			arr = arr.get(0);
		}
		if (arr.size() != count) {
			log.warn("[claude:{}] tactic {} rejected: array holds {} item(s), expected {}",
					label, tacticNum, arr.size(), count);
			return List.of();
		}
		List<String> raw = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String value = arr.get(i).asText("").trim();
			if (value.isBlank()) {
				log.warn("[claude:{}] tactic {} rejected: item {} of {} is blank (node type {})",
						label, tacticNum, i, count, arr.get(i).getNodeType());
				return List.of();
			}
			raw.add(value);
		}
		List<ClaudeCompressionField> fields = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			fields.add(new ClaudeCompressionField(tacticNum + "_" + i, raw.get(i), limitAt.applyAsInt(i)));
		}
		Map<String, String> compressed = compressionService.compress(fields, label);
		List<String> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			out.add(normalizer.normalizeC(compressed.get(tacticNum + "_" + i), limitAt.applyAsInt(i)));
		}
		return out;
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
		String overview = normalizer.normalizeProposal(
				normalizer.textOrNull(parsed.get("proposal_overview")), PROPOSAL_LIMIT);

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

		String overview = normalizer.normalizeProposal(
				normalizer.textOrNull(parsed.get("proposal_overview")), PROPOSAL_LIMIT);

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

	@Override
	public ClaudeNarrative batchAlignNarrative(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief) {
		// Never blank a field on failure: the whole pass falls back to the un-aligned originals.
		ClaudeNarrative original = new ClaudeNarrative(strategic, results);
		if (strategic == null || results == null) {
			return original;
		}
		var prompt = promptBuilder.buildBatchDPrompt(strategic, results, breakdownDigest, brief);
		if (prompt.isEmpty()) {
			return original;
		}
		JsonNode parsed =
				messagesClient.callJsonObject(prompt.get(), ALIGN_MAX_TOKENS, ALIGN_TIMEOUT_SEC, "AlignNarrative", true);
		if (parsed == null) {
			return original;
		}

		// Collect the raw aligned strings, then compress+limit them exactly as the source batches did. Any field
		// the reply omits or blanks falls back per-field to the original, so alignment only ever tightens copy.
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();

		String rawProposal = normalizer.textOrNull(parsed.get("proposal_overview"));

		JsonNode insightArr = parsed.get("strategic_insights");
		List<StrategicInsight> origInsights =
				strategic.strategicInsights() == null ? List.of() : strategic.strategicInsights();
		for (int i = 0; i < origInsights.size(); i++) {
			JsonNode item = (insightArr != null && insightArr.isArray() && i < insightArr.size())
					? insightArr.get(i) : null;
			String rawPoint = item == null ? "" : item.path("point").asText("").trim();
			String rawOverview = item == null ? "" : item.path("overview").asText("").trim();
			compressionFields.add(new ClaudeCompressionField("point_" + i, rawPoint, STRATEGIC_POINT_LIMIT));
			compressionFields.add(new ClaudeCompressionField("overview_" + i, rawOverview, STRATEGIC_OVERVIEW_LIMIT));
		}

		Map<Integer, String> rawOverviews = parseNumberedTextMap(parsed.get("results_overviews"));
		for (Map.Entry<Integer, String> e : rawOverviews.entrySet()) {
			compressionFields.add(new ClaudeCompressionField(
					"results_overview_" + e.getKey(), e.getValue(), RESULTS_OVERVIEW_LIMIT));
		}

		JsonNode thoughtArr = parsed.get("thoughts_on_performance");
		List<String> origThoughts =
				results.thoughtsOnPerformance() == null ? List.of() : results.thoughtsOnPerformance();
		for (int i = 0; i < origThoughts.size(); i++) {
			JsonNode item = (thoughtArr != null && thoughtArr.isArray() && i < thoughtArr.size())
					? thoughtArr.get(i) : null;
			String rawThought = item == null ? "" : item.asText("").trim();
			compressionFields.add(new ClaudeCompressionField("thought_" + i, rawThought, THOUGHT_LIMIT));
		}

		String rawFOpportunity = normalizer.textOrNull(parsed.get("f_opportunity"));
		String rawFFact = normalizer.textOrNull(parsed.get("f_fact"));
		String rawFStorytelling = normalizer.textOrNull(parsed.get("f_storytelling"));
		if (rawProposal != null) {
			compressionFields.add(new ClaudeCompressionField("proposal_overview", rawProposal, PROPOSAL_LIMIT));
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
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchE-Align");

		String alignedProposal = rawProposal == null
				? strategic.proposalOverview()
				: firstNonBlank(normalizer.normalizeProposal(compressed.get("proposal_overview"), PROPOSAL_LIMIT),
						strategic.proposalOverview());

		List<StrategicInsight> alignedInsights = new ArrayList<>();
		for (int i = 0; i < origInsights.size(); i++) {
			StrategicInsight fallback = origInsights.get(i);
			String point = firstNonBlank(
					normalizer.limitStrategicPoint(compressed.get("point_" + i)), fallback.point());
			String overview = firstNonBlank(
					normalizer.limitStrategicOverview(compressed.get("overview_" + i)), fallback.overview());
			alignedInsights.add(new StrategicInsight(point, overview));
		}
		if (alignedInsights.isEmpty()) {
			alignedInsights = origInsights.isEmpty() ? strategic.strategicInsights() : alignedInsights;
		}

		Map<Integer, String> alignedOverviews = new LinkedHashMap<>();
		Map<Integer, String> origOverviews =
				results.resultsOverviews() == null ? Map.of() : results.resultsOverviews();
		for (Map.Entry<Integer, String> e : origOverviews.entrySet()) {
			String aligned = rawOverviews.containsKey(e.getKey())
					? normalizer.limitResultsOverview(compressed.get("results_overview_" + e.getKey())) : null;
			alignedOverviews.put(e.getKey(), firstNonBlank(aligned, e.getValue()));
		}

		List<String> alignedThoughts = new ArrayList<>();
		for (int i = 0; i < origThoughts.size(); i++) {
			String fallback = origThoughts.get(i);
			String aligned = normalizer.normalizeC(compressed.get("thought_" + i), THOUGHT_LIMIT);
			alignedThoughts.add(firstNonBlank(aligned, fallback));
		}

		String fOpportunity = rawFOpportunity == null
				? results.fOpportunity()
				: firstNonBlank(normalizer.limitFOpportunity(compressed.get("f_opportunity")), results.fOpportunity());
		String fFact = rawFFact == null
				? results.fFact()
				: firstNonBlank(normalizer.limitFFact(compressed.get("f_fact")), results.fFact());
		String fStorytelling = rawFStorytelling == null
				? results.fStorytelling()
				: firstNonBlank(normalizer.limitFStorytelling(compressed.get("f_storytelling")), results.fStorytelling());

		ClaudeStrategic alignedStrategic = new ClaudeStrategic(
				strategic.audienceAge(), strategic.audienceSegments(), alignedProposal, alignedInsights);
		ClaudeResults alignedResults = new ClaudeResults(
				alignedOverviews, alignedThoughts, results.tacticOverviews(), results.recommendations(),
				fOpportunity, fFact, fStorytelling);
		return new ClaudeNarrative(alignedStrategic, alignedResults);
	}

	@Override
	public List<TacticConclusion> batchTacticConclusions(
			CampaignData data, List<TacticConclusionInput> inputs, String brief) {
		List<TacticConclusion> out = new ArrayList<>();
		if (inputs == null || inputs.isEmpty() || data == null) {
			return out;
		}
		for (int start = 0; start < inputs.size(); start += breakdownChunkSize) {
			List<TacticConclusionInput> chunk =
					inputs.subList(start, Math.min(start + breakdownChunkSize, inputs.size()));
			out.addAll(tacticConclusionsResilient(data, chunk, brief));
		}
		// Name the tactics that finished every attempt with no conclusion so a future blank-overview run is one
		// grep line ("shipped with no conclusion: [N]"), not a hunt back through raw reply snapshots. Each such
		// tactic's {{tactic N overview}} and breakdown copy fall back to dashes for manual fill.
		Set<Integer> produced = out.stream().map(TacticConclusion::tacticNum).collect(Collectors.toSet());
		List<Integer> missing = inputs.stream()
				.map(TacticConclusionInput::tacticNum)
				.filter(n -> !produced.contains(n))
				.toList();
		if (!missing.isEmpty()) {
			log.warn("[claude:BatchConclusions] {} of {} tactic(s) shipped with no conclusion: {} — their overview "
							+ "and breakdown copy fall back to dashes",
					missing.size(), inputs.size(), missing);
		}
		return out;
	}

	/**
	 * Runs one conclusions chunk and, when it comes back with nothing at all, retries rather than shipping the
	 * chunk's tactics with no conclusions. A multi-tactic chunk is retried one tactic per call so a single bad
	 * tactic cannot take its neighbours down, and a chunk already down to one tactic simply gets one more attempt.
	 *
	 * @param data  parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param chunk the tactics to cover
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactics' conclusions; empty only when every attempt failed
	 */
	List<TacticConclusion> tacticConclusionsResilient(
			CampaignData data, List<TacticConclusionInput> chunk, String brief) {
		List<TacticConclusion> res = tacticConclusionsChunk(data, chunk, brief);
		if (!res.isEmpty() || chunk.isEmpty()) {
			return res;
		}
		if (chunk.size() == 1) {
			log.warn("[claude:BatchConclusions] tactic {} came back empty — retrying once",
					chunk.getFirst().tacticNum());
			return tacticConclusionsChunk(data, chunk, brief);
		}
		log.warn("[claude:BatchConclusions] chunk {} came back empty — retrying one tactic per call",
				chunk.stream().map(TacticConclusionInput::tacticNum).toList());
		List<TacticConclusion> perTactic = new ArrayList<>();
		for (TacticConclusionInput input : chunk) {
			perTactic.addAll(tacticConclusionsResilient(data, List.of(input), brief));
		}
		return perTactic;
	}

	/**
	 * Runs one conclusions chunk: build the combined prompt, parse each tactic's object, compress every
	 * over-budget field in one pass, then assemble the per-tactic conclusions. Returns an empty list — never
	 * partial junk — when the call or parse fails, so only this chunk's tactics lose their conclusions.
	 *
	 * @param data  parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param chunk the tactics to cover in this single call
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactics' conclusions; empty when the chunk produced no usable reply
	 */
	List<TacticConclusion> tacticConclusionsChunk(
			CampaignData data, List<TacticConclusionInput> chunk, String brief) {
		List<TacticConclusion> out = new ArrayList<>();
		var prompt = promptBuilder.buildTacticConclusionsPrompt(
				data, chunk, brief, PUBLISHER_OBSERVATION_LIMIT, CREATIVE_TAKEAWAY_LIMIT, CREATIVE_RECO_LIMIT,
				GEO_INSIGHT_LIMIT, AUDIENCE_TAKEAWAY_LIMIT, AUDIENCE_SHORT_LIMIT, DEVICE_TAKEAWAY_LIMIT,
				DEVICE_SHORT_LIMIT);
		if (prompt.isEmpty()) {
			return out;
		}
		int maxTokens = Math.min(CONCLUSIONS_MAX_TOKENS_CAP, CONCLUSIONS_BASE_TOKENS
				+ CONCLUSIONS_TOKENS_PER_TACTIC * chunk.size());
		// allowPartial: a reply that ran past the budget still carries whole conclusions for the tactics it
		// finished, and salvaging those beats blanking the chunk over its unfinished tail.
		JsonNode parsed = messagesClient.callJsonObject(
				prompt.get(), maxTokens, BREAKDOWN_TIMEOUT_SEC, "BatchConclusions", true);
		if (parsed == null) {
			return out;
		}
		Map<Integer, JsonNode> byTactic = conclusionsByTactic(parsed);
		recoverBareConclusion(byTactic, parsed, chunk);

		// One compression pass over every over-budget field in the whole chunk, keyed by tactic+field so the
		// assembly below can read each rewritten value back.
		List<ClaudeCompressionField> fields = new ArrayList<>();
		for (TacticConclusionInput input : chunk) {
			JsonNode obj = byTactic.get(input.tacticNum());
			if (obj == null) {
				continue;
			}
			int n = input.tacticNum();
			addConclusionField(fields, n + "_overview", obj.get("overview"), TACTIC_OVERVIEW_LIMIT);
			if (input.publisher() != null) {
				addSectionFields(fields, n, "pub", obj.get("top_publishers"),
						PUBLISHER_OBSERVATION_COUNT, PUBLISHER_OBSERVATION_LIMIT, -1);
			}
			if (input.creative() != null) {
				addSectionFields(fields, n, "cre", obj.get("creative"),
						CREATIVE_TAKEAWAY_COUNT, CREATIVE_TAKEAWAY_LIMIT, CREATIVE_RECO_LIMIT);
			}
			if (input.geo() != null) {
				addSectionFields(fields, n, "geo", obj.get("geo"), GEO_BULLET_COUNT, GEO_INSIGHT_LIMIT, -1);
			}
			if (input.audience() != null) {
				addSectionFields(fields, n, "aud", obj.get("audience"),
						AUDIENCE_FIELD_COUNT, AUDIENCE_SHORT_LIMIT, AUDIENCE_TAKEAWAY_LIMIT);
			}
			if (input.device() != null) {
				addSectionFields(fields, n, "dev", obj.get("device"),
						DEVICE_FIELD_COUNT, DEVICE_SHORT_LIMIT, DEVICE_TAKEAWAY_LIMIT);
			}
		}
		Map<String, String> compressed = compressionService.compress(fields, "BatchD-Conclusions");

		for (TacticConclusionInput input : chunk) {
			JsonNode obj = byTactic.get(input.tacticNum());
			if (obj == null) {
				continue;
			}
			int n = input.tacticNum();
			String overview = obj.hasNonNull("overview")
					? normalizer.limitTacticOverview(compressed.get(n + "_overview")) : null;
			List<String> publisher = input.publisher() != null && obj.get("top_publishers") != null
					? assembleSection(compressed, n, "pub", PUBLISHER_OBSERVATION_COUNT, PUBLISHER_OBSERVATION_LIMIT, -1)
					: null;
			List<String> creative = input.creative() != null && obj.get("creative") != null
					? assembleSection(compressed, n, "cre", CREATIVE_TAKEAWAY_COUNT, CREATIVE_TAKEAWAY_LIMIT,
							CREATIVE_RECO_LIMIT)
					: null;
			List<String> geo = input.geo() != null && obj.get("geo") != null
					? assembleSection(compressed, n, "geo", GEO_BULLET_COUNT, GEO_INSIGHT_LIMIT, -1) : null;
			List<String> audience = input.audience() != null && obj.get("audience") != null
					? assembleSection(compressed, n, "aud", AUDIENCE_FIELD_COUNT, AUDIENCE_SHORT_LIMIT,
							AUDIENCE_TAKEAWAY_LIMIT)
					: null;
			List<String> device = input.device() != null && obj.get("device") != null
					? assembleSection(compressed, n, "dev", DEVICE_FIELD_COUNT, DEVICE_SHORT_LIMIT,
							DEVICE_TAKEAWAY_LIMIT)
					: null;
			out.add(new TacticConclusion(n, overview, publisher, creative, geo, audience, device));
		}
		return out;
	}

	/** Top-level keys that identify a conclusion object returned without its {@code tactic_N} wrapper. */
	private static final List<String> CONCLUSION_SECTION_KEYS =
			List.of("overview", "top_publishers", "creative", "geo", "audience", "device");

	/**
	 * Recovers a single-tactic reply the model returned without its {@code tactic_N} wrapper, i.e. as a bare
	 * {@code {"overview": ..., "top_publishers": [...], ...}} object rather than {@code {"tactic_5": {...}}}.
	 * The wrapper carries the only tactic number in the reply, so {@link #conclusionsByTactic} drops such a
	 * reply entirely and the tactic loses its overview and every breakdown section. This only fires when the
	 * chunk is exactly one tactic (so the number is unambiguous), the number-keyed map came back empty, and the
	 * bare object actually looks like a conclusion — otherwise it is a safe no-op.
	 *
	 * @param byTactic the number-keyed conclusion map, mutated in place when a bare object is recovered
	 * @param parsed   the parsed reply, possibly a bare conclusion object
	 * @param chunk    the tactics this call covered; recovery applies only to a single-tactic chunk
	 */
	void recoverBareConclusion(Map<Integer, JsonNode> byTactic, JsonNode parsed, List<TacticConclusionInput> chunk) {
		if (!byTactic.isEmpty() || chunk == null || chunk.size() != 1 || parsed == null || !parsed.isObject()) {
			return;
		}
		boolean looksLikeConclusion = CONCLUSION_SECTION_KEYS.stream().anyMatch(parsed::has);
		if (!looksLikeConclusion) {
			return;
		}
		int tacticNum = chunk.getFirst().tacticNum();
		log.warn("[claude:BatchConclusions] reply for tactic {} arrived without its tactic_N wrapper — "
				+ "recovering the bare conclusion object", tacticNum);
		byTactic.put(tacticNum, parsed);
	}

	/**
	 * Maps the combined reply's {@code {"tactic_1": {...}, …}} object to a number-keyed map of each tactic's
	 * conclusion object, recovering the tactic number from the key's digits via {@link #TACTIC_KEY} to tolerate
	 * key drift. Non-object values are skipped.
	 *
	 * @param parsed the parsed combined reply
	 * @return tactic number → its conclusion object, first writer wins per number
	 */
	Map<Integer, JsonNode> conclusionsByTactic(JsonNode parsed) {
		Map<Integer, JsonNode> byTactic = new LinkedHashMap<>();
		var fields = parsed.fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			Matcher matcher = TACTIC_KEY.matcher(entry.getKey().trim());
			if (!matcher.matches() || !entry.getValue().isObject()) {
				log.warn("[claude:BatchConclusions] reply key '{}' carries no tactic number or no object — ignoring",
						entry.getKey());
				continue;
			}
			byTactic.putIfAbsent(Integer.parseInt(matcher.group(1)), entry.getValue());
		}
		return byTactic;
	}

	/**
	 * Adds one over-budget-eligible field (the overview) to the compression batch when present.
	 *
	 * @param fields the accumulating compression fields
	 * @param key    the field key used to read the rewritten value back
	 * @param node   the raw JSON value (may be null/blank)
	 * @param limit  the field's character budget
	 */
	void addConclusionField(List<ClaudeCompressionField> fields, String key, JsonNode node, int limit) {
		String raw = node == null ? "" : node.asText("").trim();
		fields.add(new ClaudeCompressionField(key, raw, limit));
	}

	/**
	 * Adds a section's fixed-count array to the compression batch, keyed {@code <tactic>_<section>_<i>}. When
	 * {@code lastLimit} is non-negative it is used for the final index (the creative reco / audience-device
	 * takeaway budget differs from the other fields); otherwise every field uses {@code limit}.
	 *
	 * @param fields    the accumulating compression fields
	 * @param tacticNum the tactic number
	 * @param section   the short section tag ({@code pub}/{@code cre}/{@code geo}/{@code aud}/{@code dev})
	 * @param arr       the section's raw JSON array (may be null/not-array)
	 * @param count     the fixed number of fields the section carries
	 * @param limit     the character budget for the ordinary fields
	 * @param lastLimit the character budget for index 0 (takeaway) or the last index (reco), or -1 when uniform
	 */
	void addSectionFields(
			List<ClaudeCompressionField> fields, int tacticNum, String section, JsonNode arr, int count,
			int limit, int lastLimit) {
		for (int i = 0; i < count; i++) {
			String raw = arr != null && arr.isArray() && i < arr.size() ? arr.get(i).asText("").trim() : "";
			fields.add(new ClaudeCompressionField(
					tacticNum + "_" + section + "_" + i, raw, sectionFieldLimit(section, i, count, limit, lastLimit)));
		}
	}

	/**
	 * Assembles a section's normalized, length-capped strings from the compressed map, in slide order.
	 *
	 * @param compressed the compressed field map
	 * @param tacticNum  the tactic number
	 * @param section    the short section tag
	 * @param count      the fixed number of fields the section carries
	 * @param limit      the character budget for the ordinary fields
	 * @param lastLimit  the character budget for the special field, or -1 when uniform
	 * @return the section's {@code count} normalized strings, in slide order
	 */
	List<String> assembleSection(
			Map<String, String> compressed, int tacticNum, String section, int count, int limit, int lastLimit) {
		List<String> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int fieldLimit = sectionFieldLimit(section, i, count, limit, lastLimit);
			values.add(normalizer.normalizeC(compressed.get(tacticNum + "_" + section + "_" + i), fieldLimit));
		}
		return values;
	}

	/**
	 * Returns the character budget for one section field. Creative's reco is the LAST index; audience/device's
	 * takeaway is the FIRST index; publishers and geo are uniform. Encoded via {@code lastLimit}: -1 = uniform,
	 * creative uses it for the last index, audience/device for index 0.
	 *
	 * @param section   the short section tag
	 * @param i         the field index
	 * @param count     the section's field count
	 * @param limit     the ordinary budget
	 * @param lastLimit the special budget, or -1 when uniform
	 * @return the character budget for field {@code i}
	 */
	int sectionFieldLimit(String section, int i, int count, int limit, int lastLimit) {
		if (lastLimit < 0) {
			return limit;
		}
		if ("cre".equals(section)) {
			return i == count - 1 ? lastLimit : limit;
		}
		return i == 0 ? lastLimit : limit;
	}

	@Override
	public List<TacticThoughts> batchTacticThoughts(List<TacticThoughtsInput> inputs, String brief) {
		List<TacticThoughts> out = new ArrayList<>();
		if (inputs == null || inputs.isEmpty()) {
			return out;
		}
		for (TacticThoughtsInput input : inputs) {
			TacticThoughts thoughts = tacticThoughtsResilient(input, brief);
			if (thoughts != null) {
				out.add(thoughts);
			}
		}
		return out;
	}

	/**
	 * Runs one tactic's thoughts call and retries once when it comes back with nothing, rather than shipping
	 * the tactic's thoughts slide blank. A tactic whose retry also fails is dropped (returns {@code null}), so
	 * the caller renders those tokens blank rather than invented.
	 *
	 * @param input the tactic's assembled conclusions
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactic's four thoughts, or {@code null} when both attempts failed
	 */
	TacticThoughts tacticThoughtsResilient(TacticThoughtsInput input, String brief) {
		TacticThoughts thoughts = tacticThoughtsOne(input, brief);
		if (thoughts != null) {
			return thoughts;
		}
		log.warn("[claude:BatchTacticThoughts] tactic {} came back empty — retrying once", input.tacticNum());
		return tacticThoughtsOne(input, brief);
	}

	/**
	 * Runs one tactic's thoughts call: build the prompt, parse the four thoughts, compress any over-budget
	 * ones, and normalize. Returns {@code null} — never partial junk — when the call or parse fails.
	 *
	 * @param input the tactic's assembled conclusions
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactic's four thoughts, or {@code null} when the call produced no usable reply
	 */
	TacticThoughts tacticThoughtsOne(TacticThoughtsInput input, String brief) {
		var prompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, THOUGHT_LIMIT);
		if (prompt.isEmpty()) {
			return null;
		}
		JsonNode parsed = messagesClient.callJsonObject(
				prompt.get(), TACTIC_THOUGHTS_MAX_TOKENS, BREAKDOWN_TIMEOUT_SEC, "BatchTacticThoughts", true);
		if (parsed == null) {
			return null;
		}
		JsonNode arr = parsed.get("thoughts");
		if (arr == null || !arr.isArray()) {
			return null;
		}
		List<ClaudeCompressionField> fields = new ArrayList<>();
		for (int i = 0; i < TACTIC_THOUGHTS_COUNT; i++) {
			String raw = i < arr.size() ? arr.get(i).asText("").trim() : "";
			fields.add(new ClaudeCompressionField(input.tacticNum() + "_thought_" + i, raw, THOUGHT_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(fields, "BatchD-TacticThoughts");
		List<String> thoughts = new ArrayList<>(TACTIC_THOUGHTS_COUNT);
		for (int i = 0; i < TACTIC_THOUGHTS_COUNT; i++) {
			thoughts.add(normalizer.normalizeC(compressed.get(input.tacticNum() + "_thought_" + i), THOUGHT_LIMIT));
		}
		return new TacticThoughts(input.tacticNum(), thoughts);
	}

	@Override
	public ClaudeResults batchCampaignResults(
			CampaignData data, String brief, CampaignFrequencies frequencies, List<TacticNarrativeDigest> perTactic) {
		var prompt = promptBuilder.buildCampaignResultsPrompt(data, brief, frequencies, perTactic);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyResults();
		}
		int tacticCount = data.tactics() == null ? 0 : data.tactics().size();
		int maxTokens = Math.min(CAMPAIGN_RESULTS_MAX_TOKENS_CAP,
				CAMPAIGN_RESULTS_BASE_TOKENS + CAMPAIGN_RESULTS_TOKENS_PER_TACTIC * tacticCount);
		int timeoutSec = tacticCount > 10 ? 120 : 60;
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), maxTokens, timeoutSec, "BatchCampaign", true);
		if (parsed == null) {
			return claudeDefaults.emptyResults();
		}

		Map<Integer, String> rawResultsOverviews = parseNumberedTextMap(parsed.get("results_overviews"));
		List<String> rawThoughts =
				normalizer.normalizeThoughts(normalizer.textOrNull(parsed.get("thoughts_on_performance")));

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
		for (int i = 0; i < 4; i++) {
			compressionFields.add(
					new ClaudeCompressionField("rec_title_" + i, rawRecTitles[i], RECOMMENDATION_TITLE_LIMIT));
			compressionFields.add(
					new ClaudeCompressionField("rec_text_" + i, rawRecTexts[i], RECOMMENDATION_TEXT_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Campaign");

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

		// tacticOverviews is intentionally empty: the overviews come from Step 2 and the orchestrator merges
		// them into the aggregate before the placeholder resolver reads it.
		return new ClaudeResults(resultsOverviews, thoughts, Map.of(), recommendations,
				fOpportunity, fFact, fStorytelling);
	}

	@Override
	public ClaudeNarrative batchAlignCampaign(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief) {
		// The campaign-level Step-5 pass is exactly the existing narrative alignment (Batch A strategic +
		// campaign results into one brief-faithful storyline), so it delegates rather than duplicating it.
		return batchAlignNarrative(strategic, results, breakdownDigest, brief);
	}

	/**
	 * Returns {@code value} when it is non-null and not blank, otherwise {@code fallback}. Used by the Batch D
	 * alignment merge so any field the model dropped or returned empty keeps its original, un-aligned copy
	 * rather than blanking on the slide.
	 *
	 * @param value    the aligned candidate string (may be {@code null} or blank)
	 * @param fallback the original value to keep when {@code value} carries nothing usable
	 * @return {@code value} when usable, otherwise {@code fallback}
	 */
	String firstNonBlank(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
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
	public String summarizeGeo(List<List<String>> geoRows) {
		if (geoRows == null || geoRows.isEmpty()) {
			return null;
		}
		List<String> kept = geoFilter.keepGeoRows(geoRows);
		if (kept.isEmpty()) {
			log.info("[claude:Geo] no geography-related row in the workbook; skipping the summary");
			return null;
		}
		String prompt = promptBuilder.buildGeoPrompt(kept);
		if (!tokenEstimator.fitsWithin(prompt, GEO_PROMPT_MAX_TOKENS)) {
			log.warn("[claude:Geo] filtered workbook still ~{} tokens (budget {}); skipping the summary so "
							+ "{{geo_locations}} falls back to a dash for the user to fill in",
					tokenEstimator.estimateTokens(prompt), GEO_PROMPT_MAX_TOKENS);
			return null;
		}
		JsonNode resp = messagesClient.callRaw(prompt, 60, 30, "Geo");
		if (resp == null) {
			return null;
		}
		return normalizer.limitGeoSummary(normalizer.extractText(resp));
	}

	@Override
	public String summarizeFunnelStages(List<String> tacticGoals) {
		var prompt = promptBuilder.buildFunnelFromGoalsPrompt(tacticGoals);
		if (prompt.isEmpty()) {
			return null;
		}
		JsonNode resp = messagesClient.callRaw(prompt.get(), 60, 30, "Funnel");
		if (resp == null) {
			return null;
		}
		String text = normalizer.extractText(resp);
		return text == null || text.isBlank() ? null : text.trim();
	}

	@Override
	public String digestBrief(String brief) {
		var prompt = promptBuilder.buildBriefDigestPrompt(brief, BRIEF_DIGEST_LIMIT);
		if (prompt.isEmpty()) {
			return null;
		}
		JsonNode resp = messagesClient.callRaw(
				prompt.get(), BRIEF_DIGEST_MAX_TOKENS, BRIEF_DIGEST_TIMEOUT_SEC, "BriefDigest");
		if (resp == null) {
			log.warn("[claude:BriefDigest] digest failed; the raw brief is used as context instead");
			return null;
		}
		String text = normalizer.extractText(resp);
		if (text == null || text.isBlank()) {
			return null;
		}
		return normalizer.normalizeC(text.trim(), BRIEF_DIGEST_LIMIT);
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
