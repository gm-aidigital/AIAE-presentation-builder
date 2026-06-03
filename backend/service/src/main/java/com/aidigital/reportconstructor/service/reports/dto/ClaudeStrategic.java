package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Structured output of Claude Batch A (strategic) — port of the
 * {@code audience_age / audience_segments / proposal_overview / strategic_insights}
 * JSON contract in PHP {@code claude_api.php}. {@code null} fields mean the model
 * declined to answer (PHP {@code null}).
 */
public record ClaudeStrategic(
    String audienceAge,
    String audienceSegments,
    String proposalOverview,
    List<StrategicInsight> strategicInsights
) {
    public record StrategicInsight(String point, String overview) {}

    public static ClaudeStrategic empty() {
        return new ClaudeStrategic(null, null, null, List.of());
    }
}
