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
		return "You are a senior digital media analyst writing the four 'Thoughts on tactic performance' "
				+ "bullets for ONE tactic's slide in an END-OF-MONTH report on a campaign that is STILL RUNNING. "
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
}
