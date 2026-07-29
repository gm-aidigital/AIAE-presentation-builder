package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

	/**
	 * Character budget shared by the "Top Publishers" and "Creative analysis" bullets, whose slide fields hold
	 * a full analyst sentence.
	 *
	 * <p>The 100 characters a creative takeaway used to carry were below what the prompt asks each string to
	 * do: name the real creative, cite a real metric, and land the business consequence. Quoted to Claude at
	 * {@code COMPRESSION_PROMPT_BUFFER_RATIO}, 100 reached the model as 80 — about twelve words for three
	 * demands — so the model either overran the budget (paying for a compression call) or dropped the numbers
	 * to fit, which is the one thing the "no generic language" principle forbids.
	 *
	 * <p>Deliberately not shared with geo and the audience/device short fields: those slide fields are narrower
	 * and keep their own budgets.
	 */
	private static final int SECTION_BULLET_LIMIT = 160;

	/** Character budget of one {@code {{publishers_observation_N_x}}} bullet on the slide. */
	private static final int PUBLISHER_OBSERVATION_LIMIT = SECTION_BULLET_LIMIT;

	/** Bullets per tactic on the "Top Publishers" slide. */
	private static final int PUBLISHER_OBSERVATION_COUNT = 4;

	/** Character budget of the first three {@code {{cr_takeaway_tactic N_x}}} bullets on the slide. */
	private static final int CREATIVE_TAKEAWAY_LIMIT = SECTION_BULLET_LIMIT;

	/**
	 * Character budget of the fourth {@code {{cr_takeaway_tactic N_4}}} bullet, which carries the mid-flight
	 * optimisation <em>and</em> its result. Kept as its own constant even though it now matches the other three,
	 * because it is a separate slide field whose budget can move independently of them.
	 */
	private static final int CREATIVE_RECO_LIMIT = SECTION_BULLET_LIMIT;

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

	// BatchSheet emits one small per-tactic object (gender split + two peak windows) plus the two audience
	// fields. A fixed cap risks a campaign with enough tactics overrunning a flat budget: the reply gets cut
	// mid-JSON and the whole call — audience fields included — falls back to blank. Scale with the tactic
	// count instead, with a small per-tactic allowance since each tactic's payload here is a handful of short
	// fields, not prose.
	private static final int BATCH_SHEET_BASE_TOKENS = 600;
	private static final int BATCH_SHEET_TOKENS_PER_TACTIC = 60;
	private static final int BATCH_SHEET_MAX_TOKENS_CAP = 3000;

	/**
	 * Output budget for the Step-2 combined conclusions call. Each tactic can carry an overview plus up to five
	 * sections (~21 strings), so the per-tactic allowance is generous; the base covers JSON overhead and the cap
	 * bounds a large chunk. A reply that still overruns salvages the tactics it finished (allowPartial).
	 */
	private static final int CONCLUSIONS_BASE_TOKENS = 1500;
	private static final int CONCLUSIONS_TOKENS_PER_TACTIC = 1200;
	private static final int CONCLUSIONS_MAX_TOKENS_CAP = 8000;

	/**
	 * Output budget for a conclusions chunk whose tactics carry no section at all — the shape the call has
	 * whenever the per-section calls own the breakdown copy. The reply is then one ~190-character overview per
	 * tactic, so the section-sized allowance above is roughly twenty times what the reply can use: it stops
	 * bounding anything, and a model that wanders into an essay is billed for every token of it. These figures
	 * are the same two-characters-per-token sizing the other budgets use, with head-room for an overrun.
	 */
	private static final int CONCLUSIONS_OVERVIEW_BASE_TOKENS = 200;
	private static final int CONCLUSIONS_OVERVIEW_TOKENS_PER_TACTIC = 120;

	/** The per-tactic "thoughts on tactic performance" slide holds exactly four thought strings. */
	private static final int TACTIC_THOUGHTS_COUNT = 4;
	/** Short tag identifying the Step-3 per-tactic thoughts call in logs and on the report's failure card. */
	private static final String THOUGHTS_LABEL = "BatchTacticThoughts";
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

	// Batch D (narrative alignment) rewrites the campaign-level copy — the proposal, the strategic insights,
	// one results overview per tactic group, the performance thoughts and three frequency strings — and never
	// the per-tactic overviews or breakdown bullets. That is bounded, but not fixed: a deck with many tactic
	// groups asks the model to re-emit far more overviews than the four a flat 3000-token budget was sized for,
	// and because this call salvages a truncated reply (allowPartial) the overrun is invisible — the trailing
	// fields simply keep their un-aligned text. So the budget is derived from the fields the draft actually
	// carries, each allowance sized off that field's own character limit at roughly two characters per token,
	// which leaves head-room for a model that writes past the limit it was asked for.
	private static final int ALIGN_BASE_TOKENS = 400;
	private static final int ALIGN_TOKENS_PER_PROPOSAL = 220;
	private static final int ALIGN_TOKENS_PER_INSIGHT = 160;
	private static final int ALIGN_TOKENS_PER_OVERVIEW = 220;
	private static final int ALIGN_TOKENS_PER_THOUGHT = 140;
	private static final int ALIGN_TOKENS_PER_FREQUENCY_FIELD = 200;
	private static final int ALIGN_MAX_TOKENS_CAP = 8000;
	private static final int ALIGN_TIMEOUT_SEC = 90;

	/**
	 * Sends allowed for the alignment call, counting the first. Unlike every other batch, this pass has no
	 * partial-failure mode a caller can repair: a rejected reply drops the whole alignment and every field
	 * keeps its un-aligned text, so one re-ask is cheap insurance on a single call per report.
	 */
	private static final int ALIGN_ATTEMPTS = 2;

	/**
	 * Appended to the alignment prompt on the re-ask. The first attempt already asks for bare JSON, so the
	 * retry does not restate the editorial brief — it only closes off the one failure this loop can see, a
	 * reply that wrapped its object in prose or never produced an object at all.
	 */
	private static final String ALIGN_RETRY_SUFFIX =
			"\n\nIMPORTANT: the previous reply could not be parsed. Return the JSON object and nothing else — "
					+ "the first character must be { and the last must be }, with no prose, preamble or "
					+ "backticks around it.";

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
	/** Run-scoped sink the reasons rejected replies were thrown away go to, for the report's own card. */
	private final ClaudeFailureLog failureLog;
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
			ClaudeFailureLog failureLog,
			AnthropicProperties anthropicProperties) {
		this.messagesClient = messagesClient;
		this.promptBuilder = promptBuilder;
		this.normalizer = normalizer;
		this.compressionService = compressionService;
		this.claudeDefaults = claudeDefaults;
		this.geoFilter = geoFilter;
		this.tokenEstimator = tokenEstimator;
		this.failureLog = failureLog;
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
		String rejection = null;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			// The first attempt sends the prompt as built; every later one carries what the previous reply got
			// wrong, so a deterministic rejection is not simply reproduced by an identical re-send.
			String attemptPrompt =
					rejection == null ? prompt.get() : prompt.get() + sectionRetrySuffix(rejection, count);
			ClaudeSectionAttempt res = sectionOnce(label, tacticNum, attemptPrompt, count, limitAt);
			if (!res.values().isEmpty()) {
				return res.values();
			}
			rejection = res.rejection();
			if (attempt < attempts) {
				log.warn("[claude:{}] tactic {} reply failed the {}-string contract — retry {}/{}",
						label, tacticNum, count, attempt, sectionRetries);
			}
		}
		log.warn("[claude:{}] tactic {} produced no usable copy after {} attempt(s) — its fields ship blank",
				label, tacticNum, attempts);
		failureLog.record(label, "tactic " + tacticNum + " gave up after " + attempts
				+ " attempt(s); its slide fields ship blank.");
		return List.of();
	}

	/**
	 * Reports one rejected section reply to both audiences at once: the server log, and the run's failure
	 * scope so the reason reaches the "Report ready" card of the person who ran the report — who, on a hosted
	 * deployment, is the one person who cannot read the log.
	 *
	 * @param label     short tag identifying the section, e.g. {@code "PublisherSection"}
	 * @param tacticNum the tactic whose section reply was rejected
	 * @param reason    what was wrong with the reply, in words that survive being shown to a user
	 */
	void rejectSection(String label, int tacticNum, String reason) {
		log.warn("[claude:{}] tactic {} rejected: {}", label, tacticNum, reason);
		failureLog.record(label, "tactic " + tacticNum + " — " + reason + ".");
	}

	/**
	 * Reports a rejected section reply and packages the reason as a failed attempt, so the reason reaches both
	 * audiences and the retry at once.
	 *
	 * @param label     short tag identifying the section, e.g. {@code "PublisherSection"}
	 * @param tacticNum the tactic whose section reply was rejected
	 * @param reason    what was wrong with the reply, in words that survive being shown to a user
	 * @return a failed attempt carrying the reason
	 */
	ClaudeSectionAttempt rejectedSection(String label, int tacticNum, String reason) {
		rejectSection(label, tacticNum, reason);
		return new ClaudeSectionAttempt(List.of(), reason);
	}

	/**
	 * Builds the note appended to a section prompt on a retry: what the previous reply got wrong, plus the
	 * output contract restated.
	 *
	 * <p>Without it the retry is the same prompt sent twice, which is how a rejected reply used to be rejected
	 * again for the same reason — the failure mode measured on job 184, where nine attempts across three
	 * sections all ended in blank slides. Naming the defect gives the model something to correct.
	 *
	 * @param rejection what was wrong with the previous reply
	 * @param count     the exact number of strings the array must carry
	 * @return the retry note, ready to append to the prompt
	 */
	String sectionRetrySuffix(String rejection, int count) {
		return "\n\nIMPORTANT: your previous reply was rejected — " + rejection + ". Return ONLY a JSON array of "
				+ count + " non-empty strings, in the order described above: the first character must be [ and the "
				+ "last must be ], with no prose, no keys and no backticks.";
	}

	/**
	 * Runs one section call once and enforces the positional contract: the reply is accepted when it parses as a
	 * JSON array carrying at least {@code count} items whose first {@code count} are all non-blank, and the
	 * first {@code count} are what ship. A short array, a non-array, a blank slot or a failed call is rejected
	 * with its reason so {@link #runSection} can retry with that reason attached, rather than accepting a partial
	 * reply. Accepted strings are compressed and normalized to the same per-index budgets the combined path
	 * uses, so the per-section and combined paths yield identical field shapes.
	 *
	 * @param label     short tag identifying the section in log/compression messages
	 * @param tacticNum the tactic number, used to key the compression fields
	 * @param prompt    the built section prompt text, including any retry note
	 * @param count     the number of non-blank strings the slide has slots for
	 * @param limitAt   the character budget for the field at a given index
	 * @return the accepted attempt carrying {@code count} normalized strings, or a rejected one carrying the reason
	 */
	ClaudeSectionAttempt sectionOnce(
			String label, int tacticNum, String prompt, int count, java.util.function.IntUnaryOperator limitAt) {
		// allowPartial lets the transport repair a reply the model never closed — the same salvage every other
		// batch already gets. Nothing partial slips through: the shortfall check below still rejects an array
		// that lost an item to the repair, so the attempt is retried rather than shipped short.
		JsonNode arr = messagesClient.callJsonArray(
				prompt, SECTION_MAX_TOKENS, BREAKDOWN_TIMEOUT_SEC, label, true);
		if (arr == null || !arr.isArray()) {
			// The call itself failed or the reply was not an array; the transport already logged the cause, so
			// this line only ties that cause to the section and tactic whose fields are about to ship blank.
			return rejectedSection(label, tacticNum, "no JSON array in the reply");
		}
		// Tolerate an accidental one-level wrapper array — the model sometimes returns [[...]] (an array whose
		// only element is the real array of strings) instead of a flat [...]. Unwrap it before the count check;
		// the "count non-blank strings" contract still applies to the unwrapped array.
		if (arr.size() == 1 && arr.get(0).isArray()) {
			arr = arr.get(0);
		}
		if (arr.size() < count) {
			// Short is genuinely unusable — a slot would ship blank — so this is the one count that is retried.
			return rejectedSection(label, tacticNum,
					"the reply held " + arr.size() + " item(s), expected " + count);
		}
		if (arr.size() > count) {
			// Over-long is not a defect worth a re-send: the slide has count slots, the strings are in the asked
			// order, and the extras are surplus commentary. Rejecting the whole reply here used to cost a second
			// call and often a blank slide, when the copy the slide needs had already arrived.
			log.info("[claude:{}] tactic {} reply held {} items for {} slots — keeping the first {}",
					label, tacticNum, arr.size(), count, count);
		}
		List<String> raw = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String value = arr.get(i).asText("").trim();
			if (value.isBlank()) {
				return rejectedSection(label, tacticNum,
						"item " + i + " of " + count + " was blank (a " + arr.get(i).getNodeType() + " node)");
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
		return new ClaudeSectionAttempt(out, null);
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
		int tacticCount = data.tactics() == null ? 0 : data.tactics().size();
		int maxTokens = Math.min(BATCH_SHEET_MAX_TOKENS_CAP,
				BATCH_SHEET_BASE_TOKENS + BATCH_SHEET_TOKENS_PER_TACTIC * tacticCount);
		int timeoutSec = tacticCount > 10 ? 120 : 60;
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), maxTokens, timeoutSec, "BatchSheet", true);
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
		JsonNode parsed = callAlignWithRetry(prompt.get(), alignMaxTokens(strategic, results));
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

	/**
	 * Derives the output budget for the alignment call from the copy the draft actually carries.
	 *
	 * <p>Every field in the draft is re-emitted by the model, so each one is given an allowance sized off its
	 * own character limit rather than a share of one flat cap — that is what keeps a deck with many tactic
	 * groups from having its trailing overviews cut off and silently left un-aligned. The total is bounded by
	 * {@link #ALIGN_MAX_TOKENS_CAP} so a pathological draft cannot ask for an unbounded reply.
	 *
	 * @param strategic the Batch A copy whose proposal and insights are being aligned; not null
	 * @param results   the Batch C copy whose overviews, thoughts and frequency strings are being aligned; not null
	 * @return tokens the alignment reply may use
	 */
	int alignMaxTokens(ClaudeStrategic strategic, ClaudeResults results) {
		int budget = ALIGN_BASE_TOKENS;
		if (strategic.proposalOverview() != null && !strategic.proposalOverview().isBlank()) {
			budget += ALIGN_TOKENS_PER_PROPOSAL;
		}
		if (strategic.strategicInsights() != null) {
			budget += strategic.strategicInsights().size() * ALIGN_TOKENS_PER_INSIGHT;
		}
		if (results.resultsOverviews() != null) {
			budget += results.resultsOverviews().size() * ALIGN_TOKENS_PER_OVERVIEW;
		}
		if (results.thoughtsOnPerformance() != null) {
			budget += results.thoughtsOnPerformance().size() * ALIGN_TOKENS_PER_THOUGHT;
		}
		budget += ALIGN_TOKENS_PER_FREQUENCY_FIELD
				* countNonBlank(results.fOpportunity(), results.fFact(), results.fStorytelling());
		return Math.min(ALIGN_MAX_TOKENS_CAP, budget);
	}

	/**
	 * Counts how many of the given strings carry text, treating null and whitespace-only alike.
	 *
	 * @param values the strings to weigh; may contain nulls
	 * @return the number of values holding non-blank text
	 */
	int countNonBlank(String... values) {
		int count = 0;
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Sends the alignment prompt, re-asking once when the reply comes back unusable.
	 *
	 * <p>Only the parse outcome is retried here: a transient upstream failure is already retried inside the
	 * transport, so an unreachable API is not hammered by this loop, and the re-ask carries
	 * {@link #ALIGN_RETRY_SUFFIX} because the failure this sees is almost always a reply that wrapped its
	 * object in prose. Both sends share the transport's concurrency permit and the call is one per report, so
	 * the worst case costs one extra alignment-sized request.
	 *
	 * @param prompt    the Batch D prompt as built
	 * @param maxTokens output budget for the reply
	 * @return the parsed reply object, or {@code null} when no attempt produced a usable one
	 */
	JsonNode callAlignWithRetry(String prompt, int maxTokens) {
		for (int attempt = 1; attempt <= ALIGN_ATTEMPTS; attempt++) {
			String attemptPrompt = attempt == 1 ? prompt : prompt + ALIGN_RETRY_SUFFIX;
			JsonNode parsed = messagesClient.callJsonObject(
					attemptPrompt, maxTokens, ALIGN_TIMEOUT_SEC, "AlignNarrative", true);
			if (parsed != null) {
				return parsed;
			}
			if (attempt < ALIGN_ATTEMPTS) {
				log.warn("[claude:AlignNarrative] unusable reply on attempt {} of {}; re-asking with the "
						+ "JSON-only demand restated", attempt, ALIGN_ATTEMPTS);
			}
		}
		log.warn("[claude:AlignNarrative] no usable reply after {} attempts; keeping the un-aligned copy",
				ALIGN_ATTEMPTS);
		return null;
	}

	@Override
	public List<TacticConclusion> batchTacticConclusions(
			CampaignData data, List<TacticConclusionInput> inputs, String brief) {
		List<TacticConclusion> out = new ArrayList<>();
		if (inputs == null || inputs.isEmpty() || data == null) {
			return out;
		}
		List<List<TacticConclusionInput>> chunks = new ArrayList<>();
		for (int start = 0; start < inputs.size(); start += breakdownChunkSize) {
			chunks.add(List.copyOf(inputs.subList(start, Math.min(start + breakdownChunkSize, inputs.size()))));
		}
		out.addAll(runConclusionChunks(data, chunks, brief));
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
	 * Runs every conclusions chunk, concurrently once there is more than one. The chunks are independent calls
	 * against an upstream that answers in seconds, so running them in sequence made the step's wall-clock the
	 * sum of every call while the shared call limiter sat idle; fanning them out makes it roughly the slowest
	 * call. Real concurrency against Anthropic is still bounded by the semaphore in {@link
	 * AnthropicMessagesClient}, so this widens the queue, not the rate. A chunk that fails outright costs only
	 * its own tactics, exactly as it did in sequence.
	 *
	 * @param data   parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param chunks the chunks to run, already sliced to the configured size
	 * @param brief  free-text campaign brief passed through to the prompt
	 * @return every chunk's conclusions, ordered by tactic number
	 */
	List<TacticConclusion> runConclusionChunks(
			CampaignData data, List<List<TacticConclusionInput>> chunks, String brief) {
		if (chunks.size() == 1) {
			return tacticConclusionsResilient(data, chunks.getFirst(), brief);
		}
		List<TacticConclusion> out = new ArrayList<>();
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			List<CompletableFuture<List<TacticConclusion>>> futures = chunks.stream()
					.map(chunk -> CompletableFuture.supplyAsync(
							() -> tacticConclusionsResilient(data, chunk, brief), pool))
					.toList();
			for (CompletableFuture<List<TacticConclusion>> future : futures) {
				out.addAll(joinConclusions(future));
			}
		}
		out.sort(Comparator.comparingInt(TacticConclusion::tacticNum));
		return out;
	}

	/**
	 * Waits for one chunk's conclusions, turning a chunk that threw into an empty result so the remaining
	 * chunks still ship. The tactics behind a failed chunk are named by the caller's missing-tactic warning.
	 *
	 * @param future the chunk's pending conclusions
	 * @return the chunk's conclusions, or empty when it failed
	 */
	List<TacticConclusion> joinConclusions(CompletableFuture<List<TacticConclusion>> future) {
		try {
			return future.join();
		} catch (CompletionException | CancellationException e) {
			log.warn("[claude:BatchConclusions] a conclusions chunk failed outright ({}) — its tactics fall back "
					+ "to dashes", e.getMessage());
			return List.of();
		}
	}

	/**
	 * Runs one conclusions chunk and retries the tactics it did not answer for, rather than shipping them with
	 * no conclusion. A tactic counts as answered only when it came back with a non-blank overview: the prompt
	 * marks that field ALWAYS-produce, so an object that parsed without one is a failed answer, not a choice,
	 * and before this it was shipped straight to the slide as a dash. Only the unanswered tactics are re-asked,
	 * one per call, so a chunk's good conclusions are never re-billed and one bad tactic cannot take its
	 * neighbours down; a chunk already down to one tactic simply gets one more attempt. A retry that comes back
	 * with nothing usable leaves the original reply in place — it may still carry that tactic's section copy.
	 *
	 * @param data  parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param chunk the tactics to cover
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactics' conclusions; empty only when every attempt failed
	 */
	List<TacticConclusion> tacticConclusionsResilient(
			CampaignData data, List<TacticConclusionInput> chunk, String brief) {
		if (chunk == null || chunk.isEmpty()) {
			return List.of();
		}
		List<TacticConclusion> res = tacticConclusionsChunk(data, chunk, brief);
		Set<Integer> answered = answeredTactics(res);
		List<TacticConclusionInput> missing = chunk.stream()
				.filter(input -> !answered.contains(input.tacticNum()))
				.toList();
		if (missing.isEmpty()) {
			return res;
		}
		if (chunk.size() == 1) {
			log.warn("[claude:BatchConclusions] tactic {} came back with no usable overview — retrying once",
					chunk.getFirst().tacticNum());
			List<TacticConclusion> retry = tacticConclusionsChunk(data, chunk, brief);
			return answeredTactics(retry).isEmpty() ? res : retry;
		}
		log.warn("[claude:BatchConclusions] chunk {} left tactic(s) {} with no usable overview — re-asking those "
						+ "one per call", chunk.stream().map(TacticConclusionInput::tacticNum).toList(),
				missing.stream().map(TacticConclusionInput::tacticNum).toList());
		List<TacticConclusion> merged = new ArrayList<>(
				res.stream().filter(conclusion -> answered.contains(conclusion.tacticNum())).toList());
		for (TacticConclusionInput input : missing) {
			List<TacticConclusion> retried = tacticConclusionsResilient(data, List.of(input), brief);
			if (retried.isEmpty()) {
				res.stream().filter(conclusion -> conclusion.tacticNum() == input.tacticNum()).forEach(merged::add);
			} else {
				merged.addAll(retried);
			}
		}
		merged.sort(Comparator.comparingInt(TacticConclusion::tacticNum));
		return merged;
	}

	/**
	 * The tactic numbers a conclusions reply actually answered for, i.e. those carrying a non-blank overview.
	 *
	 * @param conclusions the conclusions parsed out of one reply
	 * @return the answered tactic numbers
	 */
	Set<Integer> answeredTactics(List<TacticConclusion> conclusions) {
		return conclusions.stream()
				.filter(conclusion -> conclusion.overview() != null && !conclusion.overview().isBlank())
				.map(TacticConclusion::tacticNum)
				.collect(Collectors.toSet());
	}

	/**
	 * Output budget for one conclusions chunk. A chunk carrying sections is sized for an overview plus up to
	 * five section blocks per tactic; a chunk carrying none is sized for the overviews alone, which is all the
	 * reply can contain — see {@link #CONCLUSIONS_OVERVIEW_BASE_TOKENS}.
	 *
	 * @param chunk the tactics this call covers
	 * @return the {@code max_tokens} to send
	 */
	int conclusionsMaxTokens(List<TacticConclusionInput> chunk) {
		if (chunk.stream().noneMatch(promptBuilder::carriesAnySection)) {
			return Math.min(CONCLUSIONS_MAX_TOKENS_CAP, CONCLUSIONS_OVERVIEW_BASE_TOKENS
					+ CONCLUSIONS_OVERVIEW_TOKENS_PER_TACTIC * chunk.size());
		}
		return Math.min(CONCLUSIONS_MAX_TOKENS_CAP, CONCLUSIONS_BASE_TOKENS
				+ CONCLUSIONS_TOKENS_PER_TACTIC * chunk.size());
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
		int maxTokens = conclusionsMaxTokens(chunk);
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
	public TacticThoughts tacticThoughts(TacticThoughtsInput input, String brief) {
		if (input == null) {
			return null;
		}
		return tacticThoughtsResilient(input, brief);
	}

	/**
	 * Runs one tactic's thoughts call and retries once when the reply is not the full set of four thoughts,
	 * rather than shipping a half-filled thoughts slide. When neither attempt is complete the fuller of the
	 * two is still returned — a reply carrying three real thoughts beats blanking all four — and only a tactic
	 * whose both attempts produced nothing usable is dropped ({@code null}), so its tokens render blank rather
	 * than invented.
	 *
	 * @param input the tactic's assembled conclusions
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactic's thoughts, or {@code null} when neither attempt produced a usable one
	 */
	TacticThoughts tacticThoughtsResilient(TacticThoughtsInput input, String brief) {
		TacticThoughts first = tacticThoughtsOne(input, brief);
		if (isCompleteThoughts(first)) {
			return first;
		}
		log.warn("[claude:{}] tactic {} came back {} — retrying once",
				THOUGHTS_LABEL, input.tacticNum(), first == null ? "empty" : "incomplete");
		TacticThoughts second = tacticThoughtsOne(input, brief);
		if (isCompleteThoughts(second)) {
			return second;
		}
		TacticThoughts best = fullerThoughts(first, second);
		if (best == null) {
			rejectSection(THOUGHTS_LABEL, input.tacticNum(),
					"no usable thoughts after 2 attempts; its slide fields ship blank");
		}
		return best;
	}

	/**
	 * Runs one tactic's thoughts call: build the prompt, parse the four thoughts, compress any over-budget
	 * ones, and normalize. A reply whose {@code thoughts} array is missing, is not an array, or holds nothing
	 * but blanks is rejected outright ({@code null}) — a well-formed but empty array is exactly the shape that
	 * used to pass as a success and blank the slide silently. A reply that fills some but not all four slots is
	 * returned as-is for {@link #tacticThoughtsResilient} to retry on.
	 *
	 * @param input the tactic's assembled conclusions
	 * @param brief free-text campaign brief passed through to the prompt
	 * @return the tactic's thoughts, possibly with fewer than four filled, or {@code null} when unusable
	 */
	TacticThoughts tacticThoughtsOne(TacticThoughtsInput input, String brief) {
		var prompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, THOUGHT_LIMIT);
		if (prompt.isEmpty()) {
			return null;
		}
		JsonNode parsed = messagesClient.callJsonObject(
				prompt.get(), TACTIC_THOUGHTS_MAX_TOKENS, BREAKDOWN_TIMEOUT_SEC, THOUGHTS_LABEL, true);
		if (parsed == null) {
			return null;
		}
		JsonNode arr = parsed.get("thoughts");
		if (arr == null || !arr.isArray()) {
			rejectSection(THOUGHTS_LABEL, input.tacticNum(), "reply carried no \"thoughts\" array");
			return null;
		}
		List<ClaudeCompressionField> fields = new ArrayList<>();
		int filled = 0;
		for (int i = 0; i < TACTIC_THOUGHTS_COUNT; i++) {
			String raw = i < arr.size() ? arr.get(i).asText("").trim() : "";
			if (!raw.isBlank()) {
				filled++;
			}
			fields.add(new ClaudeCompressionField(input.tacticNum() + "_thought_" + i, raw, THOUGHT_LIMIT));
		}
		if (filled == 0) {
			rejectSection(THOUGHTS_LABEL, input.tacticNum(),
					"reply carried " + arr.size() + " item(s) but no non-blank thought");
			return null;
		}
		if (filled < TACTIC_THOUGHTS_COUNT) {
			rejectSection(THOUGHTS_LABEL, input.tacticNum(),
					"reply filled only " + filled + " of " + TACTIC_THOUGHTS_COUNT + " thoughts");
		}
		Map<String, String> compressed = compressionService.compress(fields, "BatchD-TacticThoughts");
		List<String> thoughts = new ArrayList<>(TACTIC_THOUGHTS_COUNT);
		for (int i = 0; i < TACTIC_THOUGHTS_COUNT; i++) {
			thoughts.add(normalizer.normalizeC(compressed.get(input.tacticNum() + "_thought_" + i), THOUGHT_LIMIT));
		}
		return new TacticThoughts(input.tacticNum(), thoughts);
	}

	/**
	 * Reports whether a thoughts reply filled every slot the slide carries, which is the only shape worth
	 * accepting without a retry. Counted after normalization, so a thought that survived the call but was
	 * dropped as blank by the length pass counts as missing.
	 *
	 * @param thoughts the parsed thoughts, or {@code null} when the call produced nothing usable
	 * @return {@code true} when all {@link #TACTIC_THOUGHTS_COUNT} thoughts are present and non-blank
	 */
	boolean isCompleteThoughts(TacticThoughts thoughts) {
		return countThoughts(thoughts) == TACTIC_THOUGHTS_COUNT;
	}

	/**
	 * Counts the non-blank thoughts a reply carries, tolerating a {@code null} reply and the {@code null}
	 * entries {@link ClaudeResponseNormalizer#normalizeC} leaves behind for blanks.
	 *
	 * @param thoughts the parsed thoughts, or {@code null}
	 * @return how many slide-ready thoughts it holds
	 */
	int countThoughts(TacticThoughts thoughts) {
		if (thoughts == null || thoughts.thoughts() == null) {
			return 0;
		}
		int filled = 0;
		for (String thought : thoughts.thoughts()) {
			if (thought != null && !thought.isBlank()) {
				filled++;
			}
		}
		return filled;
	}

	/**
	 * Picks the attempt that carries more slide-ready thoughts, so a partial reply is never thrown away in
	 * favour of an emptier one. Ties go to the first attempt; {@code null} is returned only when neither
	 * attempt carries a single thought.
	 *
	 * @param first  the first attempt's thoughts, or {@code null}
	 * @param second the retry's thoughts, or {@code null}
	 * @return the fuller of the two, or {@code null} when both are empty
	 */
	TacticThoughts fullerThoughts(TacticThoughts first, TacticThoughts second) {
		int firstCount = countThoughts(first);
		int secondCount = countThoughts(second);
		if (firstCount == 0 && secondCount == 0) {
			return null;
		}
		return secondCount > firstCount ? second : first;
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
		ClaudeResults results = campaignResultsOne(prompt.get(), maxTokens, timeoutSec);
		if (results != null) {
			return results;
		}
		log.warn("[claude:BatchCampaign] campaign results came back empty — retrying once");
		results = campaignResultsOne(prompt.get(), maxTokens, timeoutSec);
		return results == null ? claudeDefaults.emptyResults() : results;
	}

	/**
	 * Runs one campaign-results call: send the prompt, parse the grouped overviews, thoughts, recommendations
	 * and frequency copy, compress whatever overran its budget, and normalize. Returns {@code null} — never a
	 * half-empty DTO — when the call failed or the reply carried none of the three main sections, so the
	 * caller can re-send instead of blanking the report's most visible slides on one bad reply.
	 *
	 * @param prompt     the assembled campaign-results prompt
	 * @param maxTokens  output budget for this call
	 * @param timeoutSec per-request HTTP timeout in seconds
	 * @return the campaign-level copy, or {@code null} when the call produced nothing usable
	 */
	ClaudeResults campaignResultsOne(String prompt, int maxTokens, int timeoutSec) {
		JsonNode parsed = messagesClient.callJsonObject(prompt, maxTokens, timeoutSec, "BatchCampaign", true);
		if (parsed == null) {
			return null;
		}

		Map<Integer, String> rawResultsOverviews = parseNumberedTextMap(parsed.get("results_overviews"));
		List<String> rawThoughts = normalizer.normalizeThoughts(parsed.get("thoughts_on_performance"));

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

		// A reply that parsed as JSON but carries none of the three main sections is a failure, not an answer:
		// shipping it would dash the results overview, the performance thoughts and the recommendations at once.
		// Bail out before the compression call so the retry costs one send, not two.
		if (!campaignReplyUsable(rawResultsOverviews, rawThoughts, rawRecTitles, rawRecTexts)) {
			log.warn("[claude:BatchCampaign] reply parsed but carried no overviews, thoughts or recommendations");
			return null;
		}

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

	/**
	 * Reports whether a parsed campaign-results reply carried anything worth shipping: at least one group
	 * overview, at least one performance thought, or at least one non-blank recommendation. The frequency
	 * strings alone do not qualify — they are optional copy, and a reply that contains only them still leaves
	 * every results slide blank.
	 *
	 * @param resultsOverviews the group-keyed overviews parsed from the reply
	 * @param thoughts         the performance thoughts parsed from the reply
	 * @param recTitles        the four recommendation titles, blank where the reply had none
	 * @param recTexts         the four recommendation texts, blank where the reply had none
	 * @return {@code true} when at least one of the three main sections carries content
	 */
	boolean campaignReplyUsable(
			Map<Integer, String> resultsOverviews, List<String> thoughts, String[] recTitles, String[] recTexts) {
		for (String overview : resultsOverviews.values()) {
			if (normalizer.notBlank(overview)) {
				return true;
			}
		}
		for (String thought : thoughts) {
			if (normalizer.notBlank(thought)) {
				return true;
			}
		}
		for (int i = 0; i < recTitles.length; i++) {
			if (normalizer.notBlank(recTitles[i]) || normalizer.notBlank(recTexts[i])) {
				return true;
			}
		}
		return false;
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
	public String digestBriefIfOversized(String brief) {
		if (brief == null || brief.isBlank() || brief.length() <= BRIEF_DIGEST_LIMIT) {
			// Already within the budget — a digest of a compact brief would cost a call and change nothing.
			return brief;
		}
		String digest = digestBrief(brief);
		if (digest == null || digest.isBlank()) {
			// The digest call failed; the raw text is still better context than none, and the transport already
			// logged why. This line names the consequence: every call this run makes carries the full brief.
			log.warn("[claude:BriefDigest] {}-char brief could not be digested; every call this run carries it "
					+ "in full", brief.length());
			return brief;
		}
		log.info("[claude:BriefDigest] brief context digested for the prompts: {} chars -> {}",
				brief.length(), digest.length());
		return digest;
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
