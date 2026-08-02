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
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds Anthropic Messages API prompts and campaign context blocks for Claude batches A/B/C and geo.
 *
 * <p>This is the <strong>end-of-campaign</strong> wording, and it is the default every injection point
 * resolves to. End-of-month runs are served by {@link EomPromptBuilder}, which subclasses this one and
 * overrides the methods whose wording has to differ. EOM wording changes belong there: an edit made
 * here reaches both flavours.
 */
@Component
@Primary
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

	/**
	 * Top-N caveat for the geo prompts, shared by the standalone geo-section prompt and the combined
	 * conclusions prompt so both paths hold the copy to the same claim.
	 *
	 * <p>The geo table on the "Breakdowns" tab holds a handful of the tactic's strongest markets, not every
	 * market it ran in — the block has room for a top list, and that is what the user fills in. Left unsaid,
	 * Claude counts the rows and states the count as the campaign's footprint ("delivery ran across five
	 * markets"), which is the publisher slide's old failure mode in a different table and is wrong on the
	 * deck rather than merely thin. The "Markets activated" stat above the table is the one figure that does
	 * carry total coverage, so the rule points the copy at it and forbids inferring a total from the rows;
	 * when the user left that stat blank there is no total to state at all.
	 */
	private static final String GEO_TOP_MARKETS_RULE =
			"The table lists only this tactic's TOP markets, NOT every market it ran in: never state or imply a "
					+ "total market count from the number of rows, and never read the list as the campaign's full "
					+ "geographic footprint. The 'Markets activated' stat above the table is the only figure for total "
					+ "coverage — cite that when it is present, and when it is absent write about the listed markets "
					+ "without claiming how many ran in total. Shares of delivery are shares of the listed markets, "
					+ "not of the tactic.";

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
						+ batchAProposalOverviewSpec()
						+ batchAStrategicInsightsSpec()
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
	 * The {@code proposal_overview} field spec for the full Batch A prompt: two closing sentences summing up
	 * why and how the campaign ran. Split out so the end-of-month builder can restate what the field asks for
	 * without duplicating the audience-field lines around it.
	 *
	 * @return the field spec line, newline-terminated
	 */
	String batchAProposalOverviewSpec() {
		return "  \"proposal_overview\": string,   // Exactly 2 complete sentences. Past tense, no line breaks, "
				+ "no bullets. Sentence 1: why the campaign ran — client objective + target audience. Sentence 2: "
				+ "how it ran — tactic mix + geo + flight period. Name the actual tactics, actual audience, actual "
				+ "geo. No character limit — write both sentences completely.\n";
	}

	/**
	 * The {@code strategic_insights} field spec for the full Batch A prompt: four closing-tense insights into
	 * why the campaign's approach made sense. Split out for the same reason as {@link #batchAProposalOverviewSpec}.
	 *
	 * @return the field spec lines, newline-terminated
	 */
	String batchAStrategicInsightsSpec() {
		return "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": "
				+ "string}.\n"
				+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD "
				+ "LIMIT.\n"
				+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
				+ "                                // Each overview = strategic intention/approach + WHY this "
				+ "choice made sense for THIS client/campaign. Unique angles, past tense, Business English. No "
				+ "filler.\n";
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
		Optional<String> context = strategicNarrativeContext(data, brief);
		if (context.isEmpty()) {
			return Optional.empty();
		}
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
						+ "Campaign data:\n" + context.get();
		return Optional.of(prompt);
	}

	/**
	 * Assembles the campaign-data context block the strategic-narrative prompt ends with — the brief, the
	 * campaign plan, the per-tactic performance lines and the audience tab, in that order.
	 *
	 * <p>Split out of {@link #buildBatchStrategicNarrativePrompt} so {@link EomPromptBuilder} can put the
	 * same context under different instructions instead of duplicating sixty lines of assembly. It produces
	 * exactly the text it always did — the end-of-campaign prompt is unchanged by the split.
	 *
	 * @param data  parsed campaign plan and per-tactic performance
	 * @param brief free-text campaign brief, treated as empty when {@code null}
	 * @return the assembled context block, or empty when there is nothing to describe
	 */
	Optional<String> strategicNarrativeContext(CampaignData data, String brief) {
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
		return Optional.of(String.join("\n\n", ctx));
	}

	/**
	 * The seam the strategic-narrative call goes through, so a report flavour can answer with its own
	 * instructions and its own extra context without the caller knowing which flavour it holds.
	 *
	 * <p>End-of-campaign wording ignores {@code monthlyPivots} — a finished campaign is summed up as a
	 * whole, not compared month against month — and this base implementation is therefore exactly
	 * {@link #buildBatchStrategicNarrativePrompt}. {@link EomPromptBuilder} overrides it.
	 *
	 * @param data          parsed campaign plan and per-tactic performance
	 * @param brief         free-text campaign brief, treated as empty when {@code null}
	 * @param monthlyPivots tactic number to its monthly pacing series read back from the reviewed sheet;
	 *                      unused here, may be {@code null} or empty
	 * @return the strategic-narrative prompt, or empty when no context block could be built
	 */
	Optional<String> strategicNarrativePrompt(
			CampaignData data, String brief, Map<Integer, Pivot> monthlyPivots) {
		return buildBatchStrategicNarrativePrompt(data, brief);
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
		// An audio tactic's completion metric is ACR everywhere else in the deck and the sheet; sending it here
		// as "VCR" is how Claude came to write VCR into an audio tactic's creative copy.
		String completionLabel = completionRateLabel(input.tacticName());
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName());
		if (input.kpiType() != null && !input.kpiType().isBlank()) {
			block.append(" (lead KPI: ").append(input.kpiType()).append(')');
		}
		block.append('\n');
		appendCreativeStat(block, "Creatives live", table.creativesLive());
		appendCreativeStat(block, "Best CTR/" + completionLabel, table.bestKpi());
		appendCreativeStat(block, "Avg CTR/" + completionLabel, table.avgKpi());
		appendCreativeStat(block, "Top creative", table.topCreative());
		block.append("Creative | Impressions | CTR | ").append(completionLabel).append(" | Spend\n");
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
		// Same reason as creativeContextBlock: an audio tactic's completion metric is ACR, not VCR.
		String completionLabel = completionRateLabel(input.tacticName());
		StringBuilder block = new StringBuilder();
		block.append("tactic_").append(input.tacticNum()).append(" — ").append(input.tacticName()).append('\n');
		appendCreativeStat(block, "Highest CTR", table.highestCtr());
		appendCreativeStat(block, "Best completion (" + completionLabel + ")", table.bestCompletion());
		appendCreativeStat(block, "Devices tracked", table.devicesTracked());
		appendCreativeStat(block, "Top device", table.topDevice());
		appendCreativeStat(block, "Top device % of impressions", table.topDeviceImpressionsPct());
		if (!table.rows().isEmpty()) {
			block.append("Device | Impressions | CTR | ").append(completionLabel).append(" | Spend\n");
			for (DeviceRow row : table.rows()) {
				block.append(row.device()).append(" | ").append(row.impressions())
						.append(" | ").append(row.ctr()).append(" | ").append(row.vcr())
						.append(" | ").append(row.spend()).append('\n');
			}
		}
		return block.toString();
	}

	/**
	 * Builds the Step-2 per-tactic conclusions prompt for one chunk of tactics: for each tactic it asks for the
	 * {@code {{tactic n overview}}} narrative and nothing else. Every breakdown section's slide copy is written
	 * by that section's own dedicated per-tactic call, so this prompt neither describes those fields nor accepts
	 * them.
	 *
	 * <p>The instruction block and the shared campaign context sit before the {@link
	 * AnthropicMessagesClient#CACHE_BREAKPOINT}, so they are cached once and re-read cheaply on every following
	 * per-tactic call; only the tactic's own performance data follows the marker.
	 *
	 * @param data       parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param tacticNums the 1-based tactic numbers to cover in this call
	 * @param brief      free-text campaign brief the conclusions must stay faithful to
	 * @return the conclusions prompt, or empty when the chunk carries no tactic and no data
	 */
	public Optional<String> buildTacticConclusionsPrompt(
			CampaignData data, List<Integer> tacticNums, String brief) {
		return tacticConclusionsPrompt(data, tacticNums, brief, Map.of());
	}

	/**
	 * The seam the per-tactic conclusions call goes through, so a report flavour can answer with its own
	 * instructions and its own extra per-tactic context without the caller knowing which flavour it holds.
	 *
	 * <p>The assembly is shared by both flavours; what each one supplies is the instruction block (through
	 * {@link #conclusionsRole()}, {@link #conclusionPrincipleList()} and {@link #overviewSpec()}) and each
	 * tactic's data block (through {@link #tacticConclusionDataBlock(int, Tactic, Pivot)}). End-of-campaign
	 * wording ignores {@code dailyPivots}: a finished tactic is summed up against its plan, not read off a
	 * pacing curve.
	 *
	 * @param data        parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param tacticNums  the 1-based tactic numbers to cover in this call
	 * @param brief       free-text campaign brief the conclusions must stay faithful to
	 * @param dailyPivots tactic number to its daily pacing series; unused here, may be {@code null} or empty
	 * @return the conclusions prompt, or empty when the chunk carries no tactic and no data
	 */
	Optional<String> tacticConclusionsPrompt(
			CampaignData data, List<Integer> tacticNums, String brief, Map<Integer, Pivot> dailyPivots) {
		if (tacticNums == null || tacticNums.isEmpty() || data == null || data.tactics() == null) {
			return Optional.empty();
		}
		Map<Integer, Pivot> daily = dailyPivots == null ? Map.of() : dailyPivots;
		List<String> dataBlocks = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			Tactic tactic = data.tactics().get(tacticNum);
			if (tactic == null) {
				continue;
			}
			dataBlocks.add(tacticConclusionDataBlock(tacticNum, tactic, daily.get(tacticNum)));
		}
		if (dataBlocks.isEmpty()) {
			return Optional.empty();
		}

		String prompt = conclusionsInstruction()
				+ campaignContextForConclusions(data, brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC DATA ===\n"
				+ String.join("\n\n", dataBlocks);
		return Optional.of(prompt);
	}

	/**
	 * Renders the instruction half of the conclusions prompt: the analyst framing, the shared analytical
	 * principles and the {@code {{tactic n overview}}} spec. Breakdown-section copy is not asked for here — each
	 * section is written by its own dedicated per-tactic call — so the reply carries exactly one field.
	 *
	 * @return the instruction block, ending in a blank line before the campaign context
	 */
	String conclusionsInstruction() {
		return conclusionsRole()
				+ "For EACH tactic below, return a JSON object carrying exactly ONE field: \"overview\".\n\n"
				+ conclusionPrinciples()
				+ "=== overview ===\n"
				+ overviewSpec()
				+ "\n"
				+ "Return ONLY a JSON object keyed by tactic, e.g.:\n"
				+ "{\"tactic_1\": {\"overview\": \"...\"}, \"tactic_2\": {\"overview\": \"...\"}}\n"
				+ "Produce one key for EVERY tactic in the data below — none skipped, none invented.\n"
				+ conclusionsOutputRules();
	}

	/**
	 * The opening role line of the conclusions instruction: who is writing and what the report is. Split out
	 * as its own method so the end-of-month builder can restate the report type without copying the rest of
	 * the instruction block.
	 *
	 * @return the role line, ending in a blank line
	 */
	String conclusionsRole() {
		return "You are a senior digital media analyst writing per-tactic conclusions for a post-campaign report.\n\n";
	}

	/**
	 * The analytical principles both conclusions instructions open with, shared so the overview written in the
	 * overview-only shape is held to exactly the same standard as one written alongside its sections.
	 *
	 * @return the principles block, newline-terminated
	 */
	String conclusionPrinciples() {
		return conclusionPrincipleList() + "\n";
	}

	/**
	 * The numbered principle list itself, without the blank line that closes the block. Split out so a flavour
	 * can append a principle of its own to the end of the list rather than after the blank line.
	 *
	 * @return the numbered principles, the last one newline-terminated
	 */
	String conclusionPrincipleList() {
		return "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
				+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION: state what happened, why (a specific cause), "
				+ "and what it means — as one flowing statement, never labelled sections.\n"
				+ "2. INTERPRET, NEVER ENUMERATE. The reader can see the numbers; explain what they mean.\n"
				+ "3. 'SO WHAT' IS MANDATORY: every metric cited is followed by its business consequence.\n"
				+ "4. NO GENERIC LANGUAGE: every sentence is specific to THIS campaign's numbers and audience.\n"
				+ "5. NAME THE CAUSE for strong or soft performance.\n"
				+ METRIC_DEVIATION_RULE
				+ LEARNING_PHASE_RULE;
	}

	/**
	 * The {@code {{tactic n overview}}} field spec, shared by both conclusions instructions so the field is
	 * asked for in identical words whichever shape the call takes.
	 *
	 * @return the overview spec, newline-terminated
	 */
	String overviewSpec() {
		return "MAX 190 CHARACTERS, ending on a complete word and sentence. Structure: [what the tactic "
				+ "delivered vs plan] + [WHY it performed as it did] + [business so-what]. Past tense, business "
				+ "English, max 2 sentences, no bullets. " + overviewFocusMetrics();
	}

	/**
	 * The metric-priority sentence closing the overview spec: which figures the overview leads on for each
	 * tactic type. Split out so a flavour can rewrite the surrounding spec without restating the metric map.
	 *
	 * @return the focus-metric sentence, newline-terminated
	 */
	String overviewFocusMetrics() {
		return "Focus metrics by tactic type: Display→Imps+CTR; "
				+ "Video/Pre-roll→Imps+CTR+VCR; CTV/OTT→Imps+VCR; Audio→Completions.\n";
	}

	/**
	 * The closing output rules shared by both conclusions instructions.
	 *
	 * @return the rules line, ending in a blank line
	 */
	String conclusionsOutputRules() {
		return "Rules: return ONLY the JSON (no markdown/backticks); analyst tone, no bullet characters; "
				+ "do NOT invent metrics; every string ends on a complete sentence within its limit; English.\n\n";
	}

	/**
	 * Builds the per-tactic audience-section pilot prompt: a small, self-contained call that asks for ONLY the
	 * four "Audience analysis" slide strings as a JSON object of exactly four keyed items, in slide order. Unlike
	 * the campaign-wide prompts it carries a single tactic's audience block, so the reply is small and
	 * every field it does carry can be read back on its own key — see {@link #sectionObjectRules}.
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
				"You are a senior digital media analyst writing the 'Audience analysis' slide for ONE tactic in "
						+ sectionReportKind() + ".\n\n"
						+ sectionPrinciples()
						+ "Ground in real age groups and top segments with affinity indexes (100 = campaign average); "
						+ "treat high-affinity low-volume segments as noise.\n\n"
						+ "Return the four strings in THIS order:\n"
						+ "1) KEY TAKEAWAY (who this tactic reached), at most " + takeawayPrompt + " characters;\n"
						+ "2) WHAT WORKED, at most " + shortPrompt + " characters;\n"
						+ "3) WATCH-OUT, at most " + shortPrompt + " characters;\n"
						+ "4) RECOMMENDED ACTION — FORWARD-LOOKING, which age groups and segments to lean into next, at "
						+ "most " + shortPrompt + " characters.\n\n"
						+ sectionObjectRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[AUDIENCE ANALYSIS]\n" + audienceContextBlock(input);
		return Optional.of(prompt);
	}

	/**
	 * Builds the per-tactic publisher-section prompt: a small, self-contained call that asks for ONLY the four
	 * "Top Publishers" slide observations as a JSON object of exactly four keyed items. Single-tactic and
	 * keyed like {@link #buildAudienceSectionPrompt}, so a partial reply still yields the fields it carries.
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
				"You are a senior digital media analyst writing the 'Top Publishers' slide for ONE tactic in "
						+ sectionReportKind() + ", on behalf of the team that ran the campaign (confident, complimentary "
						+ "of our own delivery).\n\n"
						+ sectionPrinciples()
						+ "Return the four observations in THIS order, each ONE complete sentence:\n"
						+ "1) VOLUME AND REACH — where delivery concentrated across the named publishers and how that "
						+ "head compares with the long tail, at most " + prompt + " characters;\n"
						+ "2) AUDIENCE FIT — why these publishers matched the audience we were chasing (we run an "
						+ "AUDIENCE-FIRST approach: we chase the audience, not sites), at most " + prompt
						+ " characters;\n"
						+ "3) PREMIUM AND BRAND SUITABILITY — state that WE BLACKLISTED a large number of PUBLISHERS "
						+ "(hundreds to a few thousand, kept qualitative — never a precise count) to hold delivery on "
						+ "premium, brand-safe inventory, at most " + prompt + " characters;\n"
						+ "4) STEERING WEIGHT — an optimisation WE ALREADY MADE toward the strongest publishers and what "
						+ "it produced, at most " + prompt + " characters.\n"
						+ "Ground every observation in the numbers: name real publishers from the table and cite their "
						+ "real shares/impressions. The table lists only this tactic's top publishers out of thousands; "
						+ "the HEAD VS LONG TAIL line under it states the exact share of delivery they carry — cite that "
						+ "share rather than estimating coverage, and never state anything the table does not show as a "
						+ "measured fact. Every optimisation is phrased as something WE ALREADY DID (e.g. 'we shifted "
						+ "weight toward stronger publishers'); never say we blacklisted or paused a TACTIC (say we "
						+ "REDUCED ITS WEIGHT).\n\n"
						+ sectionObjectRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[TOP PUBLISHERS]\n" + publisherContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic creative-section prompt: a JSON object of exactly four keyed "Creative analysis"
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
				"You are a senior digital media analyst writing the 'Creative analysis' slide for ONE tactic in "
						+ sectionReportKind() + ".\n\n"
						+ sectionPrinciples()
						+ "Return the four strings in THIS order. Takeaways 1-3 read the creative mix: ONE sentence "
						+ "each, at most " + prompt + " characters, grounded in the numbers (name real creatives, cite "
						+ "impressions shares, CTR and the completion rate exactly as the table below labels it — VCR "
						+ "for video, ACR for audio — and spend). Vary angles: delivery/completion anchor, engagement "
						+ "leader, and a read on creative format/size. " + CREATIVE_SMALL_SAMPLE_RULE
						+ creativeOptimisationRule("String 4", recoPrompt) + "\n"
						+ sectionObjectRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName()
						+ kpiSuffix(input.kpiType()) + " ===\n"
						+ "[CREATIVE ANALYSIS]\n" + creativeContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic geo-section prompt: a JSON object of exactly five keyed "Geo analysis" strings — four
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
						+ "slide of ONE tactic in " + sectionReportKind() + ".\n\n"
						+ sectionPrinciples()
						+ "Return the five strings in THIS order. Strings 1-4 are insights: ONE sentence each, at most "
						+ prompt + " characters, grounded in real markets/geos and the lead KPI; vary angles: "
						+ "concentration across top geos, efficient (over-indexing) markets, reach with softer "
						+ "engagement, and audience/market fit. Treat high-KPI low-volume markets as noise. String 5 is "
						+ "DIFFERENT — a FORWARD-LOOKING recommendation (where to open budget, which markets to scale), "
						+ "at most " + prompt + " characters, still grounded in the table. "
						+ GEO_TOP_MARKETS_RULE + "\n\n"
						+ sectionObjectRules(5)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName()
						+ kpiSuffix(input.kpiType()) + " ===\n"
						+ "[GEO ANALYSIS]\n" + geoContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Builds the per-tactic device-section prompt: a JSON object of exactly four keyed "Device breakdown" strings,
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
				"You are a senior digital media analyst writing the 'Device breakdown' slide for ONE tactic in "
						+ sectionReportKind() + ".\n\n"
						+ sectionPrinciples()
						+ "Return the four strings in THIS order:\n"
						+ "1) KEY TAKEAWAY (performance across devices), at most " + takeawayPrompt + " characters;\n"
						+ "2) WHAT WORKED, at most " + shortPrompt + " characters;\n"
						+ "3) WATCH-OUT, at most " + shortPrompt + " characters;\n"
						+ "4) RECOMMENDED ACTION — FORWARD-LOOKING, which devices to lean into next, at most "
						+ shortPrompt + " characters.\n"
						+ "Ground in real devices (impressions, CTR, spend and the completion rate exactly as the "
						+ "table below labels it — VCR for video, ACR for audio). CTR does not apply to Connected TV — "
						+ "never treat a missing CTV CTR as a zero or weakness. Treat high-rate low-volume devices as "
						+ "noise.\n\n"
						+ sectionObjectRules(4)
						+ campaignContextForConclusions(data, brief) + "\n\n"
						+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC: " + input.tacticName() + " ===\n"
						+ "[DEVICE BREAKDOWN]\n" + deviceContextBlock(input);
		return Optional.of(text);
	}

	/**
	 * Names the kind of report the per-tactic breakdown slides belong to, article included, as it reads
	 * inside every section prompt's opening line.
	 *
	 * <p>This is the only end-of-campaign marker in the five section prompts: everything else they say —
	 * the analytical principles, the field order, the character budgets, the grounding rules — is about
	 * reading one tactic's breakdown table and holds whether or not the flight has ended. The figures those
	 * tables carry are described by the shared campaign context block, which each flavour supplies.
	 *
	 * @return the report-kind phrase, with its leading article and no trailing punctuation
	 */
	String sectionReportKind() {
		return "a post-campaign report";
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
	 * Builds the "optimisation already made" instruction for the creative slide's last string, shared by the
	 * creative-section prompt and the creative rule text it shares, so both licence the same inference
	 * in the same words.
	 *
	 * <p>The slot asks what was changed on creative mid-flight and what it achieved, but nothing in the data
	 * records that: a creative table carries performance, not a change log. Left unsaid, the ask collides with
	 * the surrounding "do NOT invent" rules, and the model resolves the collision the wrong way — a hedge, a
	 * generic line, or a complaint about missing data in a slot the slide expects copy in. Naming the one thing
	 * it may reconstruct (the action) while holding every creative name and every number to the table is what
	 * keeps the string both filled and honest.
	 *
	 * @param lead  how the instruction names the slot, e.g. {@code "String 4"} or {@code "Takeaway 4"}
	 * @param limit character budget quoted to Claude for this string
	 * @return the optimisation instruction, newline-terminated
	 */
	String creativeOptimisationRule(String lead, int limit) {
		return lead + " states an optimisation ALREADY MADE on creative during the flight and its result, at most "
				+ limit + " characters. The data carries NO change log, so this ONE string is EXPECTED to be "
				+ "reconstructed rather than quoted: infer the most plausible optimisation the numbers imply — "
				+ "shifting weight toward the strongest creative, retiring an under-delivering size or format, "
				+ "refreshing worn creative, rebalancing spend across sizes — and state it in past tense as "
				+ "something WE DID. That is expected here and is NOT a data problem: never hedge it, never say the "
				+ "change log is missing, never leave it generic. Constraints: every creative you name and every "
				+ "number you cite must come from the table (never invent a metric), the result you claim must be "
				+ "consistent with the table's figures, and the action must obey the small-sample and budget-shift "
				+ "rules above. Use the tactic's own KPI type for its lead metric.\n";
	}

	/**
	 * The shared closing rules block for a per-section prompt: demand ONLY a JSON object carrying one
	 * {@code field_N} key per slide field, in the order described above.
	 *
	 * <p>A keyed object is the shape the calls that never fail already use — the tactic overviews, the campaign
	 * results and the per-tactic thoughts all return one. It is also the shape the reply can be read
	 * <em>partially</em> from: a named key is still findable when a neighbouring one is missing or malformed,
	 * where a bare array loses every field the moment its length is wrong. The earlier keyless-array contract
	 * bought positional strictness at the price of an all-or-nothing reply, and the strictness was never worth
	 * a blank slide.
	 *
	 * @param count the number of slide fields the object must carry, keyed {@code field_1}..{@code field_count}
	 * @return the closing rules block, newline-terminated
	 */
	String sectionObjectRules(int count) {
		StringBuilder example = new StringBuilder("{");
		for (int i = 1; i <= count; i++) {
			example.append(i == 1 ? "" : ", ").append("\"field_").append(i).append("\": \"...\"");
		}
		example.append('}');
		return "OUTPUT FORMAT — follow exactly:\n"
				+ "- Reply with ONLY a JSON object carrying EXACTLY these " + count + " keys, whose values are the "
				+ count + " strings described above IN THAT ORDER: " + example + "\n"
				+ "- Every value is a non-empty string. No markdown, no backticks, no arrays, no extra keys, no "
				+ "nesting.\n"
				+ "- Write NO preamble, explanation, reasoning, or commentary before or after the object — the "
				+ "object is the entire reply.\n"
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
	 * Renders one tactic's data block for {@link #buildTacticConclusionsPrompt}: the tactic header and the
	 * performance metrics the overview is written from. Breakdown tables are not carried here — each section's
	 * own per-tactic call renders them.
	 *
	 * @param tacticNum the 1-based tactic number this block belongs to
	 * @param tactic    the tactic's parsed performance metrics for the overview line
	 * @return the tactic's {@code tactic_<n>} data block
	 */
	String tacticConclusionDataBlock(int tacticNum, Tactic tactic) {
		return "tactic_" + tacticNum + " — " + tactic.name() + "\n"
				+ "PERFORMANCE (for overview): " + tacticMetricLine(tactic) + "\n";
	}

	/**
	 * The per-tactic data-block seam, so a flavour can put its own extra context behind one tactic's header.
	 *
	 * <p>End-of-campaign wording adds nothing to the plan-vs-actual line and therefore ignores the tactic's
	 * daily pacing series; {@link EomPromptBuilder} overrides this to append it.
	 *
	 * @param tacticNum the 1-based tactic number this block belongs to
	 * @param tactic    the tactic's parsed performance metrics for the overview line
	 * @param daily     the tactic's daily pacing series; unused here, may be {@code null}
	 * @return the tactic's {@code tactic_<n>} data block
	 */
	String tacticConclusionDataBlock(int tacticNum, Tactic tactic, Pivot daily) {
		return tacticConclusionDataBlock(tacticNum, tactic);
	}

	/**
	 * Renders one tactic's publisher table as prompt context, mirroring the standalone publisher prompt's
	 * inline block.
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
		block.append(publisherCoverageLine(input));
		return block.toString();
	}

	/**
	 * Renders the head-vs-long-tail line that closes a publisher block: what the listed rows carry against the
	 * tactic's whole delivery, as impressions and as a share.
	 *
	 * <p>This is the figure the prompt tells Claude to cite instead of estimating how much of the campaign the
	 * table covers — a real number from the sheet rather than a licence to guess. It is omitted whenever the
	 * arithmetic would be untrustworthy: either total is unknown, or the rows add up to more than the tactic
	 * delivered (a mistyped cell), in which case the copy is better off saying nothing about coverage.
	 *
	 * @param input the tactic's publisher input carrying both impression totals
	 * @return the coverage line, newline-terminated, or an empty string when it cannot be stated
	 */
	String publisherCoverageLine(PublisherObservationInput input) {
		long head = input.headImpressions();
		long total = input.tacticImpressions();
		if (head <= 0 || total <= 0 || head > total) {
			return "";
		}
		long share = Math.round(100.0 * head / total);
		return "HEAD VS LONG TAIL: these " + input.rows().size() + " publishers carry " + fmt.intGroup(head)
				+ " of the tactic's " + fmt.intGroup(total) + " impressions (" + share + "% of its delivery); "
				+ "the remaining " + (100 - share) + "% sits in a long tail of thousands of unlisted publishers.\n";
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
	 * Builds the shared campaign context (brief, plan and overall totals) for the conclusions prompt,
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
		String prompt = tacticThoughtsRole()
				+ "Write EXACTLY 4 short analytical thoughts about THIS tactic's performance, each 1-2 sentences, "
				+ "past tense, client-friendly, at most " + promptLimit + " characters.\n"
				+ tacticThoughtsAngles()
				+ "Ground every thought in the conclusions given; never invent a metric. Analyst tone, no bullet "
				+ "characters, no markdown.\n\n"
				+ "Return ONLY a JSON object: {\"thoughts\": [\"...\", \"...\", \"...\", \"...\"]}\n\n"
				+ "=== CAMPAIGN BRIEF ===\n" + (brief == null ? "" : brief) + "\n\n"
				+ AnthropicMessagesClient.CACHE_BREAKPOINT + "=== TACTIC CONCLUSIONS ===\n" + dataBlock;
		return Optional.of(prompt);
	}

	/**
	 * States who is writing and what the report is, for the Step-3 per-tactic thoughts call. Split out as its
	 * own method so the end-of-month builder can restate the report type without copying the rest of the
	 * instruction block.
	 *
	 * @return the role paragraph, ending in a blank line
	 */
	String tacticThoughtsRole() {
		return "You are a senior digital media analyst writing the four 'Thoughts on tactic performance' "
				+ "bullets for ONE tactic's slide in an end-of-campaign report. You are writing on behalf of the team "
				+ "that ran this campaign, so the tone is confident and complimentary of our own delivery.\n\n";
	}

	/**
	 * The four angles the thoughts must vary across. Split out so a flavour can change what a mid-flight
	 * angle asks for without restating the "synthesise, don't restate" framing or the four-thought count.
	 *
	 * @return the angles paragraph, newline-terminated
	 */
	String tacticThoughtsAngles() {
		return "Synthesise across the tactic's overview AND its breakdown conclusions below — do not just restate "
				+ "one of them. Vary the 4 angles: (1) the tactic's headline result and WHY; (2) what worked best "
				+ "across its breakdowns (publishers / creative / geo / audience / device); (3) a watch-out or nuance; "
				+ "(4) the forward-looking opportunity for this tactic.\n";
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
				campaignResultsRole()
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
						+ resultsOverviewsSpec(groupNums, groupRanges, overviewPrompt)
						+ thoughtsOnPerformanceSpec(thoughtsPrompt)
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
	 * The opening role line of the campaign-results instruction: who is writing and what the report is. Split
	 * out as its own method so the end-of-month builder can restate the report type without copying the rest
	 * of the instruction block.
	 *
	 * @return the role line, ending in a blank line
	 */
	String campaignResultsRole() {
		return "You are a senior digital media analyst writing the campaign-level copy for a post-campaign "
				+ "report.\n\n";
	}

	/**
	 * The {@code results_overviews} field spec: one two-sentence summary per tactic group, keyed by group
	 * number. Split out so a flavour can change what those two sentences say without restating the key shape
	 * the reply is parsed against.
	 *
	 * @param groupNums     comma-separated group numbers the reply must carry a key for
	 * @param groupRanges   the {@code group N to tactics a,b,c} mapping quoted to Claude
	 * @param overviewLimit the buffered character budget of one group's overview
	 * @return the field spec line, newline-terminated
	 */
	String resultsOverviewsSpec(String groupNums, String groupRanges, int overviewLimit) {
		return "  \"results_overviews\": { // Keyed by tactic-group number as strings (" + groupNums + "). "
				+ "One entry PER GROUP (" + groupRanges + "). Each value covers ONLY that group's tactics, "
				+ "EXACTLY 2 SENTENCES, past tense, ≤" + overviewLimit
				+ " chars: sentence 1 = overall result + key metric vs "
				+ "plan + a cause; sentence 2 = which tactic(s) led vs lagged, each with a reason. "
				+ tacticNamingRule() + groupNamingRule()
				+ "Client-facing tone: lead with what was achieved, frame gaps as external constraints. },\n";
	}

	/**
	 * The rule keeping campaign-level copy on a tactic's channel name rather than its slide number, shared so
	 * both flavours' results overviews hold the copy to it in identical words.
	 *
	 * @return the naming rule, space-terminated so it reads inline within a field spec
	 */
	String tacticNamingRule() {
		return "Refer to a tactic by the display name given after its number below (e.g. 'CTV'), NEVER as "
				+ "'Tactic 7'; when a tactic has no name, describe it without a label. ";
	}

	/**
	 * The rule keeping a group's overview off the internal group number: the grouping is a deck-layout device
	 * (one summary slide per seven tactics) and means nothing to the client reading the slide, so copy opening
	 * with "Group 1 …" reads as a leaked internal label.
	 *
	 * <p>Shared by the results-overview spec and the alignment schema so both passes are held to it in the
	 * same words; without it the alignment pass can reintroduce the label the first pass was told to avoid.
	 *
	 * @return the naming rule, space-terminated so it reads inline within a field spec
	 */
	String groupNamingRule() {
		return "NEVER name the group in the text — no 'Group 1', 'group 2', 'this group', 'the group': the "
				+ "grouping is an internal slide-layout device. Write about the tactics themselves, or about "
				+ "the campaign/these tactics collectively (e.g. 'CTV/OTT, GeoFencing and Native Video "
				+ "delivered …' or 'These three tactics …'). ";
	}

	/**
	 * The {@code thoughts_on_performance} field spec: the four campaign-level paragraphs and what each one
	 * covers. Split out so a flavour can change what a paragraph is about without restating the joined-by-pipe
	 * shape the reply is parsed against.
	 *
	 * @param thoughtsLimit the buffered character budget of all four paragraphs together
	 * @return the field spec line, newline-terminated
	 */
	String thoughtsOnPerformanceSpec(int thoughtsLimit) {
		return "  \"thoughts_on_performance\": string, // EXACTLY 4 short paragraphs joined by \" | \" "
				+ "(exactly 3 separators), ≤" + thoughtsLimit
				+ " chars total. (1) best tactic/channel + WHY; (2) why the "
				+ "campaign succeeded — name the mechanism; (3) one creative/format insight; (4) an efficiency "
				+ "or reach insight.\n";
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
	 * The editor framing the alignment prompt opens with: who is doing the pass and why the draft needs it.
	 *
	 * @return the framing paragraph, ending in a blank line
	 */
	String alignEditorRole() {
		return "You are the lead editor on a client-facing digital-media campaign report. Several sections were "
				+ "drafted independently by different analysts, so they repeat, drift, and sometimes explain the "
				+ "same result with different causes.\n\n";
	}

	/**
	 * The five numbered editorial rules of the alignment pass: one storyline, no contradictions, faithful to the
	 * brief, invent nothing, keep the shape.
	 *
	 * @return the rules block, ending in a blank line
	 */
	String alignJobRules() {
		return "YOUR JOB — editorial alignment, NOT reanalysis:\n"
				+ "1. ONE STORYLINE. Decide the single most important story of this campaign from the draft, then "
				+ "make every field a consistent facet of it. The proposal sets it up; the results overviews and "
				+ "thoughts pay it off; the strategic insights and frequency copy reinforce it.\n"
				+ alignSharedRules()
				+ "5. KEEP THE SHAPE. Return the SAME fields with the SAME counts and character limits as below. "
				+ "Past tense, Business English, no filler, no labels inside the copy.\n\n";
	}

	/**
	 * Rules 2 to 4 of the alignment pass — no contradictions, faithful to the brief, invent nothing — which are
	 * about editing discipline rather than about the report's tense, and are therefore shared verbatim by both
	 * flavours.
	 *
	 * @return rules 2 to 4, the last one newline-terminated
	 */
	String alignSharedRules() {
		return "2. NO CONTRADICTIONS. If two fields explain the same outcome with different causes, pick the "
				+ "best-supported cause and use it consistently. Name the hero tactic, the laggard, and the "
				+ "audience the SAME way everywhere.\n"
				+ "3. STAY FAITHFUL TO THE BRIEF. Every claim must be consistent with the campaign brief above. "
				+ "Drop or correct anything the draft says that the brief contradicts.\n"
				+ "4. DO NOT INVENT. Use only facts already present in the draft or the read-only breakdown "
				+ "signals. You are tightening and reconciling existing copy, not adding new data. If a field is "
				+ "already good and consistent, return it essentially unchanged.\n";
	}

	/**
	 * An optional context block naming the window the report covers, prepended to the alignment context.
	 *
	 * <p>Empty for end-of-campaign: the draft it aligns already names the flight period, and a second statement
	 * of it would only give the model something to contradict.
	 *
	 * @param reportingPeriod the window the report covers, as shown on the deck; may be {@code null} or blank
	 * @return the block, or an empty string
	 */
	String alignReportingPeriodBlock(String reportingPeriod) {
		return "";
	}

	/**
	 * The aligned {@code proposal_overview} schema line: what the field must still be after the pass.
	 *
	 * @return the schema line, newline-terminated
	 */
	String alignProposalSchema() {
		return "  \"proposal_overview\": string,   // Exactly 2 sentences, past tense, no line breaks. "
				+ "≤400 chars. Keep every named tactic/audience/geo from the draft; only sharpen wording.\n";
	}

	/**
	 * The aligned {@code results_overviews} schema line.
	 *
	 * @param groupKeys comma-separated group numbers the reply must carry a key for
	 * @return the schema line, newline-terminated
	 */
	String alignResultsOverviewsSchema(String groupKeys) {
		return "  \"results_overviews\": object,     // Keyed by group number as strings ("
				+ groupKeys + "). One entry per key listed, no more, no fewer. Each: EXACTLY "
				+ "2 sentences, past tense, ≤380 chars. Must pay off the same storyline with its own group's "
				+ "numbers. " + groupNamingRule() + "\n";
	}

	/**
	 * The aligned {@code thoughts_on_performance} schema line.
	 *
	 * @param thoughtCount how many thought strings the draft carried, which the reply must match exactly
	 * @return the schema line, newline-terminated
	 */
	String alignThoughtsSchema(int thoughtCount) {
		return "  \"thoughts_on_performance\": array, // EXACTLY " + thoughtCount
				+ " strings (≤220 chars each), same order as the draft. Together they must read as the campaign "
				+ "headline expanded — no contradictions with the overviews above.\n";
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
		return alignPrompt(strategic, results, breakdownDigest, brief, null);
	}

	/**
	 * The seam the alignment call goes through, so a report flavour can answer with its own editorial framing
	 * and its own extra context without the caller knowing which flavour it holds.
	 *
	 * <p>End-of-campaign wording ignores {@code reportingPeriod}: a finished campaign's copy is aligned around
	 * the flight as a whole, and the flight period is already named in the draft it aligns. {@link
	 * EomPromptBuilder} overrides the pieces that need the reporting month.
	 *
	 * @param strategic       the Batch A output feeding the draft
	 * @param results         the campaign-results output feeding the draft
	 * @param breakdownDigest one short line per breakdown conclusion, rendered as read-only context
	 * @param brief           free-text campaign brief the aligned narrative must stay faithful to
	 * @param reportingPeriod the window the report covers, as shown on the deck; unused here, may be
	 *                        {@code null} or blank
	 * @return the alignment prompt, or empty when the draft carries no alignable campaign-level copy
	 */
	Optional<String> alignPrompt(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief,
			String reportingPeriod) {
		List<String> draft = new ArrayList<>();
		List<String> schema = new ArrayList<>();

		String proposal = strategic == null ? null : strategic.proposalOverview();
		if (proposal != null && !proposal.isBlank()) {
			draft.add("proposal_overview: " + proposal.trim());
			schema.add(alignProposalSchema());
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
				schema.add(alignResultsOverviewsSchema(String.join(", ", groupKeys)));
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
			schema.add(alignThoughtsSchema(thoughtCount));
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
		context.append(alignReportingPeriodBlock(reportingPeriod));
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
				alignEditorRole()
						+ alignJobRules()
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
