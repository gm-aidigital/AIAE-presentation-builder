package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured output of Claude Batch C (results), carrying the
 * {@code results_overview / thoughts_on_performance / tactic_overviews /
 * optimization_recommendations} fields. {@code thoughtsOnPerformance} holds up
 * to 4 elements; {@code tacticOverviews} is keyed by 1-based tactic number;
 * {@code recommendations} holds up to 4 forward-looking optimization items.
 *
 * @param resultsOverview       Claude-generated narrative summarizing the campaign's overall results
 * @param thoughtsOnPerformance up to 4 Claude-generated performance commentary bullets
 * @param tacticOverviews       Claude-generated per-tactic narrative overviews, keyed by 1-based tactic number
 * @param recommendations       up to 4 Claude-generated forward-looking optimization recommendations
 * @param fOpportunity          Claude-generated {@code {{f_oppartunity}}} frequency-opportunity copy (≤180 chars), or
 *                              {@code null}
 * @param fFact                 Claude-generated {@code {{f_fact}}} actual-frequency copy (≤140 chars), or {@code null}
 * @param fStorytelling         Claude-generated {@code {{f_storytelling}}} frequency-storytelling copy (≤320 chars), or
 *                              {@code null}
 */
public record ClaudeResults(
		String resultsOverview,
		List<String> thoughtsOnPerformance,
		Map<Integer, String> tacticOverviews,
		List<Recommendation> recommendations,
		String fOpportunity,
		String fFact,
		String fStorytelling
) {

}
