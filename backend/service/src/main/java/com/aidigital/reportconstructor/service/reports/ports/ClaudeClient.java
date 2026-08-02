package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the Anthropic Claude calls the report engine makes:
 * Batch A (strategic), Batch B (tactical), Batch C (results) plus a small
 * Geo-tab summarisation call.
 *
 * <p>The real client is only registered when {@code ANTHROPIC_API_KEY}
 * is set; otherwise the stub client (the only candidate) is injected
 * and every batch returns empty (so resolvers fall back to {@code "—"}).
 */
public interface ClaudeClient {

	/**
	 * @return true when the client is hitting the real Anthropic API.
	 */
	boolean isLive();

	/**
	 * One tactic's "Top Publishers" slide copy, produced by a small dedicated call. Common contract for every
	 * per-section method: the call asks for the section's fixed number of slide strings as a bare JSON array and
	 * accepts the reply once it carries at least that many, using the first of them in slide order — a reply
	 * that came back short, blank or unparseable is retried a bounded number of times, each retry told what the
	 * last one got wrong, before the section gives up and ships blank. The smaller request and positional
	 * contract make a malformed or partial reply far less likely and, when it still happens, visible rather than
	 * silently blank. The caller fans these calls out across tactics on its own executor (bounded by the shared
	 * Claude concurrency limit).
	 *
	 * @param data  parsed campaign data supplying the shared campaign context
	 * @param input the tactic's publisher input (name + rows)
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the four publisher observations in slide order, or an empty list when every attempt failed
	 */
	List<String> publisherSection(CampaignData data, PublisherObservationInput input, String brief);

	/**
	 * One tactic's "Creative analysis" slide copy, produced by a small dedicated call. See
	 * {@link #publisherSection} for the shared accept/retry contract.
	 *
	 * @param data  parsed campaign data supplying the shared campaign context
	 * @param input the tactic's creative input (name + KPI type + table)
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the four creative takeaways in slide order (three reads + one optimisation), or an empty list on failure
	 */
	List<String> creativeSection(CampaignData data, CreativeTakeawayInput input, String brief);

	/**
	 * One tactic's "Geo analysis" slide copy, produced by a small dedicated call. See {@link #publisherSection}
	 * for the shared accept/retry contract.
	 *
	 * @param data  parsed campaign data supplying the shared campaign context
	 * @param input the tactic's geo input (name + KPI type + table)
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the five geo strings in slide order (four insights + one forward-looking reco), or an empty list on failure
	 */
	List<String> geoSection(CampaignData data, GeoInsightInput input, String brief);

	/**
	 * One tactic's "Audience analysis" slide copy, produced by a small dedicated call. See
	 * {@link #publisherSection} for the shared accept/retry contract.
	 *
	 * @param data  parsed campaign data supplying the shared campaign context
	 * @param input the tactic's audience input (name + table)
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the four audience strings in slide order (takeaway, what worked, watch-out, recommendation), or an
	 *         empty list when every attempt failed
	 */
	List<String> audienceSection(CampaignData data, AudienceInsightInput input, String brief);

	/**
	 * One tactic's "Device breakdown" slide copy, produced by a small dedicated call. See
	 * {@link #publisherSection} for the shared accept/retry contract.
	 *
	 * @param data  parsed campaign data supplying the shared campaign context
	 * @param input the tactic's device input (name + table)
	 * @param brief free-text campaign brief the copy must stay faithful to
	 * @return the four device strings in slide order (takeaway, what worked, watch-out, recommendation), or an
	 *         empty list when every attempt failed
	 */
	List<String> deviceSection(CampaignData data, DeviceInsightInput input, String brief);

	/**
	 * Batch A — audience age/segments, proposal overview, 4 strategic insights.
	 */
	ClaudeStrategic batchStrategic(CampaignData data, String brief);

