package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured output of Claude Batch C (results), carrying the
 * {@code results_overviews / thoughts_on_performance / tactic_overviews /
 * optimization_recommendations} fields. {@code thoughtsOnPerformance} holds up
 * to 4 elements; {@code tacticOverviews} is keyed by 1-based tactic number;
 * {@code recommendations} holds up to 4 forward-looking optimization items.
 *
 * @param resultsOverviews      Claude-generated results narratives, keyed by 1-based tactic-group number (each group
 *                              covers up to 7 tactics: group 1 → tactics 1–7, group 2 → 8–14, …). One entry per
 *                              tactic group present in the campaign; feeds {@code {{Our results overview N}}}
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
		Map<Integer, String> resultsOverviews,
		List<String> thoughtsOnPerformance,
		Map<Integer, String> tacticOverviews,
		List<Recommendation> recommendations,
		String fOpportunity,
		String fFact,
		String fStorytelling
) {

}
