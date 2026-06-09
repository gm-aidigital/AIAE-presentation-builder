package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Structured output of Claude Batch A (strategic), carrying the
 * {@code audience_age / audience_segments / proposal_overview / strategic_insights}
 * fields. A {@code null} field means the model declined to answer.
 *
 * @param audienceAge Claude-generated narrative describing the target audience's age profile
 * @param audienceSegments Claude-generated description of the distinct audience segments for the campaign
 * @param proposalOverview Claude-generated high-level summary of the marketing proposal
 * @param strategicInsights ordered list of strategic insight items rendered in the report (may be {@code null})
 */
public record ClaudeStrategic(
    String audienceAge,
    String audienceSegments,
    String proposalOverview,
    List<StrategicInsight> strategicInsights
) {
    /**
     * A single strategic insight entry, pairing a headline point with its supporting explanation.
     *
     * @param point short headline or title of the strategic insight
     * @param overview supporting detail or explanation backing the insight point
     */
    public record StrategicInsight(String point, String overview) {
        // required
    }
}