	/**
	 * Batch A narrative subset — proposal overview + 4 strategic insights only, with the audience fields
	 * left {@code null}. Used by the slides-from-sheet flow, where {@code audience_age}/{@code audience_segments}
	 * already live in the reviewed sheet (generated by {@link #batchSheet} in step 1), so regenerating them
	 * here would waste tokens on copy that the sheet overlay immediately discards.
	 *
	 * @param data          parsed campaign data driving the strategic context block
	 * @param brief         free-text campaign brief used as the {@code === CAMPAIGN BRIEF ===} context section
	 * @param monthlyPivots tactic number to its monthly pacing series, read back from the reviewed sheet. Only
	 *                      the end-of-month wording uses it — its insights are asked for as month-over-month
	 *                      movements — and the end-of-campaign wording ignores it; may be {@code null} or empty
	 * @return the parsed proposal + strategic-insight copy, with {@code audienceAge}/{@code audienceSegments} null
	 */
	ClaudeStrategic batchStrategicNarrative(CampaignData data, String brief, Map<Integer, Pivot> monthlyPivots);

	/**
	 * Batch B — per-tactic gender split + weekday/weekend peak windows.
	 */
	ClaudeTactical batchTactical(CampaignData data, String brief);

	/**
	 * Generate Sheet batch — a single call covering only the fields the sheet template consumes:
	 * the Batch A {@code audience_age}/{@code audience_segments} narrative plus the Batch B per-tactic
	 * gender split and weekday/weekend peak windows. The field instructions and parsing mirror Batches A
	 * and B exactly; the Batch A proposal/strategic-insight copy and all Batch C copy are never requested
	 * because the sheet does not use them.
	 *
	 * @param data  parsed campaign data whose plan/tactic context drives the audience and per-tactic estimates
	 * @param brief free-text campaign brief used as the {@code === CAMPAIGN BRIEF ===} context section
	 * @return the parsed audience + per-tactic copy for the sheet
	 */
	ClaudeSheetBatch batchSheet(CampaignData data, String brief);

	/**
	 * Batch D — narrative alignment. A final harmonisation pass run after Batches A and C and after every
	 * per-tactic breakdown batch has produced its copy. It reads the already-generated campaign-level
	 * conclusions plus a read-only digest of the breakdown conclusions and rewrites only the cross-cutting
	 * story fields — the proposal overview, the four strategic insights, the per-group results overviews, the
	 * performance thoughts, and the three frequency-narrative strings — so the deck reads as one storyline
	 * that stays faithful to the brief. The audience fields, per-tactic overviews, and optimisation
	 * recommendations are carried through unchanged.
	 *
	 * <p>The pass is purely additive: on any failure, timeout, empty reply, or a client that is not live, the
	 * originals are returned verbatim, so alignment can only improve the deck, never blank it.
	 *
	 * @param strategic       the Batch A output to align ({@code proposalOverview} + {@code strategicInsights});
	 *                        its audience fields are passed through untouched
	 * @param results         the Batch C output to align ({@code resultsOverviews} + {@code thoughtsOnPerformance}
	 *                        + frequency narrative); its tactic overviews and recommendations pass through untouched
	 * @param breakdownDigest one short line per per-tactic breakdown conclusion (geo/audience/device/creative/
	 *                        publisher), used as read-only context so the aligned narrative reflects and does not
	 *                        contradict the deeper slides; empty when no breakdown sections were selected
	 * @param brief           free-text campaign brief the aligned narrative must stay faithful to
	 * @param reportingPeriod the window the report covers, as shown on the deck (the deck's flight-dates
	 *                        label). Only the end-of-month wording reads it, so its copy can name the
	 *                        reporting month; the end-of-campaign wording ignores it
	 * @return the aligned strategic + results records, or the originals verbatim when nothing could be aligned
	 */
	ClaudeNarrative batchAlignNarrative(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief,
			String reportingPeriod);

