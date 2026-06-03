package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured output of Claude Batch C (results) — port of the
 * {@code results_overview / thoughts_on_performance / tactic_overviews} JSON
 * contract in PHP {@code claude_api.php}. {@code thoughtsOnPerformance} holds up
 * to 4 elements; {@code tacticOverviews} is keyed by 1-based tactic number.
 */
public record ClaudeResults(
    String resultsOverview,
    List<String> thoughtsOnPerformance,
    Map<Integer, String> tacticOverviews
) {
    public static ClaudeResults empty() {
        return new ClaudeResults(null, List.of(), Map.of());
    }
}
