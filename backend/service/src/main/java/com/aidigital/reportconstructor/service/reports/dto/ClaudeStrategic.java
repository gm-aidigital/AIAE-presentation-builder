package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Structured output of Claude Batch A (strategic), carrying the
 * {@code audience_age / audience_segments / proposal_overview / strategic_insights}
 * fields, plus the three north-star fields the EOM deck's second slide is built from.
 * A {@code null} field means the model declined to answer.
 *
 * @param audienceAge       Claude-generated narrative describing the target audience's age profile
 * @param audienceSegments  Claude-generated description of the distinct audience segments for the campaign
 * @param proposalOverview  Claude-generated high-level summary of the marketing proposal
 * @param strategicInsights ordered list of strategic insight items rendered in the report (may be {@code null})
 * @param northStar         the campaign's objective as one upper-cased headline ({@code {{our north star}}});
 *                          EOM only, {@code null} on an end-of-campaign run
 * @param extendedNorthStar the same objective unpacked into geos, audiences and channels
 *                          ({@code {{extended north star}}}); EOM only
 * @param horizon           when the campaign runs, for how long and with what delivery shape
 *                          ({@code {{horizon}}}); EOM only
 * @param pacingTakeaways   one key takeaway per pacing-dashboard slide, in slide order
 *                          ({@code {{pacing dash takeaway 1..4}}}); EOM only, empty on an end-of-campaign run
 * @param performanceTakeaways one key takeaway per performance-vs-plan slide, in slide order
 *                          ({@code {{performance dash takeaway 1..4}}}); EOM only, empty on an
 *                          end-of-campaign run
 * @param whatWeDid         the observation → action → expected impact chains of the EOM "what we did this
 *                          month" slide, in column order ({@code {{observation 1..3}}} and their action and
 *                          impact pairs); EOM only, empty on an end-of-campaign run
 * @param updatedProjection where the campaign lands at the end of the flight at the current pace, and
 *                          whether that clears the final KPI ({@code {{updated projection}}}); EOM only,
 *                          {@code null} on an end-of-campaign run
 * @param focusNextMonth    the carry-forward / pivot / test columns of the EOM "focus next month" slide;
 *                          EOM only, {@code null} on an end-of-campaign run
 */
public record ClaudeStrategic(
		String audienceAge,
		String audienceSegments,
		String proposalOverview,
		List<StrategicInsight> strategicInsights,
		String northStar,
		String extendedNorthStar,
		String horizon,
		List<String> pacingTakeaways,
		List<String> performanceTakeaways,
		List<WhatWeDidStep> whatWeDid,
		String updatedProjection,
		FocusNextMonth focusNextMonth
) {

	/**
	 * Backward-compatible constructor for the calls that carry both sets of dashboard takeaways but no
	 * "what we did" chains and no forward-looking copy — every call but the end-of-month alignment pass,
	 * which is the only one that has the written conclusions those are drawn from — leaving those slots
	 * empty so they render as dashes.
	 *
	 * @param audienceAge          Claude-generated narrative describing the target audience's age profile
	 * @param audienceSegments     Claude-generated description of the distinct audience segments
	 * @param proposalOverview     Claude-generated high-level summary of the marketing proposal
	 * @param strategicInsights    ordered list of strategic insight items (may be {@code null})
	 * @param northStar            the campaign's objective as one upper-cased headline
	 * @param extendedNorthStar    the same objective unpacked into geos, audiences and channels
	 * @param horizon              when the campaign runs, for how long and with what delivery shape
	 * @param pacingTakeaways      one key takeaway per pacing-dashboard slide, in slide order
	 * @param performanceTakeaways one key takeaway per performance-vs-plan slide, in slide order
	 */
	public ClaudeStrategic(
			String audienceAge, String audienceSegments, String proposalOverview,
			List<StrategicInsight> strategicInsights, String northStar, String extendedNorthStar,
			String horizon, List<String> pacingTakeaways, List<String> performanceTakeaways) {
		this(audienceAge, audienceSegments, proposalOverview, strategicInsights, northStar, extendedNorthStar,
				horizon, pacingTakeaways, performanceTakeaways, List.of(), null, null);
	}

	/**
	 * Backward-compatible constructor for the calls that carry pacing takeaways but no performance ones,
	 * leaving the performance-dashboard slots empty so they render as dashes.
	 *
	 * @param audienceAge       Claude-generated narrative describing the target audience's age profile
	 * @param audienceSegments  Claude-generated description of the distinct audience segments
	 * @param proposalOverview  Claude-generated high-level summary of the marketing proposal
	 * @param strategicInsights ordered list of strategic insight items (may be {@code null})
	 * @param northStar         the campaign's objective as one upper-cased headline
	 * @param extendedNorthStar the same objective unpacked into geos, audiences and channels
	 * @param horizon           when the campaign runs, for how long and with what delivery shape
	 * @param pacingTakeaways   one key takeaway per pacing-dashboard slide, in slide order
	 */
	public ClaudeStrategic(
			String audienceAge, String audienceSegments, String proposalOverview,
			List<StrategicInsight> strategicInsights, String northStar, String extendedNorthStar,
			String horizon, List<String> pacingTakeaways) {
		this(audienceAge, audienceSegments, proposalOverview, strategicInsights, northStar, extendedNorthStar,
				horizon, pacingTakeaways, List.of(), List.of(), null, null);
	}

	/**
	 * Backward-compatible constructor for the calls that carry north-star copy but no pacing takeaways,
	 * leaving the dashboard slots empty so they render as dashes.
	 *
	 * @param audienceAge       Claude-generated narrative describing the target audience's age profile
	 * @param audienceSegments  Claude-generated description of the distinct audience segments
	 * @param proposalOverview  Claude-generated high-level summary of the marketing proposal
	 * @param strategicInsights ordered list of strategic insight items (may be {@code null})
	 * @param northStar         the campaign's objective as one upper-cased headline
	 * @param extendedNorthStar the same objective unpacked into geos, audiences and channels
	 * @param horizon           when the campaign runs, for how long and with what delivery shape
	 */
	public ClaudeStrategic(
			String audienceAge, String audienceSegments, String proposalOverview,
			List<StrategicInsight> strategicInsights, String northStar, String extendedNorthStar,
			String horizon) {
		this(audienceAge, audienceSegments, proposalOverview, strategicInsights, northStar, extendedNorthStar,
				horizon, List.of(), List.of(), List.of(), null, null);
	}

	/**
	 * Backward-compatible constructor for the calls that carry no north-star copy — every end-of-campaign
	 * batch, and every empty-DTO failure path — leaving the three EOM fields {@code null} so those tokens
	 * render as dashes rather than stale text.
	 *
	 * @param audienceAge       Claude-generated narrative describing the target audience's age profile
	 * @param audienceSegments  Claude-generated description of the distinct audience segments
	 * @param proposalOverview  Claude-generated high-level summary of the marketing proposal
	 * @param strategicInsights ordered list of strategic insight items (may be {@code null})
	 */
	public ClaudeStrategic(
			String audienceAge, String audienceSegments, String proposalOverview,
			List<StrategicInsight> strategicInsights) {
		this(audienceAge, audienceSegments, proposalOverview, strategicInsights, null, null, null, List.of(),
				List.of(), List.of(), null, null);
	}
}
