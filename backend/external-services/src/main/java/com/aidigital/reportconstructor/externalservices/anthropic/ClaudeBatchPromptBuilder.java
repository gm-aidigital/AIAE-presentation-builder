package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusionInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds Anthropic Messages API prompts and campaign context blocks for Claude batches A/B/C and geo.
 */
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class ClaudeBatchPromptBuilder {

	private static final double COMPRESSION_PROMPT_BUFFER_RATIO = 0.8;

	/** Tactics per results-overview group; the deck carries one summary + overview slide per group. */
	private static final int TACTICS_PER_GROUP = 7;

	// Character budgets of the campaign-results fields, mirroring the limits RealClaudeClient enforces on the
	// reply. Quoted to Claude through COMPRESSION_PROMPT_BUFFER_RATIO like every other prompt, so a reply that
	// lands slightly over the number it was given still fits the real budget and skips the compression call.
	private static final int RESULTS_OVERVIEW_LIMIT = 380;
	private static final int THOUGHTS_TOTAL_LIMIT = 700;
	private static final int RECOMMENDATION_TITLE_LIMIT = 28;
	private static final int RECOMMENDATION_TEXT_LIMIT = 125;
	private static final int F_OPPORTUNITY_LIMIT = 180;
	private static final int F_FACT_LIMIT = 140;
	private static final int F_STORYTELLING_LIMIT = 320;

	/**
	 * Substrings marking a completion-led tactic as audio, so Claude sees its completion rate as ACR not VCR.
	 * Mirrors {@code TacticExtractionHelper}'s audio keywords — kept local because the prompt context is
	 * deliberately self-contained and this module does not inject the service-layer tactic helpers.
	 */
	private static final String[] AUDIO_KEYWORDS = {"audio", "podcast"};

	/**
	 * Volume-vs-rate deviation math rule (campaign-analyst principle 9), appended to the Batch C
	 * principle list. Volume metrics deviate in %, rate metrics in percentage points.
	 */
	private static final String METRIC_DEVIATION_RULE =
			"6. METRIC DEVIATION MATH. Express VOLUME-metric gaps (impressions, spend, clicks, completions) as % "
					+ "deviation vs plan; express RATE-metric gaps (CTR, VCR, viewability) as percentage-POINT (pp) "
					+ "deviation (Actual − Plan). Never state a rate gap as a percentage — a CTR moving 0.30% → 0.45% "
					+ "is +0.15pp, not +50%.\n";

	/**
	 * DSP learning-phase caveat for very short flights (campaign-analyst principle 10), appended to the
	 * Batch C principle list. Under a week of live delivery is too little to draw firm conclusions.
	 */
	private static final String LEARNING_PHASE_RULE =
			"7. DATA SUFFICIENCY. If the flight window is under 7 days live, flag DSP learning-phase volatility and "
					+ "avoid firm conclusions or scaling recommendations drawn from that short window.\n";

	/**
	 * Small-sample caveat for the creative-takeaways prompt. A creative with few impressions routinely
	 * posts a CTR or completion rate far above the tactic's average (20% against 1.2%) — that is noise on
	 * low volume, not a winner. Left unsaid, Claude reads the outlier as the campaign's best creative and
	 * recommends shifting budget into it, which is the one recommendation the DSP cannot honour: its model
	 * either fails to spend there at all or delivers well below the current average, and a large shift
	 * resets the learning phase on top of it. The ~20% ceiling is the house rule for the case where such a
	 * creative is genuinely worth backing.
	 */
	private static final String CREATIVE_SMALL_SAMPLE_RULE =
			"- SMALL-SAMPLE OUTLIERS. A creative with few impressions can post a CTR/completion rate many times "
					+ "the tactic's average (e.g. 20% against a 1.2% average). Treat that as statistical noise on "
					+ "low volume, NOT as the best creative: say so plainly rather than crowning it. Judge "
					+ "engagement leadership on creatives with meaningful impression volume.\n"
					+ "- NEVER recommend shifting significant budget onto such a low-volume outlier. The DSP's "
					+ "model would either fail to spend it or deliver well below the current average. Where a "
					+ "creative genuinely deserves more budget, the action is an increase of at most ~20%, so the "
					+ "DSP's learning phase is not reset.\n";

	private final ClaudeResponseNormalizer normalizer;
	private final Fmt fmt;

	public ClaudeBatchPromptBuilder(ClaudeResponseNormalizer normalizer, Fmt fmt) {
		this.normalizer = normalizer;
		this.fmt = fmt;
	}

	/**
	 * Returns the completion-rate label to show Claude for a tactic's completion metric: {@code "ACR"} (audio
	 * completion rate) for audio/podcast tactics, otherwise {@code "VCR"}. So the per-tactic context lines and
	 * the narrative Claude writes name an audio tactic's completions "ACR", matching the deck/sheet labels.
	 *
	 * @param tacticName the tactic display name (may be {@code null})
	 * @return {@code "ACR"} for audio/podcast tactics, otherwise {@code "VCR"}
	 */
	String completionRateLabel(String tacticName) {
		if (tacticName != null) {
			String key = tacticName.toLowerCase(Locale.ROOT);
			for (String kw : AUDIO_KEYWORDS) {
				if (key.contains(kw)) {
					return "ACR";
				}
			}
		}
		return "VCR";
	}

	/**
	 * Builds the Batch A (strategic) prompt, or empty when there is no campaign context to send.
	 *
	 * @param data  parsed campaign plan and per-tactic performance used to assemble the brief/plan/tactic context
	 *                 blocks
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as empty
	 *                when null)
	 * @return the Batch A strategic prompt requesting audience/proposal/insights JSON, or empty when no context block
	 * could be built
	 */
	public Optional<String> buildBatchAPrompt(CampaignData data, String brief) {
		String brf = brief == null ? "" : brief;
		List<String> planLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			planLines.add("Client:       " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			planLines.add("Campaign:     " + data.campaign());
		}
		if (normalizer.notBlank(data.geo())) {
			planLines.add("Geo:          " + data.geo());
		}
		if (normalizer.notBlank(data.goal())) {
			planLines.add("Goal:         " + data.goal());
		}
		if (normalizer.notBlank(data.flightDates())) {
			planLines.add("Flight:       " + data.flightDates());
		}
		if (normalizer.notBlank(data.budget())) {
			planLines.add("Budget:       " + data.budget());
		}
		if (normalizer.notBlank(data.primaryKpis())) {
			planLines.add("KPIs:         " + data.primaryKpis());
		}
		if (normalizer.notBlank(data.tacticsList())) {
			planLines.add("Tactics:      " + data.tacticsList());
		}
		if (normalizer.notBlank(data.audienceAge())) {
			planLines.add("Audience age: " + data.audienceAge());
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
			if (t.spend() > 0) {
				line.append(" Spend $").append(fmt.intGroup(Math.round(t.spend())));
			}
			if (t.imps() > 0) {
				line.append(" | Imps ").append(fmt.intGroup(t.imps()));
			}
			if (t.ctr() != null) {
				line.append(" | CTR ").append(fmt.dec2(t.ctr())).append('%');
			}
			if (t.vcr() != null) {
				line.append(" | ").append(completionRateLabel(t.name())).append(' ').append(fmt.dec2(t.vcr()))
						.append('%');
			}
			tacticLines.add(line.toString());
		}

		List<String> ctx = new ArrayList<>();
		if (!brf.isEmpty()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
		}
		if (!planLines.isEmpty()) {
			ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== TACTIC PERFORMANCE ===\n" + String.join("\n", tacticLines));
		}
		if (normalizer.notBlank(data.audienceTab())) {
			ctx.add("=== AUDIENCE & INVENTORY TAB ===\n" + data.audienceTab());
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		String prompt =
				"You are a senior digital media strategist at an advertising agency writing a client-facing campaign " +
						"report.\n\n"
						+ "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
						+ "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the " +
						"campaign?\" "
						+ "Raw data repeated as prose is not analysis. Transform each data point into a business " +
						"implication.\n"
						+ "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
						+ "Forbidden phrases: \"performance is tracking well\", \"results are in line with " +
						"expectations\", "
						+ "\"we recommend monitoring\", \"this tactic requires further optimization\". "
						+ "If a sentence could appear in any other campaign report unchanged — rewrite it.\n"
						+ "3. EXPLAIN THE WHY. Don't write \"X had a high CTR.\" Write WHY: creative format, placement" +
						" type, "
						+ "audience intent level, message-to-moment alignment, competitive bid landscape, etc.\n"
						+ "4. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo. "
						+ "Name the specific cause. Name the specific action or outcome.\n\n"
						+ "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"audience_age\": string,        // target audience age, e.g. \"25-44 years old\" or " +
						"\"35+\". "
						+ "Exact range if stated; lower bound only if a floor; generation → range (Millennials=25-40, "
						+ "GenZ=18-27, GenX=41-56, Boomers=57-75); null if not specified.\n"
						+ "  \"audience_segments\": string,   // ≤80 chars. WHO is targeted — natural phrase like "
						+ "\"Affluent auto-intenders, HHI $100K+\". No platforms/budgets/KPIs. null if no info.\n"
						+ "  \"proposal_overview\": string,   // Exactly 2 complete sentences. Past tense, no line " +
						"breaks, no bullets. "
						+ "Sentence 1: why the campaign ran — client objective + target audience. "
						+ "Sentence 2: how it ran — tactic mix + geo + flight period. "
						+ "Name the actual tactics, actual audience, actual geo. No character limit — write both " +
						"sentences completely.\n"
						+ "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": " +
						"string}.\n"
						+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD " +
						"LIMIT.\n"
						+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
						+ "                                // Each overview = strategic intention/approach + WHY this " +
						"choice made sense "
						+ "for THIS client/campaign. Unique angles, past tense, Business English. No filler.\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for any field where there is genuinely no data.\n"
						+ "- Do NOT invent facts. Base everything strictly on the provided data.\n"
						+ "- Leave a field null rather than pad it with generic filler — an empty field beats an "
						+ "unsupported claim.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Builds the narrative-only strategic prompt for the slides-from-sheet flow: it requests ONLY the
	 * {@code proposal_overview} and {@code strategic_insights} copy and deliberately omits the audience
	 * fields, because the reviewed sheet already carries {@code audience_age}/{@code audience_segments}
	 * from step 1. This lets step 2 avoid regenerating (and paying for) audience copy it would immediately
	 * discard under the sheet overlay. The context block and both requested fields mirror {@link
	 * #buildBatchAPrompt} exactly — keep them in sync when either prompt changes.
	 *
	 * @param data  parsed campaign plan and per-tactic performance used to assemble the context block
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as
	 *                empty when null)
	 * @return the strategic-narrative prompt requesting proposal/insights JSON, or empty when no context block
	 * could be built
	 */
	public Optional<String> buildBatchStrategicNarrativePrompt(CampaignData data, String brief) {
		String brf = brief == null ? "" : brief;
		List<String> planLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			planLines.add("Client:       " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			planLines.add("Campaign:     " + data.campaign());
		}
		if (normalizer.notBlank(data.geo())) {
			planLines.add("Geo:          " + data.geo());
		}
		if (normalizer.notBlank(data.goal())) {
			planLines.add("Goal:         " + data.goal());
		}
		if (normalizer.notBlank(data.flightDates())) {
			planLines.add("Flight:       " + data.flightDates());
		}
		if (normalizer.notBlank(data.budget())) {
			planLines.add("Budget:       " + data.budget());
		}
		if (normalizer.notBlank(data.primaryKpis())) {
			planLines.add("KPIs:         " + data.primaryKpis());
		}
		if (normalizer.notBlank(data.tacticsList())) {
			planLines.add("Tactics:      " + data.tacticsList());
		}
		if (normalizer.notBlank(data.audienceAge())) {
			planLines.add("Audience age: " + data.audienceAge());
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
			if (t.spend() > 0) {
				line.append(" Spend $").append(fmt.intGroup(Math.round(t.spend())));
			}
			if (t.imps() > 0) {
				line.append(" | Imps ").append(fmt.intGroup(t.imps()));
			}
			if (t.ctr() != null) {
				line.append(" | CTR ").append(fmt.dec2(t.ctr())).append('%');
			}
			if (t.vcr() != null) {
				line.append(" | ").append(completionRateLabel(t.name())).append(' ').append(fmt.dec2(t.vcr()))
						.append('%');
			}
			tacticLines.add(line.toString());
		}

		List<String> ctx = new ArrayList<>();
		if (!brf.isEmpty()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
		}
		if (!planLines.isEmpty()) {
			ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== TACTIC PERFORMANCE ===\n" + String.join("\n", tacticLines));
		}
		if (normalizer.notBlank(data.audienceTab())) {
			ctx.add("=== AUDIENCE & INVENTORY TAB ===\n" + data.audienceTab());
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		String prompt =
				"You are a senior digital media strategist at an advertising agency writing a client-facing campaign " +
						"report.\n\n"
						+ "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
						+ "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the " +
						"campaign?\" "
						+ "Raw data repeated as prose is not analysis. Transform each data point into a business " +
						"implication.\n"
						+ "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
						+ "Forbidden phrases: \"performance is tracking well\", \"results are in line with " +
						"expectations\", "
						+ "\"we recommend monitoring\", \"this tactic requires further optimization\". "
						+ "If a sentence could appear in any other campaign report unchanged — rewrite it.\n"
						+ "3. EXPLAIN THE WHY. Don't write \"X had a high CTR.\" Write WHY: creative format, placement" +
						" type, "
						+ "audience intent level, message-to-moment alignment, competitive bid landscape, etc.\n"
						+ "4. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo. "
						+ "Name the specific cause. Name the specific action or outcome.\n\n"
						+ "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"proposal_overview\": string,   // Exactly 2 complete sentences. Past tense, no line " +
						"breaks, no bullets. "
						+ "Sentence 1: why the campaign ran — client objective + target audience. "
						+ "Sentence 2: how it ran — tactic mix + geo + flight period. "
						+ "Name the actual tactics, actual audience, actual geo. No character limit — write both " +
						"sentences completely.\n"
						+ "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": " +
						"string}.\n"
						+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD " +
						"LIMIT.\n"
						+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
						+ "                                // Each overview = strategic intention/approach + WHY this " +
						"choice made sense "
						+ "for THIS client/campaign. Unique angles, past tense, Business English. No filler.\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for any field where there is genuinely no data.\n"
						+ "- Do NOT invent facts. Base everything strictly on the provided data.\n"
						+ "- Leave a field null rather than pad it with generic filler — an empty field beats an "
						+ "unsupported claim.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Builds the Batch B (tactical) prompt, or empty when there are no tactics.
	 *
	 * @param data  parsed campaign data; its tactic map drives the per-tactic gender/peak-time estimation and the
	 *                 required JSON keys
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as empty
	 *                when null)
	 * @return the Batch B prompt requesting per-tactic gender split and weekday/weekend peak windows, or empty when
	 * no tactics exist
	 */
	public Optional<String> buildBatchBPrompt(CampaignData data, String brief) {
		if (data.tactics() == null || data.tactics().isEmpty()) {
			return Optional.empty();
		}
		String brf = brief == null ? "" : brief;

		List<String> contextLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			contextLines.add("Client:   " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			contextLines.add("Campaign: " + data.campaign());
		}
		if (normalizer.notBlank(data.geo())) {
			contextLines.add("Geo:      " + data.geo());
		}
		if (normalizer.notBlank(data.goal())) {
			contextLines.add("Goal:     " + data.goal());
		}
		if (normalizer.notBlank(data.primaryKpis())) {
			contextLines.add("KPIs:     " + data.primaryKpis());
		}
		if (normalizer.notBlank(data.audienceAge())) {
			contextLines.add("Audience age: " + data.audienceAge());
		}

		StringBuilder contextBlock = new StringBuilder();
		if (!brf.isEmpty()) {
			contextBlock.append("=== CAMPAIGN BRIEF ===\n").append(brf).append("\n\n");
		}
		if (!contextLines.isEmpty()) {
			contextBlock.append("=== CAMPAIGN CONTEXT ===\n")
					.append(String.join("\n", contextLines)).append("\n\n");
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + ": " + t.name());
			if (t.imps() > 0) {
				line.append(" (").append(fmt.intGroup(t.imps())).append(" imps)");
			}
			tacticLines.add(line.toString());
		}

		List<String> keys = new ArrayList<>();
		for (Integer k : data.tactics().keySet()) {
			keys.add("\"" + k + "\"");
		}
		String tacticKeys = "[" + String.join(",", keys) + "]";

		String prompt =
				contextBlock
						+ "You are a digital media analyst. For each tactic below, estimate:\n"
						+ "1. Gender split of the reached audience.\n"
						+ "2. Peak impression time window on WEEKDAYS (format: \"H AM/PM – H AM/PM\", e.g. \"7 PM – 9" +
						" PM\").\n"
						+ "3. Peak impression time window on WEEKENDS (same format).\n\n"
						+ "Tactics:\n" + String.join("\n", tacticLines) + "\n\n"
						+ "Rules:\n"
						+ "1. Return ONLY a valid JSON object — no markdown, no backticks.\n"
						+ "2. Keys are tactic numbers as strings: " + tacticKeys + "\n"
						+ "3. Each value: {\"male\": int, \"female\": int, \"weekdays_peak\": \"H AM/PM – H AM/PM\", " +
						"\"weekends_peak\": \"H AM/PM – H AM/PM\"}\n"
						+ "4. male + female = 100. All integers.\n"
						+ "5. Gender: use campaign context as primary signal. Avoid defaulting to 50/50.\n"
						+ "6. CRITICAL: Never use multiples of 5 for gender. Use uneven integers like 43,57,61,38.\n"
						+ "7. Peak windows: whole hours, 2–5 hour range. Format: \"H PM – H PM\" (no leading zeros)" +
						".\n"
						+ "8. WEEKDAYS default to an evening window (between 5 PM and midnight) — most audiences " +
						"consume media after work/school on weekdays. Only pick a daytime or morning weekday window " +
						"when the tactic's channel or audience clearly behaves otherwise (e.g. a B2B/workplace tactic).\n"
						+ "9. WEEKENDS default to a midday window (between 10 AM and 4 PM). Only pick an evening " +
						"weekend window when the tactic specifics clearly support it.\n\n"
						+ "Example: {\"1\": {\"male\": 38, \"female\": 62, \"weekdays_peak\": \"7 PM – 9 PM\", " +
						"\"weekends_peak\": \"11 AM – 1 PM\"}}";
		return Optional.of(prompt);
	}

	/**
	 * Builds the Generate Sheet prompt: a single call that asks only for the fields the sheet template
	 * consumes — the Batch A {@code audience_age}/{@code audience_segments} narrative and the Batch B
	 * per-tactic gender split and weekday/weekend peak windows. Every field instruction is copied verbatim
	 * from Batches A and B so the sheet copy is generated identically to the slide deck, just without the
	 * unused proposal/strategic/results fields. Unlike {@link #buildBatchAPrompt}, the context sent here is
	 * deliberately narrow — brief, tactic names and the Audience & Inventory tab only. Neither the full
	 * campaign-plan fields (client/geo/goal/flight/budget/KPIs/tactics list) nor per-tactic delivery metrics
	 * (spend/impressions/CTR/VCR) carry signal for gender split or peak-hour windows — the prompt itself
	 * grounds both in "the tactic's channel or audience", which the tactic name already conveys — so both
	 * are left out to keep the reply within its token budget.
	 *
	 * @param data  parsed campaign plan and per-tactic performance used to assemble the context blocks and
	 *                 the per-tactic JSON keys
	 * @param brief campaign brief (already digested by the caller) prepended as the
	 *                {@code === CAMPAIGN BRIEF ===} section (treated as empty when null)
	 * @return the merged sheet prompt requesting audience + per-tactic JSON, or empty when no context block
	 * could be built
	 */
	public Optional<String> buildBatchSheetPrompt(CampaignData data, String brief) {
		String brf = brief == null ? "" : brief;

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			tacticLines.add("  Tactic " + e.getKey() + " — " + e.getValue().name());
		}

		List<String> ctx = new ArrayList<>();
		if (!brf.isEmpty()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== TACTICS ===\n" + String.join("\n", tacticLines));
		}
		if (normalizer.notBlank(data.audienceTab())) {
			ctx.add("=== AUDIENCE & INVENTORY TAB ===\n" + data.audienceTab());
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		List<String> keys = new ArrayList<>();
		for (Integer k : data.tactics().keySet()) {
			keys.add("\"" + k + "\"");
		}
		String tacticKeys = "[" + String.join(",", keys) + "]";

		String prompt =
				"You are a senior digital media strategist at an advertising agency writing a client-facing campaign " +
						"report.\n\n"
						+ "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
						+ "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the " +
						"campaign?\" "
						+ "Raw data repeated as prose is not analysis. Transform each data point into a business " +
						"implication.\n"
						+ "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
						+ "If a sentence could appear in any other campaign report unchanged — rewrite it.\n"
						+ "3. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo.\n\n"
						+ "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"audience_age\": string,        // target audience age, e.g. \"25-44 years old\" or " +
						"\"35+\". "
						+ "Exact range if stated; lower bound only if a floor; generation → range (Millennials=25-40, "
						+ "GenZ=18-27, GenX=41-56, Boomers=57-75); null if not specified.\n"
						+ "  \"audience_segments\": string,   // ≤80 chars. WHO is targeted — natural phrase like "
						+ "\"Affluent auto-intenders, HHI $100K+\". No platforms/budgets/KPIs. null if no info.\n"
						+ "  \"tactics\": {                    // Per-tactic gender split + peak windows. Keys are tactic " +
						"numbers as strings: " + tacticKeys + "\n"
						+ "    \"N\": {\"male\": int, \"female\": int, \"weekdays_peak\": \"H AM/PM – H AM/PM\", " +
						"\"weekends_peak\": \"H AM/PM – H AM/PM\"}\n"
						+ "  //  1. Gender split of the reached audience. male + female = 100. All integers.\n"
						+ "  //  2. Peak impression time window on WEEKDAYS (format: \"H AM/PM – H AM/PM\", e.g. \"7 PM " +
						"– 9 PM\").\n"
						+ "  //  3. Peak impression time window on WEEKENDS (same format).\n"
						+ "  //  Gender: use campaign context as primary signal. Avoid defaulting to 50/50.\n"
						+ "  //  CRITICAL: Never use multiples of 5 for gender. Use uneven integers like 43,57,61,38.\n"
						+ "  //  Peak windows: whole hours, 2–5 hour range. Format: \"H PM – H PM\" (no leading zeros).\n"
						+ "  //  WEEKDAYS default to an evening window (between 5 PM and midnight) — most audiences " +
						"consume media after work/school on weekdays. Only pick a daytime or morning weekday window " +
						"when the tactic's channel or audience clearly behaves otherwise (e.g. a B2B/workplace tactic).\n"
						+ "  //  WEEKENDS default to a midday window (between 10 AM and 4 PM). Only pick an evening " +
						"weekend window when the tactic specifics clearly support it.\n"
						+ "  //  Example: {\"1\": {\"male\": 38, \"female\": 62, \"weekdays_peak\": \"7 PM – 9 PM\", " +
						"\"weekends_peak\": \"11 AM – 1 PM\"}}\n"
						+ "  }\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for any audience field where there is genuinely no data.\n"
						+ "- tactics: include a key for every tactic number listed above.\n"
						+ "- Do NOT invent facts. Base everything strictly on the provided data.\n"
						+ "- Leave a field null rather than pad it with generic filler — an empty field beats an "
						+ "unsupported claim.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Renders one tactic's creative block as prompt context: its name and KPI type, the four summary
	 * stats the slide shows, and the creative table. Blank summary cells are omitted rather than sent as
	 * empty labels, so the user leaving a stat tile empty never reads to Claude as a zero.
	 *
	 * @param input the tactic's creative block
	 * @return the tactic's {@code tactic_<n>} context block
	 */
	String creativeContextBlock(CreativeTakeawayInput input) {
		CreativeTable table = input.table();
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName());
		if (input.kpiType() != null && !input.kpiType().isBlank()) {
			block.append(" (lead KPI: ").append(input.kpiType()).append(')');
		}
		block.append('\n');
		appendCreativeStat(block, "Creatives live", table.creativesLive());
		appendCreativeStat(block, "Best CTR/VCR", table.bestKpi());
		appendCreativeStat(block, "Avg CTR/VCR", table.avgKpi());
		appendCreativeStat(block, "Top creative", table.topCreative());
		block.append("Creative | Impressions | CTR | VCR | Spend\n");
		for (CreativeRow row : table.rows()) {
			block.append(row.name()).append(" | ").append(row.impressions()).append(" | ").append(row.ctr())
					.append(" | ").append(row.vcr()).append(" | ").append(row.spend()).append('\n');
		}
		return block.toString();
	}

	/**
	 * Appends one summary stat line to a creative context block, skipping the stat entirely when the user
	 * left its cell blank.
	 *
	 * @param block the block being built
	 * @param label the stat's prompt label
	 * @param value the stat's value as typed in the sheet, possibly blank
	 */
	void appendCreativeStat(StringBuilder block, String label, String value) {
		if (value != null && !value.isBlank()) {
			block.append(label).append(": ").append(value).append('\n');
		}
	}

	/**
	 * Renders one tactic's geo block as prompt context: its name and KPI type, the three summary stats the
	 * slide shows, and the geo table. Blank summary cells are omitted rather than sent as empty labels, so
	 * the user leaving a stat tile empty never reads to Claude as a zero.
	 *
	 * @param input the tactic's geo block
	 * @return the tactic's {@code tactic_<n>} context block
	 */
	String geoContextBlock(GeoInsightInput input) {
		GeoTable table = input.table();
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName());
		if (input.kpiType() != null && !input.kpiType().isBlank()) {
			block.append(" (lead KPI: ").append(input.kpiType()).append(')');
		}
		block.append('\n');
		appendCreativeStat(block, "Markets activated", table.marketsActivated());
		appendCreativeStat(block, "Top geo", table.topGeo());
		appendCreativeStat(block, "Most efficient geo KPI", table.topKpi());
		block.append("Geo | Impressions | ").append(kpiColumnLabel(input.kpiType())).append('\n');
		for (GeoRow row : table.rows()) {
			block.append(row.name()).append(" | ").append(row.impressions())
					.append(" | ").append(row.kpi()).append('\n');
		}
		return block.toString();
	}

	/**
	 * Names the geo table's KPI column for the prompt, using the tactic's own KPI type when known so the
	 * column reads as {@code "CTR"}/{@code "VCR"}/{@code "ACR"} rather than a generic label.
	 *
	 * @param kpiType the tactic's KPI type as the deck spells it, possibly blank
	 * @return the KPI column header for the prompt block
	 */
	String kpiColumnLabel(String kpiType) {
		return kpiType == null || kpiType.isBlank() ? "KPI" : kpiType.trim();
	}

	/**
	 * Renders one tactic's audience block as prompt context: its name, the two stat tiles the slide shows,
	 * the age-distribution table and the top-audience-segments table. Blank stat tiles are omitted rather
	 * than sent as empty labels, so the user leaving a tile empty never reads to Claude as a zero.
	 *
	 * @param input the tactic's audience block
	 * @return the tactic's {@code tactic_<n>} context block
	 */
	String audienceContextBlock(AudienceInsightInput input) {
		AudienceTable table = input.table();
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName()).append('\n');
		appendCreativeStat(block, "Dominant age group", table.ageDistribution());
		appendCreativeStat(block, "Gender demographics", table.genderDemographics());
		if (!table.ageRows().isEmpty()) {
			block.append("Age | Impressions\n");
			for (AudienceAgeRow row : table.ageRows()) {
				block.append(row.ageGroup()).append(" | ").append(row.impressions()).append('\n');
			}
		}
		if (!table.segmentRows().isEmpty()) {
			block.append("Segment | Affinity index (100 = campaign average)\n");
			for (AudienceSegmentRow row : table.segmentRows()) {
				block.append(row.segment()).append(" | ").append(row.affinityIndex()).append('\n');
			}
		}
		return block.toString();
	}

	/**
	 * Renders one tactic's device block as prompt context: its name, the five stat tiles the slide shows
	 * and the per-device performance table. Blank stat tiles are omitted rather than sent as empty labels,
	 * so the user leaving a tile empty never reads to Claude as a zero.
	 *
	 * @param input the tactic's device block
	 * @return the tactic's {@code tactic_<n>} context block
	 */
	String deviceContextBlock(DeviceInsightInput input) {
		DeviceTable table = input.table();
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName()).append('\n');
		appendCreativeStat(block, "Highest CTR", table.highestCtr());
		appendCreativeStat(block, "Best completion (VCR)", table.bestCompletion());
		appendCreativeStat(block, "Devices tracked", table.devicesTracked());
		appendCreativeStat(block, "Top device", table.topDevice());
		appendCreativeStat(block, "Top device % of impressions", table.topDeviceImpressionsPct());
		if (!table.rows().isEmpty()) {
			block.append("Device | Impressions | CTR | VCR | Spend\n");
			for (DeviceRow row : table.rows()) {
				block.append(row.device()).append(" | ").append(row.impressions())
						.append(" | ").append(row.ctr()).append(" | ").append(row.vcr())
						.append(" | ").append(row.spend()).append('\n');
			}
		}
		return block.toString();
	}

	/**
	 * Builds the Step-2 combined per-tactic conclusions prompt for one chunk of tactics: for each tactic it
	 * asks for the {@code {{tactic n overview}}} narrative plus the copy for every breakdown section that tactic
	 * enabled, in a single reply. This folds the old Batch C tactic-overview call and the five separate
	 * per-section batches into one call per tactic (or per small chunk), reusing the exact rule text and slide
	 * character budgets of those batches so the copy quality is unchanged.
	 *
	 * <p>The instruction block and the shared campaign context sit before the {@link
	 * AnthropicMessagesClient#CACHE_BREAKPOINT}, so they are cached once and re-read cheaply on every following
	 * per-tactic call; only the tactic's own data follows the marker. Every section's rules are always in the
	 * cached prefix (keeping the prefix byte-stable for the cache) and the model fills a section only when that
	 * section appears in the tactic's data.
	 *
	 * @param data                  parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param chunk                 the tactics to cover in this call, each with its enabled-section inputs
	 * @param brief                 free-text campaign brief the conclusions must stay faithful to
	 * @param publisherLimit        slide budget for each publisher bullet
	 * @param creativeLimit         slide budget for creative takeaways 1-3
	 * @param creativeRecoLimit     slide budget for creative takeaway 4 (the optimisation)
	 * @param geoLimit              slide budget for each geo string
	 * @param audienceTakeawayLimit slide budget for the audience key takeaway
	 * @param audienceShortLimit    slide budget for the other three audience strings
	 * @param deviceTakeawayLimit   slide budget for the device key takeaway
	 * @param deviceShortLimit      slide budget for the other three device strings
	 * @return the combined prompt, or empty when the chunk carries no tactic and no data
	 */
	public Optional<String> buildTacticConclusionsPrompt(
			CampaignData data, List<TacticConclusionInput> chunk, String brief,
			int publisherLimit, int creativeLimit, int creativeRecoLimit, int geoLimit,
			int audienceTakeawayLimit, int audienceShortLimit, int deviceTakeawayLimit, int deviceShortLimit) {
		if (chunk == null || chunk.isEmpty() || data == null || data.tactics() == null) {
			return Optional.empty();
		}
		List<String> dataBlocks = new ArrayList<>();
		for (TacticConclusionInput input : chunk) {
			Tactic tactic = data.tactics().get(input.tacticNum());
			if (tactic == null) {
				continue;
			}
			dataBlocks.add(tacticConclusionDataBlock(input, tactic));
		}
		if (dataBlocks.isEmpty()) {
			return Optional.empty();
		}

		int publisherPrompt = Math.max(1, (int) (publisherLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int creativePrompt = Math.max(1, (int) (creativeLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int creativeRecoPrompt = Math.max(1, (int) (creativeRecoLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int geoPrompt = Math.max(1, (int) (geoLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int audTakeawayPrompt = Math.max(1, (int) (audienceTakeawayLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int audShortPrompt = Math.max(1, (int) (audienceShortLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int devTakeawayPrompt = Math.max(1, (int) (deviceTakeawayLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int devShortPrompt = Math.max(1, (int) (deviceShortLimit * COMPRESSION_PROMPT_BUFFER_RATIO));

		String prompt =
				"You are a senior digital media analyst writing per-tactic conclusions for a post-campaign report.\n\n"
						+ "For EACH tactic below, return a JSON object with an \"overview\" string and, for EACH "
						+ "breakdown section present in that tactic's data, that section's fields. Produce a section's "
						+ "fields ONLY when that section appears in the tactic's data — never invent a section that is "
						+ "not there.\n\n"
						+ "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
						+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION: state what happened, why (a specific cause), "
						+ "and what it means — as one flowing statement, never labelled sections.\n"
						+ "2. INTERPRET, NEVER ENUMERATE. The reader can see the numbers; explain what they mean.\n"
						+ "3. 'SO WHAT' IS MANDATORY: every metric cited is followed by its business consequence.\n"
						+ "4. NO GENERIC LANGUAGE: every sentence is specific to THIS campaign's numbers and audience.\n"
						+ "5. NAME THE CAUSE for strong or soft performance.\n"
						+ METRIC_DEVIATION_RULE
						+ LEARNING_PHASE_RULE
						+ "\n"
						+ "=== overview (ALWAYS produce) ===\n"
						+ "MAX 190 CHARACTERS, ending on a complete word and sentence. Structure: [what the tactic "
						+ "delivered vs plan] + [WHY it performed as it did] + [business so-what]. Past tense, business "
						+ "English, max 2 sentences, no bullets. Focus metrics by tactic type: Display→Imps+CTR; "
						+ "Video/Pre-roll→Imps+CTR+VCR; CTV/OTT→Imps+VCR; Audio→Completions.\n\n"
						+ "=== top_publishers (array of exactly 4 strings, when present) ===\n"
						+ "KEY OBSERVATIONS for the 'Top Publishers' slide, written on behalf of the team that ran the "
						+ "campaign (confident, complimentary of our own delivery). Each ONE complete sentence, at most "
						+ publisherPrompt + " characters. The table lists only the TOP ~15 publishers over a long tail "
						+ "of thousands more. We run an AUDIENCE-FIRST approach: we chase the audience, not sites. "
						+ "Ground each in the numbers (name real publishers, cite real shares/impressions, head vs long "
						+ "tail). At most ~20% may go beyond the table and never as measured fact. Any optimisation is "
						+ "phrased as something WE ALREADY DID (e.g. 'we shifted weight toward stronger publishers'); "
						+ "never say we blacklisted or paused a TACTIC (say we REDUCED ITS WEIGHT). One of the 4 notes "
						+ "that we blacklisted a large number of PUBLISHERS (hundreds to a few thousand, kept "
						+ "qualitative) to hold delivery on premium, brand-safe inventory. Vary the 4 angles: "
						+ "volume/reach + long tail, audience-fit, premium/brand-suitability incl. blacklisting, and "
						+ "steering weight to the strongest publishers.\n\n"
						+ "=== creative (array of exactly 4 strings, when present) ===\n"
						+ "KEY TAKEAWAYS for the 'Creative analysis' slide. Takeaways 1-3 read the creative mix: ONE "
						+ "sentence each, at most " + creativePrompt + " characters, grounded in the numbers (name real "
						+ "creatives, cite impressions shares, CTR/VCR, spend). Vary angles: delivery/completion "
						+ "anchor, engagement leader, and a read on creative format/size. "
						+ CREATIVE_SMALL_SAMPLE_RULE
						+ "Takeaway 4 states an optimisation ALREADY MADE on creative during the flight and its "
						+ "result, at most " + creativeRecoPrompt + " characters. Use the tactic's own KPI type for its "
						+ "lead metric.\n\n"
						+ "=== geo (array of exactly 5 strings, when present) ===\n"
						+ "'WHAT THE MAP TELLS US' for the 'Geo analysis' slide. Strings 1-4 are insights: ONE sentence "
						+ "each, at most " + geoPrompt + " characters, grounded in real markets/geos and the lead KPI; "
						+ "vary angles: concentration across top geos, efficient (over-indexing) markets, reach with "
						+ "softer engagement, and audience/market fit. Treat high-KPI low-volume markets as noise. "
						+ "String 5 is DIFFERENT — a FORWARD-LOOKING recommendation (where to open budget, which "
						+ "markets to scale), at most " + geoPrompt + " characters, still grounded in the table.\n\n"
						+ "=== audience (array of exactly 4 strings, when present) ===\n"
						+ "For the 'Audience analysis' slide, in order: 1) KEY TAKEAWAY (who this tactic reached), at "
						+ "most " + audTakeawayPrompt + " characters; 2) WHAT WORKED, at most " + audShortPrompt
						+ " characters; 3) WATCH-OUT, at most " + audShortPrompt + " characters; 4) RECOMMENDED ACTION "
						+ "— FORWARD-LOOKING, which age groups and segments to lean into next, at most " + audShortPrompt
						+ " characters. Ground in real age groups and top segments with affinity indexes (100 = "
						+ "campaign average); treat high-affinity low-volume segments as noise.\n\n"
						+ "=== device (array of exactly 4 strings, when present) ===\n"
						+ "For the 'Device breakdown' slide, in order: 1) KEY TAKEAWAY (performance across devices), at "
						+ "most " + devTakeawayPrompt + " characters; 2) WHAT WORKED, at most " + devShortPrompt
						+ " characters; 3) WATCH-OUT, at most " + devShortPrompt + " characters; 4) RECOMMENDED ACTION "
						+ "— FORWARD-LOOKING, which devices to lean into next, at most " + devShortPrompt
						+ " characters. Ground in real devices (impressions, CTR, VCR, spend). CTR does not apply to "
						+ "Connected TV — never treat a missing CTV CTR as a zero or weakness. Treat high-rate "
						+ "low-volume devices as noise.\n\n"
						+ "Return ONLY a JSON object keyed by tactic, each key an object with the present sections, e.g.:\n"
						+ "{\"tactic_1\": {\"overview\": \"...\", \"top_publishers\": [\"...\",\"...\",\"...\",\"...\"], "
						+ "\"geo\": [\"...\",\"...\",\"...\",\"...\",\"...\"]}}\n"
						+ "Rules: return ONLY the JSON (no markdown/backticks); analyst tone, no bullet characters; "
						+ "do NOT invent metrics; every string ends on a complete sentence within its limit; English.\n\n"
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC DATA ===\n"
						+ String.join("\n\n", dataBlocks);
		return Optional.of(prompt);
	}

	/**
	 * Builds the per-tactic audience-section pilot prompt: a small, self-contained call that asks for ONLY the
	 * four "Audience analysis" slide strings as a bare JSON array of exactly four items, in slide order. Unlike
	 * the combined conclusions prompt it carries a single tactic's audience block and demands a keyless array,
	 * so there is no object key the model can drift on and the reply is validated purely by position and length.
	 * The instruction/context prefix is stable per run and marked cacheable, so each tactic re-reads it cheaply.
	 *
	 * @param input         the tactic's audience input (name + table); empty output when its table is blank
	 * @param data          parsed campaign data supplying the shared campaign context block
	 * @param brief         free-text campaign brief the copy must stay faithful to
	 * @param takeawayLimit character budget of the first string (the key takeaway)
	 * @param shortLimit    character budget of the remaining three strings
	 * @return the audience-section prompt, or empty when the tactic carries no audience table to reason over
	 */
	public Optional<String> buildAudienceSectionPrompt(
			AudienceInsightInput input, CampaignData data, String brief, int takeawayLimit, int shortLimit) {
		if (input == null || input.table() == null || input.table().isEmpty() || data == null) {
			return Optional.empty();
		}
		int takeawayPrompt = Math.max(1, (int) (takeawayLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int shortPrompt = Math.max(1, (int) (shortLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt =
				"You are a senior digital media analyst writing the 'Audience analysis' slide for ONE tactic in a "
						+ "post-campaign report.\n\n"
						+ sectionPrinciples()
						+ "Ground in real age groups and top segments with affinity indexes (100 = campaign average); "
						+ "treat high-affinity low-volume segments as noise.\n\n"
						+ "Return the four strings in THIS order:\n"
						+ "1) KEY TAKEAWAY (who this tactic reached), at most " + takeawayPrompt + " characters;\n"
						+ "2) WHAT WORKED, at most " + shortPrompt + " characters;\n"
						+ "3) WATCH-OUT, at most " + shortPrompt + " characters;\n"
						+ "4) RECOMMENDED ACTION — FORWARD-LOOKING, which age groups and segments to lean into next, at "
						+ "most " + shortPrompt + " characters.\n\n"
						+ sectionArrayRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[AUDIENCE ANALYSIS]\n" + audienceContextBlock(input);
		return Optional.of(prompt);
	}

	/**
	 * Builds the per-tactic publisher-section prompt: a small, self-contained call that asks for ONLY the four
	 * "Top Publishers" slide observations as a bare JSON array of exactly four items. Keyless and single-tactic
	 * like {@link #buildAudienceSectionPrompt}, so the reply is validated purely by position and length.
	 *
	 * @param input  the tactic's publisher input (name + rows); empty output when it has no rows
	 * @param data   parsed campaign data supplying the shared campaign context block
	 * @param brief  free-text campaign brief the copy must stay faithful to
	 * @param limit  character budget of each of the four observations
	 * @return the publisher-section prompt, or empty when the tactic carries no publisher rows
	 */
	public Optional<String> buildPublisherSectionPrompt(
			PublisherObservationInput input, CampaignData data, String brief, int limit) {
		if (input == null || input.rows() == null || input.rows().isEmpty() || data == null) {
			return Optional.empty();
		}
		int prompt = Math.max(1, (int) (limit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String text =
				"You are a senior digital media analyst writing the 'Top Publishers' slide for ONE tactic in a "
						+ "post-campaign report, on behalf of the team that ran the campaign (confident, complimentary "
						+ "of our own delivery).\n\n"
						+ sectionPrinciples()
						+ "Return FOUR key observations, each ONE complete sentence, at most " + prompt + " characters. "
						+ "The table lists only the TOP ~15 publishers over a long tail of thousands more. We run an "
						+ "AUDIENCE-FIRST approach: we chase the audience, not sites. Ground each in the numbers (name "
						+ "real publishers, cite real shares/impressions, head vs long tail). At most ~20% may go beyond "
						+ "the table and never as measured fact. Any optimisation is phrased as something WE ALREADY DID "
						+ "(e.g. 'we shifted weight toward stronger publishers'); never say we blacklisted or paused a "
						+ "TACTIC (say we REDUCED ITS WEIGHT). One of the 4 notes that we blacklisted a large number of "
						+ "PUBLISHERS (hundreds to a few thousand, kept qualitative) to hold delivery on premium, "
						+ "brand-safe inventory. Vary the 4 angles: volume/reach + long tail, audience-fit, "
						+ "premium/brand-suitability incl. blacklisting, and steering weight to the strongest "
						+ "publishers.\n\n"
						+ sectionArrayRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[TOP PUBLISHERS]\n" + publisherContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic creative-section prompt: a bare JSON array of exactly four "Creative analysis"
	 * strings — three reads of the creative mix plus one optimisation already made. Keyless and single-tactic.
	 *
	 * @param input     the tactic's creative input (name + KPI type + table); empty output when the table is blank
	 * @param data      parsed campaign data supplying the shared campaign context block
	 * @param brief     free-text campaign brief the copy must stay faithful to
	 * @param limit     character budget of the first three takeaways
	 * @param recoLimit character budget of the fourth (optimisation) string
	 * @return the creative-section prompt, or empty when the tactic carries no creative table
	 */
	public Optional<String> buildCreativeSectionPrompt(
			CreativeTakeawayInput input, CampaignData data, String brief, int limit, int recoLimit) {
		if (input == null || input.table() == null || input.table().isEmpty() || data == null) {
			return Optional.empty();
		}
		int prompt = Math.max(1, (int) (limit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int recoPrompt = Math.max(1, (int) (recoLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String text =
				"You are a senior digital media analyst writing the 'Creative analysis' slide for ONE tactic in a "
						+ "post-campaign report.\n\n"
						+ sectionPrinciples()
						+ "Return the four strings in THIS order. Takeaways 1-3 read the creative mix: ONE sentence "
						+ "each, at most " + prompt + " characters, grounded in the numbers (name real creatives, cite "
						+ "impressions shares, CTR/VCR, spend). Vary angles: delivery/completion anchor, engagement "
						+ "leader, and a read on creative format/size. " + CREATIVE_SMALL_SAMPLE_RULE
						+ "String 4 states an optimisation ALREADY MADE on creative during the flight and its result, "
						+ "at most " + recoPrompt + " characters. Use the tactic's own KPI type for its lead metric.\n\n"
						+ sectionArrayRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName()
						+ kpiSuffix(input.kpiType()) + " ===\n"
						+ "[CREATIVE ANALYSIS]\n" + creativeContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic geo-section prompt: a bare JSON array of exactly five "Geo analysis" strings — four
	 * insights plus one forward-looking recommendation. Keyless and single-tactic.
	 *
	 * @param input the tactic's geo input (name + KPI type + table); empty output when the table is blank
	 * @param data  parsed campaign data supplying the shared campaign context block
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @param limit character budget of each of the five strings
	 * @return the geo-section prompt, or empty when the tactic carries no geo table
	 */
	public Optional<String> buildGeoSectionPrompt(
			GeoInsightInput input, CampaignData data, String brief, int limit) {
		if (input == null || input.table() == null || input.table().isEmpty() || data == null) {
			return Optional.empty();
		}
		int prompt = Math.max(1, (int) (limit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String text =
				"You are a senior digital media analyst writing 'WHAT THE MAP TELLS US' for the 'Geo analysis' "
						+ "slide of ONE tactic in a post-campaign report.\n\n"
						+ sectionPrinciples()
						+ "Return the five strings in THIS order. Strings 1-4 are insights: ONE sentence each, at most "
						+ prompt + " characters, grounded in real markets/geos and the lead KPI; vary angles: "
						+ "concentration across top geos, efficient (over-indexing) markets, reach with softer "
						+ "engagement, and audience/market fit. Treat high-KPI low-volume markets as noise. String 5 is "
						+ "DIFFERENT — a FORWARD-LOOKING recommendation (where to open budget, which markets to scale), "
						+ "at most " + prompt + " characters, still grounded in the table.\n\n"
						+ sectionArrayRules(5)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName()
						+ kpiSuffix(input.kpiType()) + " ===\n"
						+ "[GEO ANALYSIS]\n" + geoContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic device-section prompt: a bare JSON array of exactly four "Device breakdown" strings,
	 * in the same takeaway/what-worked/watch-out/recommendation order as audience. Keyless and single-tactic.
	 *
	 * @param input         the tactic's device input (name + table); empty output when the table is blank
	 * @param data          parsed campaign data supplying the shared campaign context block
	 * @param brief         free-text campaign brief the copy must stay faithful to
	 * @param takeawayLimit character budget of the first string (the key takeaway)
	 * @param shortLimit    character budget of the remaining three strings
	 * @return the device-section prompt, or empty when the tactic carries no device table
	 */
	public Optional<String> buildDeviceSectionPrompt(
			DeviceInsightInput input, CampaignData data, String brief, int takeawayLimit, int shortLimit) {
		if (input == null || input.table() == null || input.table().isEmpty() || data == null) {
			return Optional.empty();
		}
		int takeawayPrompt = Math.max(1, (int) (takeawayLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		int shortPrompt = Math.max(1, (int) (shortLimit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String text =
				"You are a senior digital media analyst writing the 'Device breakdown' slide for ONE tactic in a "
						+ "post-campaign report.\n\n"
						+ sectionPrinciples()
						+ "Return the four strings in THIS order:\n"
						+ "1) KEY TAKEAWAY (performance across devices), at most " + takeawayPrompt + " characters;\n"
						+ "2) WHAT WORKED, at most " + shortPrompt + " characters;\n"
						+ "3) WATCH-OUT, at most " + shortPrompt + " characters;\n"
						+ "4) RECOMMENDED ACTION — FORWARD-LOOKING, which devices to lean into next, at most "
						+ shortPrompt + " characters.\n"
						+ "Ground in real devices (impressions, CTR, VCR, spend). CTR does not apply to Connected TV — "
						+ "never treat a missing CTV CTR as a zero or weakness. Treat high-rate low-volume devices as "
						+ "noise.\n\n"
						+ sectionArrayRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[DEVICE BREAKDOWN]\n" + deviceContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * The shared analytical-principles block prepended to every per-section prompt, kept identical across
	 * sections so the copy reads consistently and each section re-uses the same cached instruction guidance.
	 *
	 * @return the analytical-principles block, newline-terminated
	 */
	String sectionPrinciples() {
		return "ANALYTICAL PRINCIPLES — apply to every string:\n"
				+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION as one flowing statement, never labelled.\n"
				+ "2. INTERPRET, NEVER ENUMERATE. Explain what the numbers mean.\n"
				+ "3. 'SO WHAT' IS MANDATORY: every metric cited is followed by its business consequence.\n"
				+ "4. NO GENERIC LANGUAGE: every sentence is specific to THIS tactic's data.\n\n";
	}

	/**
	 * The shared closing rules block for a per-section prompt: demand ONLY a bare JSON array of exactly the
	 * given count of non-empty strings and nothing else, so the reply carries no object key the model can drift
	 * on and can be validated by position and length alone.
	 *
	 * @param count the exact number of strings the array must carry
	 * @return the closing rules block, newline-terminated
	 */
	String sectionArrayRules(int count) {
		return "OUTPUT FORMAT — follow exactly:\n"
				+ "- Your reply MUST begin with '[' and be ONLY a JSON array of EXACTLY " + count + " non-empty "
				+ "strings, in the order above (no markdown, no backticks, no object, no keys).\n"
				+ "- Write NO preamble, explanation, reasoning, or commentary before or after the array — the array "
				+ "is the entire reply.\n"
				+ "- Do NOT refuse and do NOT flag data problems. If the data looks unusual, mislabelled, incomplete "
				+ "or inconsistent, still write the best analyst copy you can from whatever is given; never replace a "
				+ "string with a complaint about the data.\n"
				+ "- Analyst tone, no bullet characters; do NOT invent metrics; every string ends on a complete "
				+ "sentence within its limit; English.\n\n";
	}

	/**
	 * Renders a tactic's KPI type as a short parenthetical suffix for the tactic header (e.g. {@code " (KPI:
	 * CTR)"}), so a section whose copy leans on the lead metric always sees it; blank when no KPI type is known.
	 *
	 * @param kpiType the tactic's KPI type as the deck spells it, or blank/null when unknown
	 * @return the parenthetical KPI suffix, or an empty string when no KPI type is present
	 */
	String kpiSuffix(String kpiType) {
		return normalizer.notBlank(kpiType) ? " (KPI: " + kpiType + ")" : "";
	}

	/**
	 * Renders one tactic's combined data block for {@link #buildTacticConclusionsPrompt}: the tactic header,
	 * its performance metrics for the overview, then each enabled section's context block reusing the same
	 * renderers the standalone per-section prompts use. A section whose input is null or empty is omitted.
	 *
	 * @param input  the tactic's enabled-section inputs
	 * @param tactic the tactic's parsed performance metrics for the overview line
	 * @return the tactic's {@code tactic_<n>} combined data block
	 */
	String tacticConclusionDataBlock(TacticConclusionInput input, Tactic tactic) {
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(tactic.name()).append('\n');
		block.append("PERFORMANCE (for overview): ").append(tacticMetricLine(tactic)).append('\n');
		if (input.publisher() != null && input.publisher().rows() != null && !input.publisher().rows().isEmpty()) {
			block.append("[TOP PUBLISHERS]\n").append(publisherContextBlock(input.publisher())).append('\n');
		}
		if (input.creative() != null && input.creative().table() != null && !input.creative().table().isEmpty()) {
			block.append("[CREATIVE ANALYSIS]\n").append(creativeContextBlock(input.creative())).append('\n');
		}
		if (input.geo() != null && input.geo().table() != null && !input.geo().table().isEmpty()) {
			block.append("[GEO ANALYSIS]\n").append(geoContextBlock(input.geo())).append('\n');
		}
		if (input.audience() != null && input.audience().table() != null && !input.audience().table().isEmpty()) {
			block.append("[AUDIENCE ANALYSIS]\n").append(audienceContextBlock(input.audience())).append('\n');
		}
		if (input.device() != null && input.device().table() != null && !input.device().table().isEmpty()) {
			block.append("[DEVICE BREAKDOWN]\n").append(deviceContextBlock(input.device())).append('\n');
		}
		return block.toString();
	}

	/**
	 * Renders one tactic's publisher table as prompt context, mirroring the standalone publisher prompt's
	 * inline block so the combined call sees the same rows.
	 *
	 * @param input the tactic's publisher input
	 * @return the tactic's publisher context block
	 */
	String publisherContextBlock(PublisherObservationInput input) {
		StringBuilder block = new StringBuilder();
		block.append("Publisher | Impressions | Share of voice\n");
		for (PublisherRow row : input.rows()) {
			block.append(row.name()).append(" | ").append(row.impressions())
					.append(" | ").append(row.shareOfVoice()).append('\n');
		}
		return block.toString();
	}

	/**
	 * Builds one tactic's actual-vs-plan metric line for the overview, in the same format the Batch C context
	 * uses. Blank/zero metrics are omitted so an unfilled figure never reads to Claude as a real zero.
	 *
	 * @param tactic the tactic's parsed performance metrics
	 * @return the pipe-separated metric line (may be empty when the tactic carries no figures)
	 */
	String tacticMetricLine(Tactic tactic) {
		StringBuilder line = new StringBuilder();
		if (tactic.spend() > 0) {
			line.append("Actual Spend $").append(fmt.intGroup(Math.round(tactic.spend())));
		}
		if (tactic.imps() > 0) {
			line.append(" | Actual Imps ").append(fmt.intGroup(tactic.imps()));
		}
		if (tactic.ctr() != null) {
			line.append(" | Actual CTR ").append(fmt.dec2(tactic.ctr())).append('%');
		}
		if (tactic.vcr() != null) {
			line.append(" | Actual ").append(completionRateLabel(tactic.name())).append(' ')
					.append(fmt.dec2(tactic.vcr())).append('%');
		}
		if (tactic.planSpend() != null) {
			line.append(" | Plan Spend $").append(fmt.intGroup(Math.round(tactic.planSpend())));
		}
		if (tactic.planImps() != null) {
			line.append(" | Plan Imps ").append(fmt.intGroup(tactic.planImps()));
		}
		if (tactic.planCtr() != null) {
			line.append(" | Plan CTR ").append(fmt.dec2(tactic.planCtr())).append('%');
		}
		if (tactic.planVcr() != null) {
			line.append(" | Plan ").append(completionRateLabel(tactic.name())).append(' ')
					.append(fmt.dec2(tactic.planVcr())).append('%');
		}
		return line.toString();
	}

	/**
	 * Builds the shared campaign context (brief, plan and overall totals) for the combined conclusions prompt,
	 * so each per-tactic overview is grounded in the same campaign-level picture the Batch C prompt gave it.
	 *
	 * @param data  parsed campaign data
	 * @param brief free-text campaign brief
	 * @return the {@code === CAMPAIGN BRIEF/PLAN/OVERALL RESULTS ===} context block
	 */
	String campaignContextForConclusions(CampaignData data, String brief) {
		List<String> ctx = new ArrayList<>();
		if (brief != null && !brief.isBlank()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brief);
		}
		List<String> planLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			planLines.add("Client:   " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			planLines.add("Campaign: " + data.campaign());
		}
		if (normalizer.notBlank(data.flightDates())) {
			planLines.add("Flight:   " + data.flightDates());
		}
		if (normalizer.notBlank(data.goal())) {
			planLines.add("Goal:     " + data.goal());
		}
		if (normalizer.notBlank(data.budget())) {
			planLines.add("Budget:   " + data.budget());
		}
		if (!planLines.isEmpty()) {
			ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
		}
		Totals tot = data.totals();
		List<String> totalLines = new ArrayList<>();
		if (tot != null) {
			if (tot.spend() > 0) {
				totalLines.add("Total Spend: $" + fmt.intGroup(Math.round(tot.spend())));
			}
			if (tot.imps() > 0) {
				totalLines.add("Total Imps:  " + fmt.intGroup(tot.imps()));
			}
			if (tot.ctr() != null) {
				totalLines.add("Total CTR:   " + fmt.dec2(tot.ctr()) + "%");
			}
			if (tot.vcr() != null) {
				totalLines.add("Total VCR:   " + fmt.dec2(tot.vcr()) + "%");
			}
		}
		if (!totalLines.isEmpty()) {
			ctx.add("=== OVERALL RESULTS ===\n" + String.join("\n", totalLines));
		}
		return String.join("\n\n", ctx);
	}

	/**
	 * Builds the Step-3 per-tactic "thoughts on tactic performance" prompt for one tactic, or empty when the
	 * tactic carries no overview and no breakdown conclusions to reason over. The tactic's own Step-2
	 * conclusions are the only input — no sheet, no raw grids — so the four thoughts synthesise across the
	 * overview and whichever breakdown sections that tactic ran.
	 *
	 * <p>The instruction and brief sit before the {@link AnthropicMessagesClient#CACHE_BREAKPOINT} so they are
	 * cached once and re-read cheaply on every following per-tactic call; only the tactic's conclusions follow
	 * the marker.
	 *
	 * @param input the tactic's assembled conclusions (overview + enabled-section bullets)
	 * @param brief free-text campaign brief the thoughts must stay faithful to
	 * @param limit the per-thought character budget
	 * @return the prompt requesting {@code {"thoughts": [4 strings]}}, or empty when there is nothing to reason over
	 */
	public Optional<String> buildTacticThoughtsPrompt(TacticThoughtsInput input, String brief, int limit) {
		if (input == null) {
			return Optional.empty();
		}
		String dataBlock = tacticThoughtsDataBlock(input);
		if (dataBlock.isBlank()) {
			return Optional.empty();
		}
		int promptLimit = Math.max(1, (int) (limit * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior digital media analyst writing the four 'Thoughts on tactic performance' "
				+ "bullets for ONE tactic's slide in an end-of-campaign report. You are writing on behalf of the team "
				+ "that ran this campaign, so the tone is confident and complimentary of our own delivery.\n\n"
				+ "Write EXACTLY 4 short analytical thoughts about THIS tactic's performance, each 1-2 sentences, "
				+ "past tense, client-friendly, at most " + promptLimit + " characters.\n"
				+ "Synthesise across the tactic's overview AND its breakdown conclusions below — do not just restate "
				+ "one of them. Vary the 4 angles: (1) the tactic's headline result and WHY; (2) what worked best "
				+ "across its breakdowns (publishers / creative / geo / audience / device); (3) a watch-out or nuance; "
				+ "(4) the forward-looking opportunity for this tactic.\n"
				+ "Ground every thought in the conclusions given; never invent a metric. Analyst tone, no bullet "
				+ "characters, no markdown.\n\n"
				+ "Return ONLY a JSON object: {\"thoughts\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC CONCLUSIONS ===\n" + dataBlock;
		return Optional.of(prompt);
	}

	/**
	 * Renders one tactic's Step-2 conclusions as prompt context for {@link #buildTacticThoughtsPrompt}: the
	 * tactic header, its overview, then each breakdown section's bullets that the tactic actually produced.
	 * Null/empty sections are omitted.
	 *
	 * @param input the tactic's assembled conclusions
	 * @return the tactic's conclusions block (blank when it carries nothing to reason over)
	 */
	String tacticThoughtsDataBlock(TacticThoughtsInput input) {
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum());
		if (input.tacticName() != null && !input.tacticName().isBlank()) {
			block.append(" — ").append(input.tacticName());
		}
		block.append('\n');
		int contentStart = block.length();
		if (input.overview() != null && !input.overview().isBlank()) {
			block.append("OVERVIEW: ").append(input.overview().trim()).append('\n');
		}
		appendConclusionSection(block, "TOP PUBLISHERS", input.publisherBullets());
		appendConclusionSection(block, "CREATIVE", input.creativeBullets());
		appendConclusionSection(block, "GEO", input.geoBullets());
		appendConclusionSection(block, "AUDIENCE", input.audienceFields());
		appendConclusionSection(block, "DEVICE", input.deviceFields());
		return block.length() > contentStart ? block.toString() : "";
	}

	/**
	 * Appends one labelled breakdown section's non-blank bullets to a tactic-thoughts context block, skipping
	 * the section entirely when it is null or holds nothing.
	 *
	 * @param block   the block being built
	 * @param label   the section header (e.g. {@code "GEO"})
	 * @param bullets the section's bullets, possibly {@code null} or holding blank entries
	 */
	void appendConclusionSection(StringBuilder block, String label, List<String> bullets) {
		if (bullets == null) {
			return;
		}
		List<String> nonBlank = new ArrayList<>();
		for (String bullet : bullets) {
			if (bullet != null && !bullet.isBlank()) {
				nonBlank.add(bullet.trim());
			}
		}
		if (nonBlank.isEmpty()) {
			return;
		}
		block.append(label).append(":\n");
		for (String bullet : nonBlank) {
			block.append("- ").append(bullet).append('\n');
		}
	}

	/**
	 * Builds the Step-4 campaign-level results prompt: the grouped {@code results_overviews}, the four
	 * {@code thoughts_on_performance} paragraphs, the four {@code optimization_recommendations}, and the
	 * frequency narrative. Unlike the classic Batch C prompt it does NOT request per-tactic overviews (those
	 * come from Step 2) and it reasons over per-tactic DIGESTS — each tactic's Step-3 thoughts where available,
	 * otherwise its overview plus a short breakdown digest — never over raw grids.
	 *
	 * @param data        parsed campaign data supplying the shared context and the tactic-group ranges
	 * @param brief       free-text campaign brief the copy must stay faithful to
	 * @param frequencies pre-computed planned/actual frequency figures embedded in the frequency narrative
	 * @param perTactic   one digest per tactic (Step-3 thoughts, or overview + breakdown digest as fallback)
	 * @return the campaign-results prompt, or empty when there is no tactic context to reason over
	 */
	public Optional<String> buildCampaignResultsPrompt(
			CampaignData data, String brief, CampaignFrequencies frequencies, List<TacticNarrativeDigest> perTactic) {
		if (data == null || data.tactics() == null || data.tactics().isEmpty()
				|| perTactic == null || perTactic.isEmpty()) {
			return Optional.empty();
		}
		boolean hasFrequencies = frequencies != null
				&& normalizer.notBlank(frequencies.plan()) && normalizer.notBlank(frequencies.fact());

		Map<Integer, List<String>> tacticsByGroup = new java.util.TreeMap<>();
		for (Integer k : data.tactics().keySet()) {
			tacticsByGroup.computeIfAbsent((k - 1) / TACTICS_PER_GROUP + 1, g -> new ArrayList<>())
					.add(String.valueOf(k));
		}
		List<String> groupNumList = new ArrayList<>();
		List<String> groupRangeList = new ArrayList<>();
		for (Map.Entry<Integer, List<String>> g : tacticsByGroup.entrySet()) {
			groupNumList.add(String.valueOf(g.getKey()));
			groupRangeList.add("group " + g.getKey() + " → tactics " + String.join(",", g.getValue()));
		}
		String groupNums = String.join(", ", groupNumList);
		String groupRanges = String.join("; ", groupRangeList);

		int overviewPrompt = bufferedLimit(RESULTS_OVERVIEW_LIMIT);
		int thoughtsPrompt = bufferedLimit(THOUGHTS_TOTAL_LIMIT);
		int recTitlePrompt = bufferedLimit(RECOMMENDATION_TITLE_LIMIT);
		int recTextPrompt = bufferedLimit(RECOMMENDATION_TEXT_LIMIT);
		int fOpportunityPrompt = bufferedLimit(F_OPPORTUNITY_LIMIT);
		int fFactPrompt = bufferedLimit(F_FACT_LIMIT);
		int fStorytellingPrompt = bufferedLimit(F_STORYTELLING_LIMIT);

		String prompt =
				"You are a senior digital media analyst writing the campaign-level copy for a post-campaign report.\n\n"
						+ "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
						+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION: what happened, WHY (a specific cause), and "
						+ "what it means — one flowing statement, never labelled sections.\n"
						+ "2. INTERPRET, NEVER ENUMERATE. The reader can see numbers; explain what they mean.\n"
						+ "3. 'SO WHAT' IS MANDATORY: every metric cited is followed by its business consequence.\n"
						+ "4. NO GENERIC LANGUAGE: every sentence is specific to THIS campaign.\n"
						+ "5. NAME THE CAUSE for strong or soft performance.\n"
						+ METRIC_DEVIATION_RULE
						+ LEARNING_PHASE_RULE
						+ "\n"
						+ "The per-tactic conclusions you reason over are given below (each tactic's synthesised "
						+ "thoughts, or its overview plus breakdown reads). Do NOT restate a single tactic — "
						+ "synthesise across the whole campaign.\n\n"
						+ "Return a JSON object with EXACTLY these keys:\n"
						+ "{\n"
						+ "  \"results_overviews\": { // Keyed by tactic-group number as strings (" + groupNums + "). "
						+ "One entry PER GROUP (" + groupRanges + "). Each value covers ONLY that group's tactics, "
						+ "EXACTLY 2 SENTENCES, past tense, ≤" + overviewPrompt
						+ " chars: sentence 1 = overall result + key metric vs "
						+ "plan + a cause; sentence 2 = which tactic(s) led vs lagged, each with a reason. "
						+ "Refer to a tactic by the display name given after its number below (e.g. 'CTV'), NEVER as "
						+ "'Tactic 7'; when a tactic has no name, describe it without a label. "
						+ "Client-facing tone: lead with what was achieved, frame gaps as external constraints. },\n"
						+ "  \"thoughts_on_performance\": string, // EXACTLY 4 short paragraphs joined by \" | \" "
						+ "(exactly 3 separators), ≤" + thoughtsPrompt
						+ " chars total. (1) best tactic/channel + WHY; (2) why the "
						+ "campaign succeeded — name the mechanism; (3) one creative/format insight; (4) an efficiency "
						+ "or reach insight.\n"
						+ "  \"optimization_recommendations\": array, // EXACTLY 4 objects {\"title\": ≤"
						+ recTitlePrompt
						+ " chars Title Case imperative, \"text\": ≤" + recTextPrompt
						+ " chars one sentence}. Distinct forward-looking levers grounded "
						+ "in the results; proactive optimisations, never a fix for past error.\n"
						+ (hasFrequencies
								? "  \"f_opportunity\": string, // ≤" + fOpportunityPrompt
								+ " chars. Name the client's industry (infer it) and "
								+ "convey: it takes " + frequencies.plan() + " touchpoints to move a user from passive "
								+ "awareness to active intent. Use " + frequencies.plan() + " VERBATIM.\n"
								+ "  \"f_fact\": string, // ≤" + fFactPrompt + " chars. Actual delivered frequency was "
								+ frequencies.fact()
								+ " touchpoints per user, closely aligned with plan. Use " + frequencies.fact()
								+ " VERBATIM.\n"
								+ "  \"f_storytelling\": string, // ≤" + fStorytellingPrompt + " chars. Frequency was "
								+ frequencies.fact() + " vs "
								+ frequencies.plan() + ", positively impacting performance; recommend maintaining it"
								+ (frequencies.remainingAudience() != null
										? " as we engage the remaining ~" + fmt.compact(frequencies.remainingAudience())
										+ " in-market audience"
										: "") + ". Neutral, factual tone; use the numbers VERBATIM.\n"
								: "")
						+ "}\n\n"
						+ "Rules: return ONLY the JSON (no markdown/backticks); include a key for every group number "
						+ "listed (" + groupNums + "); ALWAYS return exactly 4 recommendations; do NOT invent metrics; "
						+ "thoughts_on_performance uses \" | \" not newlines; each field ends on a complete sentence "
						+ "within its limit; English.\n\n"
						+ campaignContextForConclusions(data, brief) + "\n\n"
						// Deliberately NO cache breakpoint: this prompt's instruction prefix is unique to the
						// campaign-results call and the call runs once per report, so a cache block written here
						// could never be read back. Marking it would only pay the cache-write surcharge.
						+ "=== PER-TACTIC CONCLUSIONS ===\n"
						+ perTacticDigestBlock(perTactic);
		return Optional.of(prompt);
	}

	/**
	 * Shrinks a field's real character budget to the smaller number quoted to Claude, so a reply that lands
	 * slightly over the limit it was given still fits the budget actually enforced on it and skips the
	 * compression call. Same {@link #COMPRESSION_PROMPT_BUFFER_RATIO} the per-section prompts already apply.
	 *
	 * @param limit the field's real character budget
	 * @return the buffered limit to quote in the prompt, at least 1
	 */
	int bufferedLimit(int limit) {
		return Math.max(1, (int) (limit * COMPRESSION_PROMPT_BUFFER_RATIO));
	}

	/**
	 * Renders the per-tactic digests as the variable body of the campaign-results prompt: for each tactic, its
	 * Step-3 thoughts when present, otherwise its overview plus its short breakdown digest lines. Tactics with
	 * no content at all are skipped.
	 *
	 * The tactic's display name is appended to its header when the sheet supplied one, so the campaign copy can
	 * name the channel rather than a bare tactic number.
	 *
	 * @param perTactic the per-tactic digests, in tactic order
	 * @return the {@code Tactic N — Name: …} digest block
	 */
	String perTacticDigestBlock(List<TacticNarrativeDigest> perTactic) {
		StringBuilder block = new StringBuilder();
		for (TacticNarrativeDigest digest : perTactic) {
			if (digest == null) {
				continue;
			}
			StringBuilder body = new StringBuilder();
			if (digest.thoughts() != null && !digest.thoughts().isEmpty()) {
				for (String thought : digest.thoughts()) {
					if (thought != null && !thought.isBlank()) {
						body.append("  - ").append(thought.trim()).append('\n');
					}
				}
			} else {
				if (digest.overview() != null && !digest.overview().isBlank()) {
					body.append("  Overview: ").append(digest.overview().trim()).append('\n');
				}
				if (digest.breakdownDigestLines() != null) {
					for (String line : digest.breakdownDigestLines()) {
						if (line != null && !line.isBlank()) {
							body.append("  - ").append(line.trim()).append('\n');
						}
					}
				}
			}
			if (body.length() > 0) {
				block.append("Tactic ").append(digest.tacticNum());
				if (normalizer.notBlank(digest.tacticName())) {
					block.append(" — ").append(digest.tacticName().trim());
				}
				block.append(":\n").append(body);
			}
		}
		return block.toString();
	}

	/**
	 * Builds the Batch D (compression) prompt asking Claude to shrink each oversized field to its character
	 * budget while preserving meaning, or empty when there are no fields to compress. The limit quoted to
	 * Claude is {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the field's real budget — not the budget we
	 * actually enforce — so the rewrite has headroom to still fit after the hard-truncation safety net runs.
	 *
	 * @param fields oversized fields to compress, each carrying its own raw text and character budget
	 * @return the compression prompt requesting a JSON object keyed by each field's {@code key}, or empty when
	 * {@code fields} is empty
	 */
	public Optional<String> buildCompressionPrompt(List<ClaudeCompressionField> fields) {
		if (fields.isEmpty()) {
			return Optional.empty();
		}
		List<String> entries = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (ClaudeCompressionField field : fields) {
			int promptLimit = Math.max(1, (int) (field.maxChars() * COMPRESSION_PROMPT_BUFFER_RATIO));
			keys.add("\"" + field.key() + "\"");
			entries.add("- key: \"" + field.key() + "\", limit: " + promptLimit + " characters\n"
					+ "  text: \"" + field.text().replace("\"", "'") + "\"");
		}
		return Optional.of(
				"You are editing client-facing campaign-report copy that is too long for its layout slot.\n\n"
						+ "For EACH field below, REWRITE it as a complete, self-contained thought that fits within " +
						"its character limit — do not just truncate the original. Preserve the key meaning and " +
						"business message as closely as possible: cut whole secondary clauses or examples before " +
						"cutting the main point, then re-word what remains so it reads as a finished sentence.\n\n"
						+ "Fields:\n" + String.join("\n", entries) + "\n\n"
						+ "Return ONLY a JSON object mapping each key to its rewritten text, with no other keys:\n"
						+ "{" + String.join(", ", keys) + "}\n\n"
						+ "Rules:\n"
						+ "- Every value's length MUST be at or under that field's character limit — count " +
						"characters, not words.\n"
						+ "- CRITICAL: every value MUST end with a period (or other sentence-ending punctuation) " +
						"and read as a complete thought. NEVER end mid-clause, mid-list, or on a dangling comma, " +
						"dash, or conjunction (e.g. \"and\", \"to ensure\", \"such as\"). If the full original idea " +
						"does not fit, drop an entire trailing clause/example rather than trimming it down to a " +
						"fragment.\n"
						+ "- Never cut off mid-word.\n"
						+ "- Keep the same language (English) and tense as the original text.\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation."
		);
	}

	/**
	 * Builds the geo summarisation prompt from the geography-bearing rows of the media-plan workbook.
	 *
	 * <p>The caller passes rows already reduced by {@link WorkbookGeoFilter}, never the raw workbook: the
	 * answer is one ≤40-character string, so shipping every budget and pacing row of a large client plan
	 * only risked blowing the model's context window.
	 *
	 * @param geoRows the kept workbook rows, each already rendered as its cells joined with {@code " | "} and
	 *                interleaved with the {@code "### TAB: <name> ###"} markers of the tabs they came from
	 * @return a prompt asking Claude to condense the campaign's geographic targeting into a single short
	 * comma-separated string of key regions
	 */
	public String buildGeoPrompt(List<String> geoRows) {
		return "Below are the geography-related rows of a media-plan workbook (each tab preceded by a "
				+ "'### TAB: <name> ###' marker).\n"
				+ "Find the campaign's geographic targeting locations anywhere in this data and summarise them into a "
				+ "single short comma-separated string (≤40 characters), naming the most important "
				+ "regions/cities/states. Ignore non-geographic data. No explanation — return only the string.\n\n"
				+ String.join("\n", geoRows);
	}

	/**
	 * Builds the funnel-stage summarisation prompt from the campaign's per-tactic goals.
	 *
	 * <p>The goals are the reviewed {@code {{tactic n goal}}} values read back from the assembled EOC sheet,
	 * so the funnel line is inferred from a dozen short strings the user has already seen and can correct —
	 * not from a scan of the whole source workbook.
	 *
	 * @param tacticGoals the non-blank per-tactic goal strings, in tactic order
	 * @return a prompt asking Claude to condense the goals into a single short comma-separated funnel-stage
	 * string, or empty when no goal carries any text
	 */
	public Optional<String> buildFunnelFromGoalsPrompt(List<String> tacticGoals) {
		List<String> goals = new ArrayList<>();
		if (tacticGoals != null) {
			for (String goal : tacticGoals) {
				if (normalizer.notBlank(goal)) {
					goals.add("  - " + goal.trim());
				}
			}
		}
		if (goals.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(
				"Below are the per-tactic goals of a digital media campaign.\n"
						+ "Determine the marketing funnel stages the campaign targets (typically some of "
						+ "Awareness, Consideration, Conversion, Retention/Loyalty), inferring them from these goals. "
						+ "Return a single short comma-separated string (≤60 characters) ordered top-of-funnel first, "
						+ "with no stage repeated. No explanation — return only the string.\n\n"
						+ "=== TACTIC GOALS ===\n" + String.join("\n", goals));
	}

	/**
	 * Builds the brief-digest prompt: condenses the free-text campaign brief into a compact, thesis-style
	 * summary that every later batch uses in place of the raw brief.
	 *
	 * <p>The brief is user-pasted and unbounded, and it was previously repeated verbatim into a dozen
	 * prompts. Digesting it once means the campaign context is paid for once at full length and carried
	 * everywhere else at a fraction of the tokens, while the facts the copy must stay faithful to survive.
	 *
	 * @param brief    the free-text campaign brief, optionally with its change-log section appended
	 * @param maxChars the character budget the digest must fit into
	 * @return the prompt asking for the digest, or empty when the brief is blank
	 */
	public Optional<String> buildBriefDigestPrompt(String brief, int maxChars) {
		if (!normalizer.notBlank(brief)) {
			return Optional.empty();
		}
		return Optional.of(
				"You are condensing a campaign brief for a digital advertising agency into the working context "
						+ "that every later copywriting step will read instead of the full brief.\n\n"
						+ "Write a compact, thesis-style digest of at most " + maxChars + " characters. Rules:\n"
						+ "- KEEP every fact later copy must stay faithful to: client and product, the business "
						+ "objective, the target audience, the geography, the flight window, the budget, the stated "
						+ "KPIs and success criteria, the channel/tactic mix, and any explicit constraint, mandatory "
						+ "or prohibition.\n"
						+ "- KEEP any mid-flight changes section verbatim in substance — what changed, and why.\n"
						+ "- DROP boilerplate, agency pleasantries, process/timeline talk, contact details, legal "
						+ "footers and anything repeated.\n"
						+ "- Do NOT invent, infer or extrapolate a single fact. If the brief does not say it, it is "
						+ "not in the digest.\n"
						+ "- Write dense declarative sentences grouped by topic, no bullet characters, no markdown, "
						+ "no headings, no preamble.\n"
						+ "- Output in English regardless of the input language.\n\n"
						+ "Return ONLY the digest text.\n\n"
						+ "=== CAMPAIGN BRIEF ===\n" + brief);
	}

	/**
	 * Builds the primary-KPIs prompt from the campaign tactic mix, or empty when there are no tactics.
	 *
	 * <p>Each tactic line carries the metrics actually present in the plan ({@code CTR} signals a
	 * display/banner format, {@code VCR} a video/CTV/audio format) so Claude can pick the right KPI per
	 * tactic and return a single de-duplicated comma-separated line.
	 *
	 * @param data parsed campaign data whose tactic map drives the KPI selection
	 * @return a prompt asking Claude for a single comma-separated primary-KPIs line, or empty when no tactics exist
	 */
	public Optional<String> buildPrimaryKpisPrompt(CampaignData data) {
		if (data == null || data.tactics() == null || data.tactics().isEmpty()) {
			return Optional.empty();
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  - " + t.name());
			List<String> metrics = new ArrayList<>();
			if (t.ctr() != null) {
				metrics.add("has CTR");
			}
			if (t.vcr() != null) {
				metrics.add("has VCR");
			}
			if (!metrics.isEmpty()) {
				line.append(" (").append(String.join(", ", metrics)).append(')');
			}
			tacticLines.add(line.toString());
		}

		String prompt =
				"You are a digital media analyst. Below is the tactic mix of a single advertising campaign.\n"
						+ "Output the campaign's PRIMARY KPIs as ONE short comma-separated line, naming only the KPIs "
						+ "relevant to this tactic mix.\n\n"
						+ "Rules:\n"
						+ "- Always start with \"Imps\".\n"
						+ "- Include \"CTR\" if any tactic is a display/banner/native format (or has CTR).\n"
						+ "- Include \"VCR\" if any tactic is a video/CTV/OTT/pre-roll/audio format (or has VCR).\n"
						+ "- Always end with \"R&F\".\n"
						+ "- De-duplicate; keep this order: Imps, CTR, VCR, R&F (omit CTR or VCR when not relevant).\n"
						+ "- Return ONLY the single line — no markdown, no backticks, no explanation, no trailing "
						+ "period.\n"
						+ "- Examples: \"Imps, CTR, R&F\" (display only), \"Imps, VCR, R&F\" (video only), "
						+ "\"Imps, CTR, VCR, R&F\" (mixed).\n\n"
						+ "Tactics:\n" + String.join("\n", tacticLines);
		return Optional.of(prompt);
	}

	/**
	 * Builds the Batch D (narrative alignment) prompt, or empty when there is nothing to align.
	 *
	 * <p>Unlike Batches A–C, this prompt sends Claude no raw plan or metric grid: it sends the copy those
	 * batches already wrote — the proposal overview, the four strategic insights, the per-group results
	 * overviews, the performance thoughts, and the frequency narrative — as a {@code === CURRENT DRAFT ===}
	 * block, plus a read-only {@code === BREAKDOWN SIGNALS ===} digest of the per-tactic slide conclusions.
	 * Claude's job is editorial, not analytical: reconcile these independently written fields into one
	 * storyline that stays faithful to the brief, tightening or re-pointing copy without inventing facts that
	 * are not already present in the draft or digest.
	 *
	 * <p>The reply mirrors the source schema exactly ({@code proposal_overview}, {@code strategic_insights},
	 * {@code results_overviews}, {@code thoughts_on_performance}, {@code f_opportunity}/{@code f_fact}/
	 * {@code f_storytelling}) so {@link RealClaudeClient} can parse and re-limit it with the same helpers that
	 * handled the originals. Fields absent from the draft are omitted from both the request and the schema, so
	 * the model is never asked to fabricate a field that was empty to begin with.
	 *
	 * @param strategic       the Batch A output whose {@code proposalOverview}/{@code strategicInsights} feed the draft
	 * @param results         the Batch C output whose results overviews, thoughts and frequency narrative feed the draft
	 * @param breakdownDigest one short line per breakdown conclusion; rendered as read-only alignment context
	 * @param brief           free-text campaign brief the aligned narrative must stay faithful to
	 * @return the Batch D alignment prompt, or empty when the draft carries no alignable campaign-level copy
	 */
	public Optional<String> buildBatchDPrompt(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief) {
		List<String> draft = new ArrayList<>();
		List<String> schema = new ArrayList<>();

		String proposal = strategic == null ? null : strategic.proposalOverview();
		if (proposal != null && !proposal.isBlank()) {
			draft.add("proposal_overview: " + proposal.trim());
			schema.add("  \"proposal_overview\": string,   // Exactly 2 sentences, past tense, no line breaks. "
					+ "≤400 chars. Keep every named tactic/audience/geo from the draft; only sharpen wording.\n");
		}

		List<StrategicInsight> insights = strategic == null ? null : strategic.strategicInsights();
		int insightCount = 0;
		if (insights != null) {
			for (StrategicInsight si : insights) {
				if (si == null) {
					continue;
				}
				String point = si.point() == null ? "" : si.point().trim();
				String overview = si.overview() == null ? "" : si.overview().trim();
				if (point.isEmpty() && overview.isEmpty()) {
					continue;
				}
				draft.add("strategic_insight[" + insightCount + "]: point=\"" + point + "\" overview=\"" + overview + "\"");
				insightCount++;
			}
		}
		if (insightCount > 0) {
			schema.add("  \"strategic_insights\": array,     // EXACTLY " + insightCount
					+ " objects {\"point\": string (≤20 chars), \"overview\": string (≤230 chars)}, in the same "
					+ "order as the draft. Each must stay a distinct facet of the ONE campaign storyline.\n");
		}

		Map<Integer, String> overviews = results == null ? null : results.resultsOverviews();
		if (overviews != null && !overviews.isEmpty()) {
			List<String> groupKeys = new ArrayList<>();
			for (Map.Entry<Integer, String> e : overviews.entrySet()) {
				if (e.getValue() == null || e.getValue().isBlank()) {
					continue;
				}
				draft.add("results_overview[" + e.getKey() + "]: " + e.getValue().trim());
				groupKeys.add(String.valueOf(e.getKey()));
			}
			if (!groupKeys.isEmpty()) {
				schema.add("  \"results_overviews\": object,     // Keyed by group number as strings ("
						+ String.join(", ", groupKeys) + "). One entry per key listed, no more, no fewer. Each: EXACTLY "
						+ "2 sentences, past tense, ≤380 chars. Must pay off the same storyline with its own group's "
						+ "numbers.\n");
			}
		}

		List<String> thoughts = results == null ? null : results.thoughtsOnPerformance();
		int thoughtCount = 0;
		if (thoughts != null) {
			for (String t : thoughts) {
				if (t != null && !t.isBlank()) {
					draft.add("thought[" + thoughtCount + "]: " + t.trim());
					thoughtCount++;
				}
			}
		}
		if (thoughtCount > 0) {
			schema.add("  \"thoughts_on_performance\": array, // EXACTLY " + thoughtCount
					+ " strings (≤220 chars each), same order as the draft. Together they must read as the campaign "
					+ "headline expanded — no contradictions with the overviews above.\n");
		}

		String fOpportunity = results == null ? null : results.fOpportunity();
		if (fOpportunity != null && !fOpportunity.isBlank()) {
			draft.add("f_opportunity: " + fOpportunity.trim());
			schema.add("  \"f_opportunity\": string,         // ≤180 chars. Same frequency point, aligned wording.\n");
		}
		String fFact = results == null ? null : results.fFact();
		if (fFact != null && !fFact.isBlank()) {
			draft.add("f_fact: " + fFact.trim());
			schema.add("  \"f_fact\": string,                // ≤140 chars.\n");
		}
		String fStorytelling = results == null ? null : results.fStorytelling();
		if (fStorytelling != null && !fStorytelling.isBlank()) {
			draft.add("f_storytelling: " + fStorytelling.trim());
			schema.add("  \"f_storytelling\": string,        // ≤320 chars.\n");
		}

		if (draft.isEmpty() || schema.isEmpty()) {
			return Optional.empty();
		}

		StringBuilder context = new StringBuilder();
		String brf = brief == null ? "" : brief.trim();
		if (!brf.isEmpty()) {
			context.append("=== CAMPAIGN BRIEF ===\n").append(brf).append("\n\n");
		}
		context.append("=== CURRENT DRAFT (independently written — your job is to align it) ===\n")
				.append(String.join("\n", draft)).append("\n");
		if (breakdownDigest != null && !breakdownDigest.isEmpty()) {
			context.append("\n=== BREAKDOWN SIGNALS (read-only — reflect, do not restate or rewrite) ===\n")
					.append(String.join("\n", breakdownDigest)).append("\n");
		}

		String prompt =
				"You are the lead editor on a client-facing digital-media campaign report. Several sections were "
						+ "drafted independently by different analysts, so they repeat, drift, and sometimes explain the "
						+ "same result with different causes.\n\n"
						+ "YOUR JOB — editorial alignment, NOT reanalysis:\n"
						+ "1. ONE STORYLINE. Decide the single most important story of this campaign from the draft, then "
						+ "make every field a consistent facet of it. The proposal sets it up; the results overviews and "
						+ "thoughts pay it off; the strategic insights and frequency copy reinforce it.\n"
						+ "2. NO CONTRADICTIONS. If two fields explain the same outcome with different causes, pick the "
						+ "best-supported cause and use it consistently. Name the hero tactic, the laggard, and the "
						+ "audience the SAME way everywhere.\n"
						+ "3. STAY FAITHFUL TO THE BRIEF. Every claim must be consistent with the campaign brief above. "
						+ "Drop or correct anything the draft says that the brief contradicts.\n"
						+ "4. DO NOT INVENT. Use only facts already present in the draft or the read-only breakdown "
						+ "signals. You are tightening and reconciling existing copy, not adding new data. If a field is "
						+ "already good and consistent, return it essentially unchanged.\n"
						+ "5. KEEP THE SHAPE. Return the SAME fields with the SAME counts and character limits as below. "
						+ "Past tense, Business English, no filler, no labels inside the copy.\n\n"
						+ "Return ONLY a JSON object with EXACTLY these keys (omit none, add none):\n\n"
						+ "{\n"
						+ String.join("", schema)
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ context;
		return Optional.of(prompt);
	}
}
