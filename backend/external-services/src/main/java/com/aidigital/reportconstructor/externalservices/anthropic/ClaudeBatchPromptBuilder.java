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
	 * per-tactic gender split and weekday/weekend peak windows. The context block mirrors {@link
	 * #buildBatchAPrompt} and every field instruction is copied verbatim from Batches A and B so the sheet
	 * copy is generated identically to the slide deck, just without the unused proposal/strategic/results
	 * fields.
	 *
	 * @param data  parsed campaign plan and per-tactic performance used to assemble the context blocks and
	 *                 the per-tactic JSON keys
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated
	 *                as empty when null)
	 * @return the merged sheet prompt requesting audience + per-tactic JSON, or empty when no context block
	 * could be built
	 */
	public Optional<String> buildBatchSheetPrompt(CampaignData data, String brief) {
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
	 * Builds the Batch C (results) prompt, or empty when there is no campaign context to send.
	 *
	 * @param data        parsed campaign data including overall totals and actual-vs-plan tactic metrics used for the
	 *                       results context blocks
	 * @param brief       free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as
	 *                       empty when null)
	 * @param frequencies pre-computed planned/actual frequency figures; when both are present a {@code === FREQUENCY
	 *                       ===} block is added and Claude is asked to fill the frequency-narrative fields, otherwise
	 *                       those fields are returned null
	 * @return the Batch C post-campaign prompt requesting results overview, performance thoughts and per-tactic
	 * overviews JSON, or empty when no context block could be built
	 */
	public Optional<String> buildBatchCPrompt(CampaignData data, String brief, CampaignFrequencies frequencies) {
		String brf = brief == null ? "" : brief;
		boolean hasFrequencies = frequencies != null
				&& normalizer.notBlank(frequencies.plan()) && normalizer.notBlank(frequencies.fact());

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

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
			if (t.spend() > 0) {
				line.append(" Actual Spend $").append(fmt.intGroup(Math.round(t.spend())));
			}
			if (t.imps() > 0) {
				line.append(" | Actual Imps ").append(fmt.intGroup(t.imps()));
			}
			if (t.ctr() != null) {
				line.append(" | Actual CTR ").append(fmt.dec2(t.ctr())).append('%');
			}
			if (t.vcr() != null) {
				line.append(" | Actual ").append(completionRateLabel(t.name())).append(' ')
						.append(fmt.dec2(t.vcr())).append('%');
			}
			if (t.planSpend() != null) {
				line.append(" | Plan Spend $").append(fmt.intGroup(Math.round(t.planSpend())));
			}
			if (t.planImps() != null) {
				line.append(" | Plan Imps ").append(fmt.intGroup(t.planImps()));
			}
			if (t.planCtr() != null) {
				line.append(" | Plan CTR ").append(fmt.dec2(t.planCtr())).append('%');
			}
			if (t.planVcr() != null) {
				line.append(" | Plan ").append(completionRateLabel(t.name())).append(' ')
						.append(fmt.dec2(t.planVcr())).append('%');
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
		if (!totalLines.isEmpty()) {
			ctx.add("=== OVERALL RESULTS ===\n" + String.join("\n", totalLines));
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== RESULTS BY TACTIC ===\n" + String.join("\n", tacticLines));
		}
		if (hasFrequencies) {
			ctx.add("=== FREQUENCY ===\n"
					+ "Planned frequency (touchpoints per user): " + frequencies.plan() + "\n"
					+ "Actual frequency (touchpoints per user):  " + frequencies.fact());
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		List<String> nums = new ArrayList<>();
		for (Integer k : data.tactics().keySet()) {
			nums.add(String.valueOf(k));
		}
		String tacticNums = String.join(", ", nums);

		// Tactic groups (7 tactics each) present in this campaign — one results overview per group:
		// group 1 → tactics 1–7, group 2 → 8–14, group 3 → 15–21, group 4 → 22–28.
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

		String prompt =
				"You are a senior digital media analyst writing a post-campaign report for a client presentation.\n\n"
						+ "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
						+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION in every insight. "
						+ "State what happened, explain WHY it happened (name a specific cause: learning phase, " +
						"creative fatigue, "
						+ "inventory constraints, bid competitiveness, audience saturation, pacing decision, seasonal " +
						"effect, etc.), "
						+ "then state what this means or what should follow. All three parts are mandatory, delivered "
						+ "as one cohesive, flowing statement — never as labeled sections or separate bullets.\n"
						+ "2. INTERPRET, NEVER ENUMERATE. The reader can see the numbers. Your job is to explain what " +
						"they mean. "
						+ "WRONG: \"CTV delivered 12M impressions, the highest of any channel.\" "
						+ "RIGHT: \"CTV absorbed the majority of delivery because it was the only channel with priced " +
						"inventory "
						+ "in the target geo during the campaign window — not a sign that other tactics " +
						"underperformed, "
						+ "but a feature of the buy structure.\" Transform every data point into a business " +
						"implication.\n"
						+ "3. 'SO WHAT' IS MANDATORY. Every metric cited must be followed by its business " +
						"consequence: "
						+ "did we hit goals? what does over/underdelivery mean for the brand? what did the audience " +
						"actually receive?\n"
						+ "4. NO GENERIC LANGUAGE. Forbidden: \"performance is tracking well\", \"results are in line " +
						"with expectations\", "
						+ "\"we recommend monitoring\", \"this tactic requires optimization\". "
						+ "Every sentence must be specific to THIS campaign's numbers, channels, and audience.\n"
						+ "5. NAME THE CAUSE. Don't say performance was strong — say why: audience targeting " +
						"precision, "
						+ "creative format fit, placement quality, flight timing, competitive dynamics, etc.\n"
						+ METRIC_DEVIATION_RULE
						+ LEARNING_PHASE_RULE
						+ "\n"
						+ "Read the campaign data and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"results_overviews\": {              // Keyed by tactic-group number as strings (" + groupNums + ").\n"
						+ "  //  One entry PER GROUP listed above (" + groupRanges + "). Each value covers ONLY that " +
						"group's tactics.\n"
						+ "  //  Include a key for every group number listed — no more, no fewer.\n"
						+ "    \"1\": string,                       // Key is the group number as a string (never a " +
						"letter). EXACTLY 2 SENTENCES. Past tense, no bullets, no " +
						"line breaks. Hard limit: ≤380 chars total.\n"
						+ "  //  SENTENCE 1 — Overall result + key metric vs plan + reason WHY performance was as it " +
						"was.\n"
						+ "  //    Must include: the most significant delivery outcome (over/underdelivery vs plan) + " +
						"one specific cause\n"
						+ "  //    (budget pacing, inventory constraints, audience fit, bid dynamics, flight timing, " +
						"etc.).\n"
						+ "  //    WRONG: \"The campaign delivered X impressions across Y tactics.\"\n"
						+ "  //    RIGHT: \"The campaign significantly underdelivered against planned reach goals due " +
						"to budget pacing delays,\n"
						+ "  //            achieving only 4.8M impressions versus a planned 13M+ through Q1 spending " +
						"constraints.\"\n"
						+ "  //  SENTENCE 2 — Tactic-level breakdown: which tactic(s) led performance and which " +
						"lagged, with a specific reason for each.\n"
						+ "  //    Name the actual tactics. Include one metric per tactic (VCR%, CTR%, imps, spend). " +
						"Name the cause.\n"
						+ "  //    RIGHT: \"Video tactics dominated actual delivery with strong completion rates (97% " +
						"for Live Sports, 98% for CTV/Netflix),\n"
						+ "  //            while display formats like DOOH underperformed due to limited spend " +
						"activation.\"\n"
						+ "  //  DO NOT write a third sentence. Stop after the second.\n"
						+ "  //  CLIENT-FACING TONE: This text goes directly into a client presentation.\n"
						+ "  //    Always lead with what went well or what was achieved — even in underdelivery " +
						"scenarios, frame it as\n"
						+ "  //    a strategic constraint, not a failure. Highlight strong metrics (VCR, CTR, " +
						"completion rates)\n"
						+ "  //    before mentioning gaps. If a tactic underperformed, attribute it to external " +
						"factors\n"
						+ "  //    (inventory availability, budget pacing, market conditions) — never to poor " +
						"execution.\n"
						+ "  },\n"
						+ "  \"thoughts_on_performance\": string,  // EXACTLY 4 SHORT ANALYTICAL PARAGRAPHS separated " +
						"by the literal string \" | \".\n"
						+ "  //  Each paragraph: 1–2 sentences, past tense, client-friendly. NOT bullet headers — " +
						"flowing sentences.\n"
						+ "  //  REQUIRED STRUCTURE — exactly these 4 paragraphs in this order:\n"
						+ "  //  (1) Which tactic/channel performed best and the specific reason WHY (not just 'it " +
						"performed well').\n"
						+ "  //  (2) Why the campaign succeeded overall — name the mechanism: targeting precision, " +
						"audience-channel fit, creative alignment, etc.\n"
						+ "  //  (3) One creative or format insight — what worked and why (format size, video length, " +
						"placement position, etc.).\n"
						+ "  //  (4) Efficiency or reach insight — what the spend delivered beyond raw impressions " +
						"(CPM efficiency, frequency management, reach quality).\n"
						+ "  //  CRITICAL: produce EXACTLY 4 paragraphs — no more, no fewer. Result must contain " +
						"EXACTLY 3 \" | \" separators.\n"
						+ "  //  BAD example: \"Programmatic video performed well.\" | \"Audience targeting was " +
						"effective.\" | ...\n"
						+ "  //  GOOD example: \"Programmatic video exceeded impression goals by 0.6%, driven by " +
						"strong inventory availability "
						+ "in the 25-44 demo during evening dayparts — the format's native environment for this " +
						"audience.\" | ...\n"
						+ "  //  Total string including \" | \" separators must be ≤700 chars.\n"
						+ "  \"tactic_overviews\": {               // Per-tactic. Keys: tactic numbers as strings (" + tacticNums + ")\n"
						+ "    \"N\": string                        // MAX 190 CHARACTERS. End on a complete word and " +
						"sentence.\n"
						+ "  //  STRUCTURE: [What the tactic delivered vs plan] + [WHY it performed as it did] + " +
						"[business So what].\n"
						+ "  //  All three parts required even in 190 chars — be concise but complete.\n"
						+ "  //  WRONG: \"CTV delivered 5M impressions at 98% VCR, exceeding plan.\"\n"
						+ "  //  RIGHT: \"CTV delivered 5M impressions at 98% VCR (+2pp vs plan), driven by premium " +
						"inventory selection — "
						+ "confirming the audience's high receptivity to full-screen video in this vertical.\"\n"
						+ "  //  Focus metrics by tactic type: Display→Imps+CTR; Video/Pre-roll→Imps+CTR+VCR; " +
						"CTV/OTT→Imps+VCR; Audio→Completions.\n"
						+ "  //  Past tense. No bullets. Business English. Max 2 sentences.\n"
						+ "  },\n"
						+ "  \"optimization_recommendations\": array  // EXACTLY 4 objects: {\"title\": string, " +
						"\"text\": string}.\n"
						+ "  //  This is the single most important slide of the deck — the agency's forward-looking, " +
						"actionable plan.\n"
						+ "  //  Every other section of this report exists to justify these 4 recommendations: ground " +
						"each one in the\n"
						+ "  //    actual results, tactics, audience and goal above.\n"
						+ "  //  PURPOSE: concrete next-step actions that show how to hit the client's goal even more " +
						"effectively next flight.\n"
						+ "  //  'title': MAX 28 CHARACTERS. A short imperative action headline, Title Case, no " +
						"trailing period\n"
						+ "  //    (e.g. \"Scale CTV In Evening Dayparts\", \"Refresh Display Creative\"). Name the " +
						"specific lever.\n"
						+ "  //  'text': MAX 125 CHARACTERS. One complete sentence stating WHAT to do and WHY it " +
						"advances the goal,\n"
						+ "  //    referencing a specific tactic / audience / metric from this campaign. End on a " +
						"complete sentence.\n"
						+ "  //  CLIENT-FACING TONE — non-negotiable: these recommendations represent the agency as the " +
						"expert partner.\n"
						+ "  //    Frame every item as a proactive optimization to amplify success — NEVER as a fix for " +
						"agency error\n"
						+ "  //    or past underperformance. Position the agency as the strategist driving the next win. " +
						"No blame, no\n"
						+ "  //    apology, no generic advice (\"keep monitoring\", \"optimize further\") — each must be " +
						"specific and ownable.\n"
						+ "  //  The 4 recommendations must be DISTINCT levers (e.g. budget reallocation, " +
						"audience expansion,\n"
						+ "  //    creative strategy, channel/daypart mix, measurement) — no two restating the same idea.\n"
						+ (hasFrequencies
								? "  \"f_opportunity\": string,           // MAX 180 CHARACTERS. End on a complete " +
								"sentence.\n"
								+ "  //  Convey EXACTLY this message, naming the client's specific industry (infer it " +
								"from the brief/campaign):\n"
								+ "  //  \"Based on our experience in your industry ([INDUSTRY]), it takes " + frequencies.plan() +
								" touchpoints to move a user\n"
								+ "  //   from passive awareness to active intent — keeping the brand top-of-mind and " +
								"triggering the 'Invisible Win' faster.\"\n"
								+ "  //  Use the number " + frequencies.plan() + " VERBATIM. Do not invent a different " +
								"figure.\n"
								+ "  \"f_fact\": string,                   // MAX 140 CHARACTERS. End on a complete " +
								"sentence.\n"
								+ "  //  Convey this message: in practice the campaign's actual delivered frequency came " +
								"out to " + frequencies.fact() + "\n"
								+ "  //   touchpoints per user, closely aligned with our original plan. Use the number " +
								frequencies.fact() + " VERBATIM.\n"
								+ "  \"f_storytelling\": string,           // MAX 320 CHARACTERS. End on a complete " +
								"sentence.\n"
								+ "  //  Convey EXACTLY this 3-part message, using all VERBATIM numbers given:\n"
								+ "  //  \"Our data shows frequency was " + frequencies.fact() + " vs " + frequencies.plan() +
								", which positively impacted overall\n"
								+ "  //   performance, consistent with the campaign results and booked media volume. " +
								"Moving forward, we recommend\n"
								+ "  //   maintaining this frequency as we engage the remaining in-market audience"
								+ (frequencies.remainingAudience() != null
										? " of approximately " + fmt.compact(frequencies.remainingAudience())
										: "")
								+ " available for upcoming\n"
								+ "  //   flights.\"\n"
								+ "  //  Tone: neutral and factual — do not editorialize or claim the results \"validate\" " +
								"or \"prove\" anything.\n"
								: "")
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for thoughts_on_performance if genuinely insufficient data.\n"
						+ "- For results_overviews: include a key for every tactic-group number listed above (" + groupNums +
						"); each value covers only that group's tactics.\n"
						+ "- For tactic_overviews: include a key for every tactic number listed above.\n"
						+ "- optimization_recommendations: ALWAYS return exactly 4 objects, each with non-empty title " +
						"and text.\n"
						+ "- Do NOT invent metrics. Use only the numbers provided.\n"
						+ "- Leave a field blank/omit the claim rather than pad it with generic filler — an unsupported "
						+ "sentence is worse than a shorter, fully-grounded one.\n"
						+ "- CRITICAL: each tactic_overview value MUST end on a complete word/sentence and be ≤190 " +
						"characters.\n"
						+ "- thoughts_on_performance uses \" | \" (space-pipe-space) as paragraph separator — NOT " +
						"newlines.\n"
						+ "- DEPTH OVER BREADTH: one insight with a real explanation beats three that only restate " +
						"numbers.\n"
						+ (hasFrequencies
								? "- f_opportunity / f_fact / f_storytelling: keep each within its character limit, embed " +
								"the supplied frequency numbers VERBATIM, and end on a complete sentence.\n"
								: "")
						+ "- Output in English.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Builds the publisher-observations prompt for one chunk of tactics, or empty when the chunk carries
	 * no tactic with publisher rows.
	 *
	 * <p>The per-bullet limit quoted to Claude is {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the slide's
	 * real 155-character budget, for the same reason the compression prompt shrinks its quoted limit: text
	 * written right up to the budget has nowhere to go when the truncation safety net runs, and gets cut
	 * mid-thought. Asking for the smaller number up front means most bullets never need compressing at all.
	 *
	 * @param inputs   the chunk's tactics, each with its hand-entered publisher table
	 * @param brief    free-text campaign brief, used to tie the channel mix back to the audience
	 * @param maxChars the slide's real per-bullet character budget
	 * @return the prompt requesting a JSON object of {@code "tactic_<n>"} → 4-bullet array, or empty when
	 * every tactic in the chunk has an empty table
	 */
	public Optional<String> buildPublisherObservationsPrompt(
			List<PublisherObservationInput> inputs, String brief, int maxChars) {
		List<String> blocks = new ArrayList<>();
		for (PublisherObservationInput input : inputs) {
			if (input.rows() == null || input.rows().isEmpty()) {
				continue;
			}
			StringBuilder block = new StringBuilder();
			block.append("tactic_").append(input.tacticNum())
					.append(" — ").append(input.tacticName()).append('\n')
					.append("Publisher | Impressions | Share of voice\n");
			for (PublisherRow row : input.rows()) {
				block.append(row.name()).append(" | ").append(row.impressions())
						.append(" | ").append(row.shareOfVoice()).append('\n');
			}
			blocks.add(block.toString());
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		int promptLimit = Math.max(1, (int) (maxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior programmatic media analyst writing the KEY OBSERVATIONS bullets for the "
				+ "'Top Publishers' slide of an end-of-campaign report. You are writing on behalf of the team that "
				+ "ran this campaign, so the tone is confident and complimentary of our own delivery — these "
				+ "observations should make the reader feel the publisher mix was managed deliberately and well.\n\n"
				+ "For EACH tactic below, write exactly 4 observations about its publisher delivery.\n\n"
				+ "Context you MUST reflect:\n"
				+ "- The table lists only the TOP ~15 publishers. Behind them sits a long tail of thousands more "
				+ "publishers that also carried delivery — the top list is the head of a much wider distribution, "
				+ "never the whole of it.\n"
				+ "- We run an AUDIENCE-FIRST approach: we do not chase specific sites, we chase the audience. The "
				+ "target audience can show up across a huge variety of publishers, and what matters is that "
				+ "wherever it appears, that inventory matches the targeting we chose — not that any single "
				+ "publisher was picked in advance.\n\n"
				+ "Rules:\n"
				+ "- Each observation is ONE complete sentence, at most " + promptLimit + " characters.\n"
				+ "- Ground every observation in the numbers given: name real publishers, cite real shares and "
				+ "impressions, compare ranks, and frame the head vs. the long tail of thousands of other publishers.\n"
				+ "- At most ~20% of each observation may go beyond the table — a short, well-established read on "
				+ "why that publisher mix fits the audience we targeted (e.g. an audience interested in home "
				+ "improvement naturally over-indexing on a given publisher). Never invent a metric that is not in "
				+ "the table, and never state such a read as measured fact.\n"
				+ "- Any recommendation or optimisation MUST be phrased as something WE ALREADY DID during the "
				+ "flight, never as future advice — e.g. 'we shifted weight toward the stronger publishers' or 'we "
				+ "concentrated delivery on the best-performing inventory'.\n"
				+ "- NEVER say we blacklisted or paused a tactic; if a tactic underdelivered, say we REDUCED ITS "
				+ "WEIGHT. (Blacklisting individual PUBLISHERS is fine and encouraged — see below.)\n"
				+ "- One of the 4 observations should note that over the flight we blacklisted a large number of "
				+ "publishers (on the order of hundreds to a few thousand) to keep delivery on premium, "
				+ "brand-safe inventory and protect the brand's premium standing. Keep the count qualitative "
				+ "(e.g. 'hundreds of publishers', 'thousands of publishers') — do not fabricate a precise figure.\n"
				+ "- Vary the angle across the 4: volume/reach anchor and the long tail, audience-fit of the mix, "
				+ "premium/brand-suitability incl. the blacklisting work, and how we already steered weight toward "
				+ "the strongest publishers.\n"
				+ "- Analyst tone, no filler, no bullet characters, no markdown.\n\n"
				+ "Return ONLY a JSON object keyed by tactic, each key mapping to an array of exactly 4 strings:\n"
				+ "{\"tactic_1\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== PUBLISHER DATA ===\n" + String.join("\n", blocks);
		return Optional.of(prompt);
	}

	/**
	 * Builds the creative-takeaways prompt for one chunk of tactics, or empty when the chunk carries no
	 * tactic with a filled creative block.
	 *
	 * <p>Both quoted limits are {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the slide's real budgets, for
	 * the same reason {@link #buildPublisherObservationsPrompt}'s is: copy written right up to the budget
	 * has nowhere to go when the truncation safety net runs and gets cut mid-thought, so asking for the
	 * smaller number up front means most bullets never need compressing at all.
	 *
	 * @param inputs        the chunk's tactics, each with its hand-entered creative block
	 * @param brief         free-text campaign brief, used to tie the creative read back to the industry
	 * @param maxChars      the slide's real budget for the three observation bullets
	 * @param recoMaxChars  the slide's real budget for the fourth (recommendation) bullet
	 * @return the prompt requesting a JSON object of {@code "tactic_<n>"} → 4-bullet array, or empty when
	 * every tactic in the chunk has a blank block
	 */
	public Optional<String> buildCreativeTakeawaysPrompt(
			List<CreativeTakeawayInput> inputs, String brief, int maxChars, int recoMaxChars) {
		List<String> blocks = new ArrayList<>();
		for (CreativeTakeawayInput input : inputs) {
			if (input.table() == null || input.table().isEmpty()) {
				continue;
			}
			blocks.add(creativeContextBlock(input));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		int promptLimit = Math.max(1, (int) (maxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		int recoPromptLimit = Math.max(1, (int) (recoMaxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior programmatic media analyst writing the KEY TAKEAWAYS bullets for the "
				+ "'Creative analysis' slide of an end-of-campaign report.\n\n"
				+ "For EACH tactic below, write exactly 4 takeaways about its creative performance.\n\n"
				+ "Rules:\n"
				+ "- Takeaways 1-3 read the creative mix: ONE complete sentence each, at most " + promptLimit
				+ " characters.\n"
				+ "- Ground every takeaway in the numbers given: name real creatives, cite real impressions "
				+ "shares, CTR/VCR and spend, and compare creatives against each other.\n"
				+ "- Vary the angle across takeaways 1-3: the delivery/completion anchor, the engagement "
				+ "leader, and a read on creative format or size (e.g. what a top creative's size implies "
				+ "about device or placement distribution).\n"
				+ "- At most ~20% of each takeaway may go beyond the table — a short, well-established read on "
				+ "why that creative or format suits this campaign's industry or audience. Never invent a "
				+ "metric that is not in the table, and never state such a read as measured fact.\n"
				+ CREATIVE_SMALL_SAMPLE_RULE
				+ "- Takeaway 4 is DIFFERENT: it states an optimisation ALREADY MADE on creative during the "
				+ "flight and the result it produced (e.g. a mid-flight budget shift from one creative to "
				+ "another after a deviation was spotted). At most " + recoPromptLimit + " characters, still "
				+ "one complete sentence, still grounded in the table's creatives and numbers, and still bound "
				+ "by the small-sample and ~20% rules above.\n"
				+ "- Use the tactic's own KPI type when talking about its lead metric.\n"
				+ "- Analyst tone, no filler, no bullet characters, no markdown.\n\n"
				+ "Return ONLY a JSON object keyed by tactic, each key mapping to an array of exactly 4 strings:\n"
				+ "{\"tactic_1\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== CREATIVE DATA ===\n" + String.join("\n", blocks);
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
	 * Builds the geo-insights prompt for one chunk of tactics, or empty when the chunk carries no tactic
	 * with a filled geo block.
	 *
	 * <p>The per-string limit quoted to Claude is {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the slide's
	 * real 140-character budget, for the same reason {@link #buildPublisherObservationsPrompt}'s is: copy
	 * written right up to the budget has nowhere to go when the truncation safety net runs and gets cut
	 * mid-thought, so asking for the smaller number up front means most strings never need compressing.
	 *
	 * <p>Unlike the creative and publisher prompts — whose recommendations must be framed as something
	 * already done during the flight — the fifth geo string is a genuinely forward-looking recommendation:
	 * what we would do next to improve results.
	 *
	 * @param inputs   the chunk's tactics, each with its hand-entered geo block
	 * @param brief    free-text campaign brief, used to tie the geo read back to the audience and goals
	 * @param maxChars the slide's real per-string character budget (shared by the insights and the reco)
	 * @return the prompt requesting a JSON object of {@code "tactic_<n>"} → 5-string array, or empty when
	 * every tactic in the chunk has a blank block
	 */
	public Optional<String> buildGeoInsightsPrompt(List<GeoInsightInput> inputs, String brief, int maxChars) {
		List<String> blocks = new ArrayList<>();
		for (GeoInsightInput input : inputs) {
			if (input.table() == null || input.table().isEmpty()) {
				continue;
			}
			blocks.add(geoContextBlock(input));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		int promptLimit = Math.max(1, (int) (maxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior programmatic media analyst writing the 'WHAT THE MAP TELLS US' bullets "
				+ "and the recommendation for the 'Geo analysis' slide of an end-of-campaign report. You are writing "
				+ "on behalf of the team that ran this campaign, so the tone is confident and complimentary of our "
				+ "own delivery.\n\n"
				+ "For EACH tactic below, write exactly 5 strings: 4 insights about its geographic delivery, then a "
				+ "5th recommendation.\n\n"
				+ "Rules:\n"
				+ "- Strings 1-4 are the insights: ONE complete sentence each, at most " + promptLimit
				+ " characters.\n"
				+ "- Ground every insight in the numbers given: name real markets/geos, cite real impressions and "
				+ "the tactic's lead KPI, compare geos against each other and against the top-geo/most-efficient "
				+ "stats, and read where delivery concentrated.\n"
				+ "- Vary the angle across insights 1-4: the concentration of delivery across the top geos, the "
				+ "geos that over-indexed on the lead KPI (efficient markets), the geos with reach but softer "
				+ "engagement, and the audience/market fit of where delivery landed.\n"
				+ "- At most ~20% of each insight may go beyond the table — a short, well-established read on why a "
				+ "given market fits this campaign's audience, industry or goals (you may draw on the campaign brief "
				+ "and goals for this). Never invent a metric that is not in the table, and never state such a read "
				+ "as measured fact.\n"
				+ "- SMALL-SAMPLE OUTLIERS. A market with few impressions can post a KPI far above the average; "
				+ "treat that as noise on low volume, not as a top market, and judge efficiency alongside volume.\n"
				+ "- String 5 is DIFFERENT: it is a FORWARD-LOOKING recommendation — what we would do next to "
				+ "improve results (e.g. where to open incremental budget, which markets to scale, where to tighten "
				+ "frequency). At most " + promptLimit + " characters, one complete sentence, still grounded in the "
				+ "table's geos and numbers. This one MAY be future advice (the insights and the other slides' "
				+ "recommendations may not).\n"
				+ "- Use the tactic's own KPI type when talking about its lead metric.\n"
				+ "- Analyst tone, no filler, no bullet characters, no markdown.\n\n"
				+ "Return ONLY a JSON object keyed by tactic, each key mapping to an array of exactly 5 strings:\n"
				+ "{\"tactic_1\": [\"...\", \"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== GEO DATA ===\n" + String.join("\n", blocks);
		return Optional.of(prompt);
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
	 * Builds the audience-insights prompt for one chunk of tactics, or empty when the chunk carries no
	 * tactic with a filled audience block.
	 *
	 * <p>The per-string limits quoted to Claude are {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the
	 * slide's real budgets — 256 characters for the key takeaway and 120 for the other three — for the
	 * same reason {@link #buildGeoInsightsPrompt}'s is: copy written right up to the budget gets cut
	 * mid-thought when the truncation safety net runs, so asking for the smaller number up front means
	 * most strings never need compressing.
	 *
	 * <p>The fourth string is a forward-looking recommended action: which age groups and audience
	 * segments to lean into next, tied to the campaign brief. The other three describe what already
	 * happened during the flight.
	 *
	 * @param inputs          the chunk's tactics, each with its hand-entered audience block
	 * @param brief           free-text campaign brief, used to tie the audience read back to the goals
	 * @param takeawayMaxChars the slide's real character budget for the key takeaway
	 * @param shortMaxChars    the slide's real character budget for the other three fields
	 * @return the prompt requesting a JSON object of {@code "tactic_<n>"} → 4-string array, or empty when
	 * every tactic in the chunk has a blank block
	 */
	public Optional<String> buildAudienceInsightsPrompt(
			List<AudienceInsightInput> inputs, String brief, int takeawayMaxChars, int shortMaxChars) {
		List<String> blocks = new ArrayList<>();
		for (AudienceInsightInput input : inputs) {
			if (input.table() == null || input.table().isEmpty()) {
				continue;
			}
			blocks.add(audienceContextBlock(input));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		int takeawayLimit = Math.max(1, (int) (takeawayMaxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		int shortLimit = Math.max(1, (int) (shortMaxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior programmatic media analyst writing the four copy fields on the "
				+ "'Audience analysis' slide of an end-of-campaign report. You are writing on behalf of the team "
				+ "that ran this campaign, so the tone is confident and complimentary of our own delivery.\n\n"
				+ "For EACH tactic below, write exactly 4 strings, in this order:\n"
				+ "1. KEY TAKEAWAY — the single most important read on who this tactic reached, at most "
				+ takeawayLimit + " characters. One or two complete sentences.\n"
				+ "2. WHAT WORKED — the strongest audience result, at most " + shortLimit
				+ " characters, one complete sentence.\n"
				+ "3. WATCH-OUT — a caveat or soft spot in the audience delivery, at most " + shortLimit
				+ " characters, one complete sentence.\n"
				+ "4. RECOMMENDED ACTION — a FORWARD-LOOKING recommendation about which age groups and audience "
				+ "segments to lean into next, at most " + shortLimit + " characters, one complete sentence. This "
				+ "one MAY be future advice; strings 1-3 describe what already happened.\n\n"
				+ "Rules:\n"
				+ "- Ground every string in the numbers given: name the real dominant age groups and the real "
				+ "top segments with their affinity indexes, and read where delivery and engagement concentrated.\n"
				+ "- Focus the recommendation on the MOST EFFECTIVE ages and segments — the buckets with the "
				+ "strongest delivery and the segments with the highest affinity index (100 = campaign average).\n"
				+ "- Tie your read and hypotheses to the campaign brief's audience and goals, but at most ~20% of "
				+ "any string may go beyond the table, and never invent a number that is not in the data.\n"
				+ "- SMALL-SAMPLE OUTLIERS. A segment with a very high affinity index on tiny volume is noise, not "
				+ "a headline; judge it alongside how much of delivery it actually represents.\n"
				+ "- Analyst tone, no filler, no bullet characters, no markdown.\n\n"
				+ "Return ONLY a JSON object keyed by tactic, each key mapping to an array of exactly 4 strings:\n"
				+ "{\"tactic_1\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== AUDIENCE DATA ===\n" + String.join("\n", blocks);
		return Optional.of(prompt);
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
	 * Builds the device-insights prompt for one chunk of tactics, or empty when the chunk carries no
	 * tactic with a filled device block.
	 *
	 * <p>The per-string limits quoted to Claude are {@link #COMPRESSION_PROMPT_BUFFER_RATIO} of the
	 * slide's real budgets — 256 characters for the key takeaway and 120 for the other three — for the
	 * same reason {@link #buildAudienceInsightsPrompt}'s are: copy written right up to the budget gets cut
	 * mid-thought when the truncation safety net runs, so asking for the smaller number up front means
	 * most strings never need compressing.
	 *
	 * <p>The fourth string is a forward-looking recommended action: which devices to lean into next, tied
	 * to the campaign brief. The other three describe what already happened during the flight.
	 *
	 * @param inputs           the chunk's tactics, each with its hand-entered device block
	 * @param brief            free-text campaign brief, used to tie the device read back to the goals
	 * @param takeawayMaxChars the slide's real character budget for the key takeaway
	 * @param shortMaxChars    the slide's real character budget for the other three fields
	 * @return the prompt requesting a JSON object of {@code "tactic_<n>"} → 4-string array, or empty when
	 * every tactic in the chunk has a blank block
	 */
	public Optional<String> buildDeviceInsightsPrompt(
			List<DeviceInsightInput> inputs, String brief, int takeawayMaxChars, int shortMaxChars) {
		List<String> blocks = new ArrayList<>();
		for (DeviceInsightInput input : inputs) {
			if (input.table() == null || input.table().isEmpty()) {
				continue;
			}
			blocks.add(deviceContextBlock(input));
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		int takeawayLimit = Math.max(1, (int) (takeawayMaxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		int shortLimit = Math.max(1, (int) (shortMaxChars * COMPRESSION_PROMPT_BUFFER_RATIO));
		String prompt = "You are a senior programmatic media analyst writing the four copy fields on the "
				+ "'Device breakdown' slide of an end-of-campaign report. You are writing on behalf of the team "
				+ "that ran this campaign, so the tone is confident and complimentary of our own delivery.\n\n"
				+ "For EACH tactic below, write exactly 4 strings, in this order:\n"
				+ "1. KEY TAKEAWAY — the single most important read on how this tactic performed across devices, "
				+ "at most " + takeawayLimit + " characters. One or two complete sentences.\n"
				+ "2. WHAT WORKED — the strongest device result, at most " + shortLimit
				+ " characters, one complete sentence.\n"
				+ "3. WATCH-OUT — a caveat or soft spot in the device delivery, at most " + shortLimit
				+ " characters, one complete sentence.\n"
				+ "4. RECOMMENDED ACTION — a FORWARD-LOOKING recommendation about which devices to lean into "
				+ "next, at most " + shortLimit + " characters, one complete sentence. This one MAY be future "
				+ "advice; strings 1-3 describe what already happened.\n\n"
				+ "Rules:\n"
				+ "- Ground every string in the numbers given: name the real devices, cite real impressions, "
				+ "CTR, completion rate (VCR) and spend, and read where delivery and engagement concentrated.\n"
				+ "- Focus the recommendation on the MOST EFFECTIVE devices — those with the strongest engagement "
				+ "for their share of delivery and spend.\n"
				+ "- CTR does not apply to Connected TV (non-clickable inventory); never treat a missing CTV CTR "
				+ "as a zero or a weakness.\n"
				+ "- Tie your read and hypotheses to the campaign brief's audience and goals, but at most ~20% of "
				+ "any string may go beyond the table, and never invent a number that is not in the data.\n"
				+ "- SMALL-SAMPLE OUTLIERS. A device with a very high rate on tiny volume is noise, not a "
				+ "headline; judge it alongside how much of delivery it actually represents.\n"
				+ "- Analyst tone, no filler, no bullet characters, no markdown.\n\n"
				+ "Return ONLY a JSON object keyed by tactic, each key mapping to an array of exactly 4 strings:\n"
				+ "{\"tactic_1\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== DEVICE DATA ===\n" + String.join("\n", blocks);
		return Optional.of(prompt);
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