	/**
	 * Step 2 of the restructured slides-from-sheet flow — the per-tactic conclusions call. For each tactic it
	 * writes that tactic's {@code {{tactic n overview}}} narrative and nothing else; every breakdown section's
	 * slide copy comes from that section's own dedicated per-tactic call ({@link #publisherSection},
	 * {@link #creativeSection}, {@link #geoSection}, {@link #audienceSection}, {@link #deviceSection}). The
	 * tactic's performance metrics for the overview are read from {@code data} by tactic number.
	 *
	 * <p>Tactics are processed in configurable chunks behind a global concurrency semaphore, with a stable
	 * cached instruction/context prefix so each call re-reads it cheaply. A chunk that fails or fails to parse
	 * drops only its own tactics; the rest still get their conclusions, and a tactic missing from the result got
	 * no usable reply.
	 *
	 * @param data        parsed campaign data supplying the shared context and each tactic's overview metrics
	 * @param tacticNums  the 1-based tactic numbers to cover, in slide order
	 * @param brief       free-text campaign brief the conclusions must stay faithful to
	 * @param dailyPivots tactic number to its daily pacing series, read back from the reviewed sheet. Only the
	 *                    end-of-month wording reads it — an overview on a live tactic argues its pacing from the
	 *                    day-by-day curve — and the end-of-campaign wording ignores it; may be empty
	 * @return one {@link TacticConclusion} per tactic that produced a usable reply, in input order
	 */
	List<TacticConclusion> batchTacticConclusions(
			CampaignData data, List<Integer> tacticNums, String brief, Map<Integer, Pivot> dailyPivots);

	/**
	 * Step 3 of the restructured slides-from-sheet flow — the per-tactic "thoughts on tactic performance" call,
	 * for ONE tactic. Runs only for tactics that passed the shared "> 2 breakdowns" gate; the caller builds one
	 * input per such tactic from that tactic's in-memory Step-2 conclusions. The call reasons over that tactic's
	 * own overview and breakdown conclusions and returns up to four length-capped thought strings.
	 *
	 * <p>One tactic per call so the caller can dispatch every tactic at once — the calls then run in parallel
	 * behind the same global concurrency semaphore as Step 2. A reply that fills fewer than four thoughts is
	 * retried once; a tactic whose call fails, fails to parse, or comes back with nothing usable returns
	 * {@code null}, so its slide renders those tokens blank rather than invented.
	 *
	 * @param input the tactic's overview and breakdown conclusions
	 * @param brief free-text campaign brief the thoughts must stay faithful to
	 * @return the tactic's thoughts, or {@code null} when the call produced no usable reply
	 */
	TacticThoughts tacticThoughts(TacticThoughtsInput input, String brief);

	/**
	 * Step 4 of the restructured slides-from-sheet flow — the campaign-level results call. It fills the
	 * campaign-wide result copy: {@code {{Our results overview N}}}, {@code {{thoughts on the performance N}}},
	 * the optimization {@code {{recommendation N}}}/{@code {{recommendation N text}}} pairs, and the frequency
	 * narrative. It reasons over the per-tactic digests — each tactic's Step-3 thoughts where available, otherwise
	 * its overview plus a short breakdown digest — never over raw grids.
	 *
	 * <p>The returned {@link ClaudeResults} intentionally carries an empty {@code tacticOverviews} map: the
	 * overviews are produced by {@link #batchTacticConclusions} in Step 2, and the caller merges them into the
	 * aggregate before handing it to the placeholder resolver. On any failure an empty results DTO is returned so
	 * the deck falls back to sheet values rather than blanking worse than before.
	 *
	 * @param data        parsed campaign data supplying the shared campaign context
	 * @param brief       free-text campaign brief the copy must stay faithful to
	 * @param frequencies pre-computed planned/actual frequency figures embedded in the frequency narrative
	 * @param perTactic   one digest per tactic (Step-3 thoughts, or overview + breakdown digest as fallback)
	 * @return the campaign-level results copy with an empty tactic-overview map, or an empty DTO on failure
	 */
	ClaudeResults batchCampaignResults(
			CampaignData data, String brief, CampaignFrequencies frequencies, List<TacticNarrativeDigest> perTactic);

