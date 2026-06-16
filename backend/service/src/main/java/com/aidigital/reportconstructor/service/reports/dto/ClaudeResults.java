package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured output of Claude Batch C (results), carrying the
 * {@code results_overview / thoughts_on_performance / tactic_overviews} fields.
 * {@code thoughtsOnPerformance} holds up to 4 elements; {@code tacticOverviews}
 * is keyed by 1-based tactic number.
 *
 * @param resultsOverview       Claude-generated narrative summarizing the campaign's overall results
 * @param thoughtsOnPerformance up to 4 Claude-generated performance commentary bullets
 * @param tacticOverviews       Claude-generated per-tactic narrative overviews, keyed by 1-based tactic number
 */
public record ClaudeResults(
		String resultsOverview,
		List<String> thoughtsOnPerformance,
		Map<Integer, String> tacticOverviews
) {

}
