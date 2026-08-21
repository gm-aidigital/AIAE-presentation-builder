package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's context for the channel-slide pacing narrative call: who the channel is, what it is judged
 * on, and the METRIC table the narrative sits above. Assembled from the placeholder map after every figure
 * on the slide has been resolved, so the call reasons over exactly what the reader will see.
 *
 * @param tacticNum the 1-based tactic number
 * @param tacticName the channel's display name ({@code "CTV"}, {@code "Meta"}, …), may be {@code null}
 * @param kpiType    the KPI the tactic is judged on ({@code "CTR"}, {@code "VCR"}, …), may be {@code null}
 * @param metrics    the slide's METRIC rows in table order, never {@code null}
 */
public record TacticPacingInput(
		int tacticNum,
		String tacticName,
		String kpiType,
		List<TacticPacingMetric> metrics
) {
}
