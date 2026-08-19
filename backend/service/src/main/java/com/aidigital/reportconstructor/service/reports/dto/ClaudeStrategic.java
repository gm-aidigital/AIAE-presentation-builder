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
 */
public record ClaudeStrategic(
		String audienceAge,
		String audienceSegments,
		String proposalOverview,
		List<StrategicInsight> strategicInsights,
		String northStar,
		String extendedNorthStar,
		String horizon
) {

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
		this(audienceAge, audienceSegments, proposalOverview, strategicInsights, null, null, null);
	}
}
