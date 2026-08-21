package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The EOM half of the prompt text: every Claude prompt an end-of-month run sends.
 *
 * <p>This class exists so end-of-month wording can be changed without touching the end-of-campaign
 * wording. {@link ClaudeBatchPromptBuilder} is the end-of-campaign builder and is treated as frozen —
 * an EOM edit belongs here, as an override of the one method that carries the wording to change, and
 * never as an edit to the parent.
 *
 * <p>What to override and what to leave alone. The parent's prompt methods mix two things: the
 * <em>wording</em> (what the copy should say, what tense it is in, what a recommendation means) and the
 * <em>contract</em> (the JSON keys the reply must carry, how many strings a section returns, the
 * character budgets). {@link RealClaudeClient} parses replies against that contract and is shared by
 * both flavours, so an override that changes a JSON key or a field count breaks EOM parsing while EOC
 * keeps working. Override wording; inherit the contract.
 *
 * <p>A method that is not overridden here uses the parent's text, which means EOM behaves exactly as it
 * did before this split, and a later EOC wording change reaches EOM too. Once a method is overridden,
 * the two flavours are independent and an EOC change no longer reaches it — which is the point, and
 * also the thing to remember when the parent's contract changes.
 */
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class EomPromptBuilder extends ClaudeBatchPromptBuilder {

	/** Header prepended to the shared campaign-context block so every EOM call knows the flight is live. */
	private static final String ONGOING_HEADER = "=== REPORTING PERIOD ===\n"
			+ "This is an END-OF-MONTH report on a campaign that is STILL RUNNING. The figures below cover "
			+ "one reporting month of a longer flight, not a finished campaign. Never write as if the "
			+ "campaign has ended, and never present a number as a final result.\n"
			+ "The Flight line in the campaign plan below is that reporting month's window, not the campaign's "
			+ "whole flight: every actual figure is the month's delivery, and every plan figure is the month's "
			+ "target, so a gap against plan is a pacing gap with months left to close it.";

	/**
	 * The mid-flight principle appended to the shared conclusions principle list, so a per-tactic overview
	 * reads a gap against plan as pacing rather than as a verdict on a finished tactic.
	 *
	 * <p>It also redirects the closing move of principle 1. On a finished campaign a gap ends in a
	 * recommendation; mid-flight the tactic is still live and still correcting, so the honest close is why
	 * the gap is a normal fluctuation of a running flight and what keeps it on target.
	 */
	private static final String MID_FLIGHT_PRINCIPLE =
			"8. MID-FLIGHT PACING, NOT A VERDICT. Each tactic's plan figures are its target for THIS reporting "
					+ "month, so a gap against them is pacing, not a missed result. Read every gap as a normal "
					+ "fluctuation of a live flight and justify it with a specific cause. The RECOMMENDATION step of "
					+ "principle 1 becomes reassurance: why the tactic is on track over the remaining flight — never "
					+ "a fix for a failure, never a closing verdict.\n";

	/** Characters the north-star headline fits on the slide, upper-cased. */
	private static final int NORTH_STAR_LIMIT = 80;

	/** Characters the north-star supporting paragraph fits. */
	private static final int EXTENDED_NORTH_STAR_LIMIT = 340;

	/** Characters the horizon block fits. */
	private static final int HORIZON_LIMIT = 150;

	/** Characters the EOM north-star slide's audience-segments line fits, wider than the EOC template's. */
	private static final int AUDIENCE_SEGMENTS_LIMIT = 150;

	/** Tactics one pacing-dashboard slide draws; the deck carries one such slide per block of them. */
	static final int TACTICS_PER_DASHBOARD = 7;

	/** Pacing-dashboard slides the EOM template carries, covering tactics 1-7, 8-14, 15-21 and 22-28. */
	static final int MAX_DASHBOARDS = 4;

	/** Reply normaliser, kept alongside the parent's copy because the parent's field is private. */
	private final ClaudeResponseNormalizer normalizer;

	/** Number formatter, kept alongside the parent's copy because the parent's field is private. */
	private final Fmt fmt;

	public EomPromptBuilder(ClaudeResponseNormalizer normalizer, Fmt fmt) {
		super(normalizer, fmt);
		this.normalizer = normalizer;
		this.fmt = fmt;
	}

	/**
	 * Builds the end-of-month strategic-narrative prompt: the proposal overview and the four strategic
	 * insights, written for a campaign that is still running.
	 *
	 * <p>Two things differ from the end-of-campaign wording. The proposal overview describes the reporting
	 * month as continued activity in service of the campaign's standing objectives, instead of summing up a
	 * finished flight. The insights are asked for as movements — what shifted this month against the months
	 * before it and why — which is why the month-over-month delivery table is appended to the shared context
	 * block: without it the model has only this month's totals and can only restate them.
	 *
	 * <p>The JSON keys and the character budgets are the parent's, unchanged: {@code proposal_overview} is
	 * two sentences, each insight {@code point} is capped at 20 characters and each {@code overview} at 230,
	 * and there are exactly four of them. {@link RealClaudeClient} parses both flavours with the same code.
	 *
	 * @param data          parsed campaign plan and per-tactic performance
	 * @param brief         free-text campaign brief, treated as empty when {@code null}
	 * @param monthlyPivots tactic number to its monthly pacing series read back from the reviewed sheet
	 * @return the prompt requesting proposal/insights JSON, or empty when no context block could be built
	 */
	@Override
	Optional<String> strategicNarrativePrompt(
			CampaignData data, String brief, Map<Integer, Pivot> monthlyPivots) {
		Optional<String> shared = strategicNarrativeContext(data, brief);
		if (shared.isEmpty()) {
			return Optional.empty();
		}
		String monthly = monthlyDeliveryBlock(data, monthlyPivots);
		String context = monthly.isEmpty() ? shared.get() : shared.get() + "\n\n" + monthly;
		String dashboard = pacingDashboardBlock(data);
		if (!dashboard.isEmpty()) {
			context = context + "\n\n" + dashboard;
		}
		String performance = performanceDashboardBlock(data);
		if (!performance.isEmpty()) {
			context = context + "\n\n" + performance;
		}

		String prompt =
				"You are a senior digital media strategist at an advertising agency writing a client-facing "
						+ "END-OF-MONTH campaign report. The campaign is STILL RUNNING: this report covers one "
						+ "reporting month of a longer flight, and nothing in it is a final result.\n\n"
						+ "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
						+ "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the "
						+ "campaign?\" Raw data repeated as prose is not analysis. Transform each data point into a "
						+ "business implication.\n"
						+ "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
						+ "Forbidden phrases: \"performance is tracking well\", \"results are in line with "
						+ "expectations\", \"we recommend monitoring\", \"this tactic requires further "
						+ "optimization\". If a sentence could appear in any other campaign report unchanged — "
						+ "rewrite it.\n"
						+ "3. EXPLAIN THE WHY. Don't write \"X had a high CTR.\" Write WHY: creative format, "
						+ "placement type, audience intent level, message-to-moment alignment, competitive bid "
						+ "landscape, etc.\n"
						+ "4. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo. "
						+ "Name the specific cause. Name the specific action or outcome.\n"
						+ "5. COMPARE, DON'T RESTATE. Where a month-over-month delivery table is given below, it is "
						+ "the point of comparison. A number for this month alone is not an insight; what that "
						+ "number did against the months before it, and why, is.\n"
						+ "6. THE CAMPAIGN HAS NOT ENDED. Never write a closing verdict, never call a figure final, "
						+ "and never imply the flight is over. Write about where delivery stands, where it is "
						+ "heading, and what is being done about it.\n\n"
						+ "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"proposal_overview\": string,   // Exactly 2 complete sentences. No line breaks, no "
						+ "bullets. Sentence 1: what we kept running through the reporting month — name the month "
						+ "(take it from the Flight line), the actual tactic mix and the actual audience/geo — "
						+ "phrased as continued activity, e.g. \"Throughout <month> we continued running ...\". "
						+ "Sentence 2: the standing strategic objectives of the CAMPAIGN AS A WHOLE that this "
						+ "month's activity is driving toward. No character limit — write both sentences "
						+ "completely.\n"
						+ northStarSchema()
						+ pacingTakeawaysSchema(dashboardCount(data))
						+ performanceTakeawaysSchema(dashboardCount(data))
						+ "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, "
						+ "\"overview\": string}.\n"
						+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD "
						+ "LIMIT.\n"
						+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
						+ "                                // Each overview = a SHIFT visible in this reporting "
						+ "month — a metric that moved against the previous months, a tactic that changed "
						+ "direction, a pattern that emerged — plus WHY it moved and what it means for the rest of "
						+ "the flight. Four different shifts, not four angles on one. Business English, no filler.\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for any field where there is genuinely no data.\n"
						+ "- Do NOT invent facts. Base everything strictly on the provided data. If only one month "
						+ "of delivery is shown, write about what is emerging within it rather than inventing a "
						+ "trend.\n"
						+ "- Leave a field null rather than pad it with generic filler — an empty field beats an "
						+ "unsupported claim.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Specifies the {@code proposal_overview} field for the full Batch A prompt (audience + narrative
	 * combined), used only by the classic direct-to-deck flow that never assembles a reviewed sheet — see
	 * {@link ClaudeBatchPromptBuilder#buildBatchAPrompt}. That flow has no month-over-month table and no
	 * shared EOM context block to prepend (unlike {@link #campaignContextForConclusions} and {@link
	 * #strategicNarrativePrompt}), so this is the only place an EOM run through it learns the flight is
	 * still live; left unoverridden, an EOM report generated this way would open with "why the campaign
	 * ran... how it ran" — a closing summary directly contradicting every other EOM prompt in this class.
	 *
	 * @return the mid-flight field spec line, newline-terminated
	 */
	@Override
	String batchAProposalOverviewSpec() {
		return "  \"proposal_overview\": string,   // Exactly 2 complete sentences. No line breaks, no bullets. "
				+ "This is an END-OF-MONTH report on a campaign that is STILL RUNNING. Sentence 1: what we kept "
				+ "running through the reporting month — name the month (take it from the Flight line), the "
				+ "actual tactic mix and the actual audience/geo — phrased as continued activity, e.g. \"Throughout "
				+ "<month> we continued running ...\". Sentence 2: the standing strategic objectives of the "
				+ "CAMPAIGN AS A WHOLE that this month's activity is driving toward. No character limit — write "
				+ "both sentences completely.\n";
	}

	/**
	 * Specifies the {@code strategic_insights} field for the full Batch A prompt.
	 *
	 * <p>Unlike {@link #strategicNarrativePrompt}'s insights spec, this does not ask for a movement against
	 * earlier months: {@link ClaudeBatchPromptBuilder#buildBatchAPrompt} never carries a month-over-month
	 * table, and asking for one here would push the model to invent a trend it has no figures for. Each
	 * insight instead reads as a notable pattern within the reporting month itself.
	 *
	 * @return the mid-flight field spec lines, newline-terminated
	 */
	@Override
	String batchAStrategicInsightsSpec() {
		return "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": "
				+ "string}.\n"
				+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD "
				+ "LIMIT.\n"
				+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
				+ "                                // Each overview = a notable pattern in THIS reporting month's "
				+ "delivery — plus WHY it is happening and what it means for the rest of the flight. The campaign "
				+ "is STILL RUNNING: never write a closing verdict. Unique angles, Business English, no filler.\n";
	}

	/**
	 * Renders the month-over-month delivery table — one line per tactic, then a campaign total line — from
	 * the monthly pacing blocks read back out of the reviewed sheet.
	 *
	 * <p>Rates are kept on the tactic lines rather than rolled into the campaign line on purpose: a click
	 * rate and a completion rate belong to different tactics and averaging them across a mixed plan produces
	 * a number that means nothing. The campaign line therefore carries impressions only, which are additive.
	 *
	 * @param data          parsed campaign data supplying the tactic names
	 * @param monthlyPivots tactic number to its monthly pacing series; {@code null} or empty yields no block
	 * @return the rendered context block, or an empty string when no tactic carries a monthly series
	 */
	String monthlyDeliveryBlock(CampaignData data, Map<Integer, Pivot> monthlyPivots) {
		if (monthlyPivots == null || monthlyPivots.isEmpty()) {
			return "";
		}
		List<String> lines = new ArrayList<>();
		Map<String, Double> campaignImps = new LinkedHashMap<>();
		for (Map.Entry<Integer, Pivot> entry : monthlyPivots.entrySet()) {
			Pivot pivot = entry.getValue();
			if (pivot == null || pivot.isEmpty()) {
				continue;
			}
			String name = tacticName(data, entry.getKey());
			List<String> points = new ArrayList<>();
			for (Map.Entry<String, double[]> point : pivot.data().entrySet()) {
				double[] values = point.getValue();
				campaignImps.merge(point.getKey(), values[0], Double::sum);
				points.add(point.getKey() + " " + fmt.intGroup(Math.round(values[0])) + " imps"
						+ rateSuffix(name, pivot, values));
			}
			if (!points.isEmpty()) {
				lines.add("  Tactic " + entry.getKey() + " — " + name + ": " + String.join(" | ", points));
			}
		}
		if (lines.isEmpty()) {
			return "";
		}
		List<String> totals = new ArrayList<>();
		for (Map.Entry<String, Double> month : campaignImps.entrySet()) {
			totals.add(month.getKey() + " " + fmt.intGroup(Math.round(month.getValue())) + " imps");
		}
		lines.add("  Campaign total: " + String.join(" | ", totals));
		return "=== MONTH-OVER-MONTH DELIVERY ===\n"
				+ "Each tactic's monthly pacing series from the reviewed sheet, oldest month first.\n"
				+ String.join("\n", lines);
	}

	/**
	 * Renders one month's rate for a tactic line — {@code ", CTR 0.42%"} or {@code ", VCR 96.40%"} — or an
	 * empty string when the tactic's series carries no rate metric or delivered no impressions that month.
	 *
	 * @param tacticName the tactic's name, deciding whether a completion rate is labelled VCR or ACR
	 * @param pivot      the tactic's monthly series, whose flags say which metric column it carried
	 * @param values     one month's {@code {imps, clicks, completions}} triple
	 * @return the rate suffix appended to the month's impressions, or an empty string
	 */
	String rateSuffix(String tacticName, Pivot pivot, double[] values) {
		double imps = values[0];
		if (imps <= 0) {
			return "";
		}
		if (pivot.hasClicks() && values[1] > 0) {
			return ", CTR " + fmt.dec2(values[1] / imps * 100) + "%";
		}
		if (pivot.hasCompletions() && values[2] > 0) {
			return ", " + completionRateLabel(tacticName) + " " + fmt.dec2(values[2] / imps * 100) + "%";
		}
		return "";
	}

	/**
	 * Resolves a tactic's display name for the delivery table, falling back to its slide number.
	 *
	 * @param data      parsed campaign data whose tactic map is consulted
	 * @param tacticNum the 1-based tactic number
	 * @return the tactic's name, or {@code "Tactic N"} when the sheet carries none
	 */
	String tacticName(CampaignData data, Integer tacticNum) {
		Tactic tactic = data == null || data.tactics() == null ? null : data.tactics().get(tacticNum);
		if (tactic != null && normalizer.notBlank(tactic.name())) {
			return tactic.name();
		}
		return "Tactic " + tacticNum;
	}

	/**
	 * Prepends the ongoing-flight framing to the campaign context every per-tactic call shares.
	 *
	 * <p>This block sits in the cached prefix of the Step-2 conclusions, Step-3 thoughts and per-section
	 * calls, so it is the cheapest single place to tell the model that the campaign is mid-flight: it is
	 * paid for once per run and read by every call that follows. It also says what the plan and actual
	 * figures underneath it mean for an EOM run — both are the reporting month's, not the flight's — which
	 * is what turns "delivered X impressions" into "delivered X of the month's target".
	 *
	 * <p>It deliberately states no "month N of M" cadence. {@code eomMonthNumber} and
	 * {@code eomFlightMonthsTotal} are both derived from the selected reporting window and are equal by
	 * construction, so a cadence sentence built from them reads "month 1 of 1" on a normal run. The
	 * month-over-month table in {@link #strategicNarrativePrompt} carries the real calendar position.
	 *
	 * @param data  parsed campaign data supplying the plan and totals
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the parent's context block with the EOM reporting-period header in front of it
	 */
	@Override
	String campaignContextForConclusions(CampaignData data, String brief) {
		return ONGOING_HEADER + "\n\n" + super.campaignContextForConclusions(data, brief);
	}

	/**
	 * States who is writing and what the report is, for the Step-2 per-tactic conclusions call.
	 *
	 * <p>The parent's line calls it a post-campaign report. Left in place it is the first thing the model
	 * reads on every conclusions call, and it outranks the reporting-period header further down the prompt:
	 * the overviews come back written as closing verdicts on finished tactics.
	 *
	 * @return the EOM role line, ending in a blank line
	 */
	@Override
	String conclusionsRole() {
		return "You are a senior digital media analyst writing per-tactic conclusions for an END-OF-MONTH report "
				+ "on a campaign that is STILL RUNNING. Every tactic below is live: what you write covers one "
				+ "reporting month of a longer flight, and no figure in it is final.\n\n";
	}

	/**
	 * Adds the mid-flight principle to the analytical principles the conclusions call opens with.
	 *
	 * <p>The parent's seven principles hold for EOM unchanged — the deviation math, the learning-phase
	 * caveat and the "name the cause" rules are about how to read numbers, not about when the flight ends.
	 * What is missing is the reading of a gap against plan, which for EOM is a pacing gap against the
	 * month's target rather than a shortfall the campaign can no longer recover from.
	 *
	 * @return the parent's numbered principles with the mid-flight principle appended
	 */
	@Override
	String conclusionPrincipleList() {
		return super.conclusionPrincipleList() + MID_FLIGHT_PRINCIPLE;
	}

	/**
	 * Specifies the {@code {{tactic n overview}}} field for an end-of-month run: a pacing note rather than the
	 * parent's [delivered vs plan] + [why] + [business so-what] verdict on a finished tactic.
	 *
	 * <p>The field answers one question — how the month's metrics landed against the month's plan — and, where
	 * one of them sits off plan or the daily curve moved around, argues that as a normal fluctuation of a
	 * running flight rather than a problem. The causes it may name are the ones a media analyst reads off a
	 * live tactic, and the spec keeps them tied to the figures actually in the data block: the month's
	 * plan-vs-actual line and, when the sheet carried one, the tactic's daily pacing series.
	 *
	 * <p>The 190-character budget, the two-sentence cap and the per-tactic-type metric priorities are the
	 * parent's, unchanged: {@link RealClaudeClient} trims both flavours' replies to the same limit.
	 *
	 * @return the EOM overview spec, newline-terminated
	 */
	@Override
	String overviewSpec() {
		return "MAX 190 CHARACTERS, ending on a complete word and sentence. Write ONLY about pacing: how the "
				+ "tactic's metrics landed against the month's plan figures — ahead of, on, or behind pace, with "
				+ "the gap that shows it — and how delivery paced through the month where a DAILY PACING series is "
				+ "given (steady, ramping, front- or back-loaded, dipping and recovering). Where a metric sits off "
				+ "plan or the daily curve moved around, say plainly that this is a normal fluctuation of a live "
				+ "flight, name the specific cause (delivery ramp-up, inventory availability, bid-landscape shifts, "
				+ "DSP re-optimisation, creative rotation, weekday/weekend seasonality) and why the tactic is on "
				+ "track over the remaining flight. Never frame a gap or a dip as a failure, never prescribe a fix, "
				+ "never write a closing verdict. Ground every claim in the figures given: with no DAILY PACING "
				+ "series for a tactic, say nothing about its day-by-day movement.\n" + overviewFocusMetrics();
	}

	/**
	 * Appends the tactic's daily pacing series to its data block, so the overview argues the month's pacing
	 * from the curve the client will see on the tactic's pacing chart rather than from the month's totals.
	 *
	 * <p>Impressions only, and nothing derived. The daily clicks/completions column is left out on purpose:
	 * a single day's rate swings on small volume and is the kind of figure the copy would quote as if it
	 * meant something, while the rates that do belong in the overview — the month's CTR/VCR against plan —
	 * are already on the performance line above.
	 *
	 * @param tacticNum the 1-based tactic number this block belongs to
	 * @param tactic    the tactic's parsed performance metrics for the overview line
	 * @param daily     the tactic's daily pacing series; a {@code null} or empty series appends nothing
	 * @return the parent's data block, with the daily pacing line underneath it when there is one
	 */
	@Override
	String tacticConclusionDataBlock(int tacticNum, Tactic tactic, Pivot daily) {
		String block = super.tacticConclusionDataBlock(tacticNum, tactic, daily);
		String pacing = dailyPacingLine(daily);
		return pacing.isEmpty() ? block : block + pacing;
	}

	/**
	 * States who is writing and what the report is, for the Step-4 campaign-level results call.
	 *
	 * <p>Same reason as {@link #conclusionsRole()}: the parent's line calls it a post-campaign report, it is
	 * the first thing the model reads, and it outranks the reporting-period header further down the prompt.
	 *
	 * @return the EOM role line, ending in a blank line
	 */
	@Override
	String campaignResultsRole() {
		return "You are a senior digital media analyst writing the campaign-level copy for an END-OF-MONTH report "
				+ "on a campaign that is STILL RUNNING. Everything below covers one reporting month of a longer "
				+ "flight, and no figure in it is final.\n\n";
	}

	/**
	 * Specifies each tactic group's {@code results_overviews} entry for an end-of-month run: how the group
	 * paced against the month's plan, rather than the parent's closing result for a finished group.
	 *
	 * <p>The two sentences keep their jobs — the first is the group's headline, the second is who led and who
	 * lagged — but both are read as pacing, and a lagging tactic is argued as a normal fluctuation of a live
	 * flight rather than written off. The keying by group number, the two-sentence cap, the character budget
	 * and the naming rule are the parent's, unchanged: {@link RealClaudeClient} reads both flavours' replies
	 * off the same keys.
	 *
	 * @param groupNums     comma-separated group numbers the reply must carry a key for
	 * @param groupRanges   the group-to-tactics mapping quoted to Claude
	 * @param overviewLimit the buffered character budget of one group's overview
	 * @return the EOM field spec line, newline-terminated
	 */
	@Override
	String resultsOverviewsSpec(String groupNums, String groupRanges, int overviewLimit) {
		return "  \"results_overviews\": { // Keyed by tactic-group number as strings (" + groupNums + "). "
				+ "One entry PER GROUP (" + groupRanges + "). Each value covers ONLY that group's tactics, "
				+ "EXACTLY 2 SENTENCES, ≤" + overviewLimit
				+ " chars: sentence 1 = how the group paced against the month's plan (ahead of, on, or behind "
				+ "pace) + the key metric that shows it + a cause; sentence 2 = which tactic(s) led the pace and "
				+ "which trailed it, each with a reason. Past tense for the month's delivery, but no closing "
				+ "verdict — every tactic is still live. " + tacticNamingRule() + groupNamingRule()
				+ "Client-facing tone: lead with what was achieved this month, and present a tactic behind pace "
				+ "as a normal fluctuation of a running flight with the remaining months to close it. },\n";
	}

	/**
	 * Specifies the four {@code thoughts_on_performance} paragraphs for an end-of-month run.
	 *
	 * <p>Only the second paragraph changes. The parent asks why the campaign succeeded, which asserts a
	 * finished campaign and a settled outcome in the one field that sets the tone of the whole results slide;
	 * mid-flight the honest question is why delivery is landing the way it is. The count, the {@code " | "}
	 * separator and the character budget are the parent's — {@link RealClaudeClient} splits both flavours'
	 * replies on the same separator.
	 *
	 * @param thoughtsLimit the buffered character budget of all four paragraphs together
	 * @return the EOM field spec line, newline-terminated
	 */
	@Override
	String thoughtsOnPerformanceSpec(int thoughtsLimit) {
		return "  \"thoughts_on_performance\": string, // EXACTLY 4 short paragraphs joined by \" | \" "
				+ "(exactly 3 separators), ≤" + thoughtsLimit
				+ " chars total. (1) best-pacing tactic/channel this month + WHY; (2) why the campaign is "
				+ "delivering the way it is — name the mechanism; (3) one creative/format insight; (4) an "
				+ "efficiency or reach insight.\n";
	}

	/**
	 * Specifies the {@code performance_story} field — the fifth slot of the "Thoughts on the performance"
	 * slide — for an end-of-month run.
	 *
	 * <p>The parent asks for the campaign "start to finish", closing on where the results leave the client.
	 * That is the one field on the slide written as continuous prose, so a closing verdict there reads as the
	 * report's last word on a campaign that has months left to run — and it sits directly under the four
	 * mid-flight paragraphs {@link #thoughtsOnPerformanceSpec} already rewrote, which it would contradict.
	 * The story keeps its shape: it still opens from the brief and still ties the paragraphs above it
	 * together, but it closes on where the flight stands and where it is heading.
	 *
	 * <p>The JSON key and the character budget are the parent's, unchanged.
	 *
	 * @param storyLimit the buffered character budget of the story
	 * @return the EOM field spec line, newline-terminated
	 */
	@Override
	String performanceStorySpec(int storyLimit) {
		return "  \"performance_story\": string, // ≤" + storyLimit + " chars, ONE narrative: how WE see this "
				+ "campaign so far. Open from what the brief set out to achieve, carry it through what the "
				+ "results above show is happening this month, close on where the flight stands and what the "
				+ "remaining months are pointed at. Prose a client reads aloud — not a summary of the four "
				+ "thoughts, never contradicting them, and never a closing verdict on a campaign that is still "
				+ "running.\n";
	}

	/**
	 * States what the alignment pass is editing, for the Step-5 final narrative alignment.
	 *
	 * <p>The parent calls the deliverable a campaign report and leaves the pass free to smooth the copy into a
	 * closing summary — which is the one thing an alignment pass over mid-flight copy must not do, because it
	 * runs last and its output is what reaches the deck.
	 *
	 * @return the EOM framing paragraph, ending in a blank line
	 */
	@Override
	String alignEditorRole() {
		return "You are the lead editor on a client-facing digital-media END-OF-MONTH report for a campaign that is "
				+ "STILL RUNNING. Several sections were drafted independently by different analysts, so they repeat, "
				+ "drift, and sometimes explain the same result with different causes.\n\n";
	}

	/**
	 * The editorial rules of the alignment pass, with the storyline and shape rules written for a report on a
	 * live campaign.
	 *
	 * <p>Rules 2 to 4 are the parent's verbatim — they are about editing discipline, not about the report's
	 * tense. Rule 1 asks for the story of the reporting month instead of the story of the campaign, and rule 5
	 * keeps the pass from tidying mid-flight copy into a closing verdict.
	 *
	 * @return the EOM rules block, ending in a blank line
	 */
	@Override
	String alignJobRules() {
		return "YOUR JOB — editorial alignment, NOT reanalysis:\n"
				+ "1. ONE STORYLINE. Decide the single most important story of THIS REPORTING MONTH from the draft — "
				+ "how the campaign is pacing and why — then make every field a consistent facet of it. The proposal "
				+ "sets it up; the results overviews and thoughts pay it off; the strategic insights and frequency "
				+ "copy reinforce it.\n"
				+ alignSharedRules()
				+ "5. KEEP THE SHAPE. Return the SAME fields with the SAME counts and character limits as below. "
				+ "Business English, no filler, no labels inside the copy. Past tense for the month's delivery, but "
				+ "the campaign has NOT ended: never align a field into a closing verdict, a final result, or a "
				+ "farewell to the campaign, and never remove the reporting month from copy that names it.\n\n";
	}

	/**
	 * Names the reporting month at the top of the alignment context, so the aligned copy can place the month's
	 * story in time ("through July we …") and every field names the same period.
	 *
	 * <p>This is the one call in the run that sees all the campaign-level copy at once, which makes it the
	 * cheapest place to get the period consistent: the draft's fields were written by separate calls, and only
	 * some of them name the month. The label is the deck's own flight-dates string, so the copy cannot drift
	 * from what the slides show.
	 *
	 * @param reportingPeriod the window the report covers, as shown on the deck; blank yields no block
	 * @return the reporting-period block, or an empty string when the run carries no label
	 */
	@Override
	String alignReportingPeriodBlock(String reportingPeriod) {
		if (!normalizer.notBlank(reportingPeriod)) {
			return "";
		}
		return "=== REPORTING PERIOD ===\n"
				+ "This report covers " + reportingPeriod.trim() + " — one reporting month of a campaign that is "
				+ "still running. Where a field places its story in time, place it in THIS period and name the "
				+ "month, e.g. \"through July we ...\"; every field must name the same period, and none may imply "
				+ "the flight is over.\n\n";
	}

	/**
	 * Specifies what the aligned {@code proposal_overview} must still be: two sentences that set up the month's
	 * story, rather than the parent's summary of a finished flight.
	 *
	 * <p>The 400-character budget and the "keep every named tactic/audience/geo" rule are the parent's — the
	 * pass may sharpen wording, never drop a fact the draft carried.
	 *
	 * @return the EOM schema line, newline-terminated
	 */
	@Override
	String alignProposalSchema() {
		return "  \"proposal_overview\": string,   // Exactly 2 sentences, no line breaks. ≤400 chars. Sentence 1 "
				+ "names the reporting month and what we kept running through it; sentence 2 the campaign's standing "
				+ "objectives. Keep every named tactic/audience/geo from the draft; only sharpen wording.\n";
	}

	/**
	 * Specifies the aligned {@code results_overviews} entries: each group's pacing through the month, keeping
	 * the parent's keying, two-sentence cap and 380-character budget.
	 *
	 * @param groupKeys comma-separated group numbers the reply must carry a key for
	 * @return the EOM schema line, newline-terminated
	 */
	@Override
	String alignResultsOverviewsSchema(String groupKeys) {
		return "  \"results_overviews\": object,     // Keyed by group number as strings ("
				+ groupKeys + "). One entry per key listed, no more, no fewer. Each: EXACTLY "
				+ "2 sentences, ≤380 chars, about how that group paced this month — no closing verdict. Must pay "
				+ "off the same storyline with its own group's numbers. " + groupNamingRule() + "\n";
	}

	/**
	 * Names the kind of report the five breakdown-section slides belong to, for an end-of-month run.
	 *
	 * <p>This one phrase is the whole EOM change to the section prompts, and it is deliberately the whole
	 * change: what those prompts ask for — the takeaway, what worked, the watch-out, the forward-looking
	 * action, the grounding in the tactic's own table — is the same question mid-flight as at the end. What
	 * differs is what the table's figures are, and that is already stated once for all five calls by
	 * {@link #campaignContextForConclusions}: one reporting month of a live campaign, actuals for the month
	 * against the month's target.
	 *
	 * @return the EOM report-kind phrase, with its leading article and no trailing punctuation
	 */
	@Override
	String sectionReportKind() {
		return "an END-OF-MONTH report on a campaign that is STILL RUNNING, covering one reporting month of a "
				+ "longer flight";
	}

	/**
	 * Specifies the creative section's fourth string — the optimisation already made on creative — for an
	 * end-of-month run.
	 *
	 * <p>Everything the parent says about WHY this one string may be reconstructed rather than quoted, and
	 * about which names and numbers it is held to, applies unchanged mid-flight. Only the tense moves: the
	 * parent asks for it in past tense as a finished change, which on a live tactic reads as a completed
	 * optimisation programme rather than a change still working. Asking for it as something we did during the
	 * month and are still seeing the effect of keeps the slot filled without closing the tactic out.
	 *
	 * @param lead  how the instruction names the slot, e.g. {@code "String 4"} or {@code "Takeaway 4"}
	 * @param limit character budget quoted to Claude for this string
	 * @return the EOM optimisation instruction, newline-terminated
	 */
	@Override
	String creativeOptimisationRule(String lead, int limit) {
		return lead + " states an optimisation WE ALREADY MADE on creative during this reporting month and the "
				+ "effect it is having, at most " + limit + " characters. The data carries NO change log, so this "
				+ "ONE string is EXPECTED to be reconstructed rather than quoted: infer the most plausible "
				+ "optimisation the numbers imply — shifting weight toward the strongest creative, retiring an "
				+ "under-delivering size or format, refreshing worn creative, rebalancing spend across sizes — and "
				+ "state it as something WE DID this month, with the effect described as still in flight rather "
				+ "than as a settled outcome. That is expected here and is NOT a data problem: never hedge it, "
				+ "never say the change log is missing, never leave it generic. Constraints: every creative you "
				+ "name and every number you cite must come from the table (never invent a metric), the result you "
				+ "claim must be consistent with the table's figures, and the action must obey the small-sample "
				+ "and budget-shift rules above. Use the tactic's own KPI type for its lead metric.\n";
	}

	/**
	 * States who is writing and what the report is, for the Step-3 per-tactic thoughts call.
	 *
	 * <p>Same reason as {@link #conclusionsRole()}: the parent's line calls it an end-of-campaign report, it
	 * is the first thing the model reads, and the tone instruction ("confident and complimentary of our own
	 * delivery") is fine mid-flight, so only the report-type clause changes.
	 *
	 * @return the EOM role paragraph, ending in a blank line
	 */
	@Override
	String tacticThoughtsRole() {
		return "You are a senior digital media analyst writing the 'Thoughts on tactic performance' slide for "
				+ "ONE tactic in an END-OF-MONTH report on a campaign that is STILL RUNNING — four analytical "
				+ "bullets plus the closing narrative that ties them together. "
				+ "You are writing on behalf of the team running this campaign, so the tone is confident and "
				+ "complimentary of our own delivery so far.\n\n";
	}

	/**
	 * Specifies the four thought angles for an end-of-month run: only the first changes, from the tactic's
	 * headline result to how it is pacing.
	 *
	 * <p>The parent's angle (1) asks for a closing headline, which reads as a verdict on a tactic that has not
	 * finished; angles (2)-(4) — what worked across breakdowns, a watch-out, the forward-looking opportunity —
	 * hold for a live tactic exactly as written, and the "synthesise, don't restate" framing and the
	 * four-thought count are the parent's, unchanged.
	 *
	 * @return the EOM angles paragraph, newline-terminated
	 */
	@Override
	String tacticThoughtsAngles() {
		return "Synthesise across the tactic's overview AND its breakdown conclusions below — do not just restate "
				+ "one of them. Vary the 4 angles: (1) how the tactic is pacing this month and WHY; (2) what worked "
				+ "best across its breakdowns (publishers / creative / geo / audience / device); (3) a watch-out or "
				+ "nuance; (4) the forward-looking opportunity for this tactic.\n";
	}

	/**
	 * Specifies the four analytical bullets of the Step-3 per-tactic thoughts call for an end-of-month run.
	 *
	 * <p>The parent asks for them in past tense. That single word outranks {@link #tacticThoughtsAngles()}
	 * right above it — angle (1) asks how the tactic IS pacing, and the bullets still come back written as if
	 * the tactic had finished. The count and the character budget are the parent's: {@link RealClaudeClient}
	 * parses four bullets plus a story from both flavours with the same code.
	 *
	 * @param promptLimit the buffered character budget quoted for each bullet
	 * @return the EOM bullets spec, newline-terminated
	 */
	@Override
	String tacticThoughtsBulletsSpec(int promptLimit) {
		return "Write EXACTLY 4 short analytical thoughts about how THIS tactic is performing so far, each 1-2 "
				+ "sentences, client-friendly, at most " + promptLimit + " characters. The tactic is STILL "
				+ "RUNNING: write about delivery to date and where it is heading, never as a verdict on a "
				+ "finished tactic.\n";
	}

	/**
	 * Specifies the closing {@code story} field — the tactic slide's fifth slot — for an end-of-month run.
	 *
	 * <p>Same defect as {@link #performanceStorySpec} one level down: the parent asks for the tactic "start to
	 * finish" and closes on where that leaves it, which is a verdict on a tactic that is still delivering, and
	 * it sits on the same slide as four bullets this class has just had rewritten mid-flight. The story keeps
	 * its job — the through-line the bullets leave implicit, tied back to the brief — and closes on the
	 * remaining flight instead.
	 *
	 * <p>The field name, its position after the four thoughts and its character budget are the parent's,
	 * unchanged.
	 *
	 * @param storyLimit the story's character budget
	 * @return the EOM story spec, ending in a blank line
	 */
	@Override
	String tacticThoughtsStorySpec(int storyLimit) {
		int promptLimit = bufferedLimit(storyLimit);
		return "\nThen write ONE closing narrative — \"story\" — of at most " + promptLimit + " characters: how "
				+ "WE see this tactic so far. Open from what the brief asked this tactic to do, carry it through "
				+ "what the overview and the four thoughts above establish is happening, and close on where that "
				+ "leaves the tactic for the rest of the flight. It is prose that a client reads aloud, not a "
				+ "summary or a restatement of the four thoughts: it must add the through-line the bullets leave "
				+ "implicit, it must not contradict any of them, and it must not read as a closing verdict on a "
				+ "tactic that is still running.\n\n";
	}

	/**
	 * Renders one tactic's daily pacing series as a single compact line, oldest day first.
	 *
	 * @param daily the tactic's daily pacing series
	 * @return the {@code DAILY PACING} line, newline-terminated, or an empty string when there is no series
	 */
	String dailyPacingLine(Pivot daily) {
		if (daily == null || daily.isEmpty()) {
			return "";
		}
		List<String> days = new ArrayList<>();
		for (Map.Entry<String, double[]> day : daily.data().entrySet()) {
			days.add(day.getKey() + " " + fmt.intGroup(Math.round(day.getValue()[0])));
		}
		return "DAILY PACING (impressions per day, oldest first): " + String.join(" | ", days) + "\n";
	}
	/**
	 * The three north-star fields of the EOM deck's second slide, appended to the strategic-narrative schema.
	 *
	 * <p>They answer three different questions and must not restate one another: {@code north_star} is the
	 * campaign's objective as a headline, {@code extended_north_star} is that objective unpacked into the
	 * geos, audiences and channels it is pursued with, and {@code horizon} is the timing — when the campaign
	 * runs, for how long, and with what delivery shape — which is what explains the channel mix printed
	 * underneath it on the slide.
	 *
	 * <p>They ride on this call rather than on one of their own because it already carries the campaign plan,
	 * the audience and the geo the answers are drawn from; a separate call would pay for that context twice.
	 * The end-of-campaign flavour never asks for them, so on an EOC run the three fields come back absent and
	 * their tokens render as dashes.
	 *
	 * @return the three field specs, each newline-terminated
	 */
	String northStarSchema() {
		return "  \"north_star\": string,          // MAX " + NORTH_STAR_LIMIT + " CHARACTERS, HARD LIMIT. The "
				+ "campaign's single objective as one headline, WRITTEN ENTIRELY IN CAPITAL LETTERS, e.g. "
				+ "\"DEEP PENETRATION OF THE HIGH-HHI LUXURY-TRAVEL AUDIENCE\". Name the actual audience or "
				+ "outcome this campaign is bought for — not a channel, not a metric, not a slogan. No final "
				+ "period.\n"
				+ "  \"extended_north_star\": string, // MAX " + EXTENDED_NORTH_STAR_LIMIT + " chars. The same "
				+ "objective unpacked: in WHICH geos, against WHICH audience segments, through WHICH channels. "
				+ "Name the actual markets, the actual segments and the actual tactic mix from the data below — "
				+ "never a generic restatement of the headline.\n"
				+ "  \"horizon\": string,            // MAX " + HORIZON_LIMIT + " chars. WHEN the campaign runs, "
				+ "for HOW LONG, and with what delivery shape (continuous presence, flighted bursts, a "
				+ "front-loaded launch). Take the dates from the campaign plan. This is what explains the "
				+ "channel mix on the slide, so make the link explicit: sustained presence is why reach-building "
				+ "video and always-on display sit beneath it.\n";
	}

	/**
	 * Widens the {@code audience_segments} budget to {@link #AUDIENCE_SEGMENTS_LIMIT} characters: the EOM
	 * north-star slide prints the segments line in its own block rather than in the EOC template's narrow
	 * strip, so it fits a fuller phrase than the parent asks for.
	 *
	 * @return the end-of-month audience-segments budget
	 */
	@Override
	int audienceSegmentsLimit() {
		return AUDIENCE_SEGMENTS_LIMIT;
	}

	/**
	 * How many pacing-dashboard slides this campaign's deck keeps: one per block of
	 * {@link #TACTICS_PER_DASHBOARD} tactics, capped at the {@link #MAX_DASHBOARDS} the template draws.
	 * The blocks above the campaign's tactic count are deleted from the deck, so asking for their
	 * takeaways would pay for copy no slide prints.
	 *
	 * @param data parsed campaign plan and per-tactic performance
	 * @return the number of dashboard slides, at least one
	 */
	int dashboardCount(CampaignData data) {
		int tactics = data == null || data.tactics() == null ? 0 : data.tactics().size();
		int blocks = (tactics + TACTICS_PER_DASHBOARD - 1) / TACTICS_PER_DASHBOARD;
		return Math.clamp(blocks, 1, MAX_DASHBOARDS);
	}

	/**
	 * The {@code pacing_takeaways} field spec: one key takeaway per pacing-dashboard slide, in slide order.
	 *
	 * <p>The takeaway is asked for as a verdict on the block's pacing — everything on track, or the one or
	 * two channels worth looking at and why — because that is the sentence the slide prints under its table,
	 * next to the numbers it is about. It rides on the strategic call, which already carries the campaign
	 * plan and the delivery history the verdict has to be consistent with.
	 *
	 * @param dashboards how many dashboard slides the deck keeps, one takeaway each
	 * @return the field spec, newline-terminated
	 */
	String pacingTakeawaysSchema(int dashboards) {
		return "  \"pacing_takeaways\": array,      // EXACTLY " + dashboards + " string(s), in order, one per "
				+ "PACING DASHBOARD block below (block 1 = tactics 1-" + TACTICS_PER_DASHBOARD + ", block 2 = "
				+ "the next " + TACTICS_PER_DASHBOARD + ", and so on). MAX "
				+ ClaudeResponseNormalizer.PACING_TAKEAWAY_LIMIT + " CHARACTERS EACH, HARD LIMIT — the slot is "
				+ "one line under the table.\n"
				+ "                                // Each string is the pacing verdict for THAT block and "
				+ "nothing else: if every channel in it is delivering against its budget and impression goal, "
				+ "say so and say what is carrying it; if one or two are off, NAME them, give the pacing figure "
				+ "and say in the same breath why it is a normal fluctuation of a live flight or what is being "
				+ "done about it. Never a list of every channel, never a metric restated without a "
				+ "consequence.\n";
	}

	/**
	 * The {@code performance_takeaways} field spec: one key takeaway per performance-vs-plan slide, in
	 * slide order.
	 *
	 * <p>The slide next to it is a KPI table — each tactic's goal rate, the rate it is actually running at
	 * and the gap between them — so the takeaway is asked for as a verdict on KPI delivery specifically,
	 * not on budget pacing, which the dashboard three slides earlier already has its own takeaway for.
	 * Blocked the same way and asked for one per block, for the same reason.
	 *
	 * @param dashboards how many dashboard slides the deck keeps, one takeaway each
	 * @return the field spec, newline-terminated
	 */
	String performanceTakeawaysSchema(int dashboards) {
		return "  \"performance_takeaways\": array, // EXACTLY " + dashboards + " string(s), in order, one per "
				+ "PERFORMANCE VS PLAN block below (same blocks as the pacing dashboard: block 1 = tactics 1-"
				+ TACTICS_PER_DASHBOARD + ", and so on). MAX "
				+ ClaudeResponseNormalizer.PACING_TAKEAWAY_LIMIT + " CHARACTERS EACH, HARD LIMIT — the slot is "
				+ "one line under the table.\n"
				+ "                                // Each string is the KPI verdict for THAT block and nothing "
				+ "else — this is about the RATES (CTR / completion rate) against their goals, NOT about budget "
				+ "or impression pacing. If every channel in the block is at or above its goal rate, say so and "
				+ "say what is driving it; if one or two are below, NAME them, give the gap in percentage points "
				+ "and say in the same breath why it is a normal fluctuation of a live flight or what is being "
				+ "done about it. Never a list of every channel, never a rate restated without a "
				+ "consequence.\n";
	}

	/**
	 * Renders the performance-vs-plan dashboard as the slides print it — each tactic's goal rate against
	 * the rate it is actually delivering — grouped into the same blocks the deck draws.
	 *
	 * <p>The shared context carries the delivered rates but no rate targets, so without this block there is
	 * no goal for a KPI verdict to be about. Both rates are printed whenever the plan carries one: which of
	 * them a given slide row prints is decided deck-side from the tactic's KPI type, and a takeaway written
	 * against the rate that tactic is actually judged on is right either way.
	 *
	 * @param data parsed campaign plan and per-tactic performance
	 * @return the performance context block, or an empty string when no tactic carries a planned rate
	 */
	String performanceDashboardBlock(CampaignData data) {
		if (data == null || data.tactics() == null || data.tactics().isEmpty()) {
			return "";
		}
		List<String> lines = new ArrayList<>();
		boolean planned = false;
		int block = 0;
		for (Map.Entry<Integer, Tactic> entry : data.tactics().entrySet()) {
			Tactic tactic = entry.getValue();
			if (tactic == null) {
				continue;
			}
			int tacticBlock = (entry.getKey() - 1) / TACTICS_PER_DASHBOARD + 1;
			if (tacticBlock != block) {
				block = tacticBlock;
				lines.add("  -- BLOCK " + block + " --");
			}
			planned = planned || tactic.planCtr() != null || tactic.planVcr() != null;
			lines.add("  Tactic " + entry.getKey() + " — " + tactic.name() + ": "
					+ performanceLine(tactic));
		}
		if (!planned) {
			return "";
		}
		return "=== PERFORMANCE VS PLAN ===\n"
				+ "The rate KPIs per tactic — the goal and what is actually being delivered against it — blocked "
				+ "exactly as the report's performance slides print it. A gap is read in percentage points.\n"
				+ String.join("\n", lines);
	}

	/**
	 * Renders one performance row: whichever rate KPIs the tactic carries, goal against actual.
	 *
	 * @param tactic the tactic whose rates are rendered
	 * @return the formatted row, or a note that the tactic carries no rate KPI at all
	 */
	String performanceLine(Tactic tactic) {
		List<String> parts = new ArrayList<>();
		if (tactic.planCtr() != null || tactic.ctr() != null) {
			parts.add("CTR goal " + rate(tactic.planCtr()) + " / actual " + rate(tactic.ctr()));
		}
		if (tactic.planVcr() != null || tactic.vcr() != null) {
			parts.add("completion rate goal " + rate(tactic.planVcr()) + " / actual " + rate(tactic.vcr()));
		}
		return parts.isEmpty() ? "no rate KPI" : String.join(" | ", parts);
	}

	/**
	 * Renders one rate for the performance block, as the percentage the deck prints.
	 *
	 * @param value the rate, already scaled to a percentage ({@code null} when the campaign carries none)
	 * @return the rate with its percent sign, or {@code "n/a"} when there is none
	 */
	String rate(Double value) {
		return value == null ? "n/a" : fmt.dec2(value) + "%";
	}

	/**
	 * Renders the pacing dashboard exactly as the slides print it — planned and actual budget, the pacing
	 * between them, and planned and actual impressions per tactic — grouped into the blocks the deck draws,
	 * with the campaign totals last.
	 *
	 * <p>The takeaways are written against these numbers rather than against the tactic-performance lines in
	 * the shared context, which carry no plan figures at all: without the plan there is no pacing to have a
	 * verdict about. Blocking the table the same way the deck does is what lets the model be told "one
	 * takeaway per block" and have that mean something.
	 *
	 * @param data parsed campaign plan and per-tactic performance
	 * @return the dashboard context block, or an empty string when no tactic carries a planned figure
	 */
	String pacingDashboardBlock(CampaignData data) {
		if (data == null || data.tactics() == null || data.tactics().isEmpty()) {
			return "";
		}
		List<String> lines = new ArrayList<>();
		double planSpend = 0;
		double factSpend = 0;
		double planImps = 0;
		double factImps = 0;
		boolean planned = false;
		int block = 0;
		for (Map.Entry<Integer, Tactic> entry : data.tactics().entrySet()) {
			Tactic tactic = entry.getValue();
			if (tactic == null) {
				continue;
			}
			int tacticBlock = (entry.getKey() - 1) / TACTICS_PER_DASHBOARD + 1;
			if (tacticBlock != block) {
				block = tacticBlock;
				lines.add("  -- BLOCK " + block + " --");
			}
			planned = planned || tactic.planSpend() != null || tactic.planImps() != null;
			planSpend += nullSafe(tactic.planSpend());
			factSpend += tactic.spend();
			planImps += nullSafe(tactic.planImps());
			factImps += tactic.imps();
			lines.add("  Tactic " + entry.getKey() + " — " + tactic.name() + ": "
					+ pacingLine(tactic.planSpend(), tactic.spend(), tactic.planImps(), tactic.imps()));
		}
		if (!planned) {
			return "";
		}
		lines.add("  Campaign total: " + pacingLine(planSpend, factSpend, planImps, factImps));
		return "=== PACING DASHBOARD ===\n"
				+ "The reporting month's budget and impression pacing per tactic, blocked exactly as the "
				+ "report's dashboard slides print it. Pacing is actual spend over planned spend.\n"
				+ String.join("\n", lines);
	}

	/**
	 * Renders one dashboard row: the planned and actual budget with the pacing between them, then the
	 * planned and actual impressions.
	 *
	 * @param planSpend planned spend for the row ({@code null} when the plan carries none)
	 * @param factSpend delivered spend for the row
	 * @param planImps  planned impressions for the row ({@code null} when the plan carries none)
	 * @param factImps  delivered impressions for the row
	 * @return the formatted row
	 */
	String pacingLine(Double planSpend, double factSpend, Double planImps, double factImps) {
		double plan = nullSafe(planSpend);
		String pacing = plan > 0 ? Math.round(factSpend / plan * 100) + "%" : "n/a";
		return "budget plan " + fmt.money(plan) + " / actual " + fmt.money(factSpend) + " = " + pacing
				+ " | imps plan " + fmt.intGroup(nullSafe(planImps)) + " / actual " + fmt.intGroup(factImps);
	}

	/**
	 * Reads an optional planned figure as a number, treating "not planned" as zero so a row with no plan
	 * simply carries no pacing rather than breaking the whole block.
	 *
	 * @param value the planned figure (may be {@code null})
	 * @return the value, or {@code 0} when absent
	 */
	double nullSafe(Double value) {
		return value == null ? 0 : value;
	}
}