	/**
	 * Step 5 of the restructured slides-from-sheet flow — the campaign-level final alignment/trim pass. Like
	 * {@link #batchAlignNarrative}, it harmonizes the Batch A strategic copy and the campaign-level result copy
	 * into one brief-faithful storyline and enforces the character limits, informed by a read-only breakdown
	 * digest. Purely additive: on any failure the originals are returned verbatim.
	 *
	 * @param strategic       the Batch A output to align ({@code proposalOverview} + {@code strategicInsights})
	 * @param results         the campaign-level results to align ({@code resultsOverviews} + performance thoughts
	 *                        + frequency); tactic overviews and recommendations pass through untouched
	 * @param breakdownDigest one short line per per-tactic breakdown conclusion, used as read-only context
	 * @param brief           free-text campaign brief the aligned narrative must stay faithful to
	 * @param reportingPeriod the window the report covers, as shown on the deck (the deck's flight-dates
	 *                        label). Only the end-of-month wording reads it, so its copy can name the
	 *                        reporting month; the end-of-campaign wording ignores it
	 * @return the aligned strategic + results records, or the originals verbatim when nothing could be aligned
	 */
	ClaudeNarrative batchAlignCampaign(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief,
			String reportingPeriod);

	/**
	 * Geo-tab → short ≤40-char comma-separated location string (or null).
	 */
	String summarizeGeo(List<List<String>> geoRows);

	/**
	 * Per-tactic goals → short comma-separated marketing-funnel-stage string (e.g.
	 * {@code "Awareness, Consideration, Conversion"}), or {@code null}. Used as a fallback when the media
	 * plan carries no explicit funnel/goal column.
	 *
	 * <p>The goals are the reviewed {@code {{tactic n goal}}} values read back from the assembled EOC sheet,
	 * so this runs in the slides-from-sheet step rather than during sheet assembly: the model reads a dozen
	 * short strings the user has already seen, not a scan of the whole source workbook.
	 *
	 * @param tacticGoals the per-tactic goal strings in tactic order; blank entries are ignored
	 * @return the comma-separated funnel-stage line, or {@code null} when no goal carries any text
	 */
	String summarizeFunnelStages(List<String> tacticGoals);

	/**
	 * Free-text campaign brief → compact thesis-style digest, or {@code null} when the brief is blank or the
	 * call fails.
	 *
	 * <p>The brief is user-pasted and unbounded, and every narrative batch repeats it as context. Digesting
	 * it once keeps the campaign facts the copy must stay faithful to while paying for the full text only
	 * once. The digest is written into the EOC sheet's {@code {{RFP info}}} field, so the slides-from-sheet
	 * step reads it back instead of digesting again — and the user can edit it like any other sheet value.
	 *
	 * @param brief the free-text campaign brief, optionally with its change-log section appended
	 * @return the digest, or {@code null} when the caller should fall back to the raw brief
	 */
	String digestBrief(String brief);

	/**
	 * Bounds a brief before it becomes prompt context: text already inside the digest budget is returned
	 * unchanged, anything longer is digested by {@link #digestBrief}.
	 *
	 * <p>Step 1 digests the brief and writes the digest into the sheet, so the slides step normally reads back
	 * something already compact. Normally — not always: the sheet's {@code {{RFP info}}} cell is user-editable
	 * and could hold a pasted wall of text, the change log is appended raw and unbounded, and an older sheet
	 * (or a run where Claude was stubbed) carries no digest at all and falls back to the raw brief. Every one of
	 * those paths puts the full text into the cached prefix of every call the run makes, which is exactly what
	 * digesting was meant to avoid — so the slides step re-bounds the text here rather than trusting it.
	 *
	 * @param brief the brief context assembled for the run, optionally with its change log appended
	 * @return text within the digest budget: the input unchanged, its digest, or the input when digesting failed
	 */
	String digestBriefIfOversized(String brief);

	/**
	 * Media plan → single-line primary-KPIs string (e.g. {@code "Imps, CTR, VCR, R&F"}) reflecting the KPIs
	 * relevant to the campaign's tactic mix, or {@code null} when no tactics/KPIs can be inferred.
	 *
	 * @param data parsed campaign data whose tactic mix drives the KPI selection
	 * @return the comma-separated primary-KPIs line, or {@code null}
	 */
	String summarizePrimaryKpis(CampaignData data);
}
