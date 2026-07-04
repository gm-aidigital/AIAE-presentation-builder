package com.aidigital.reportconstructor.service.reports.dto;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;

import java.util.Map;

/**
 * Per-tactic pacing series read back out of a filled EOC workbook, so the "Slides from Sheet"
 * flow can drive the daily/monthly pacing charts straight from the numbers the user reviewed
 * instead of re-querying BigQuery. Each value mirrors the {@link Pivot} the chart pipeline would
 * otherwise compute from the raw export.
 *
 * <p>The distribution (pie) charts are not carried here: they are built from the scalar
 * {@code {{tactic n imps}}} / {@code {{total imps}}} placeholders the resolver already reads.
 *
 * @param dailyPivots   tactic number (1-based) &rarr; its daily pacing pivot
 * @param monthlyPivots tactic number (1-based) &rarr; its monthly pacing pivot
 */
public record SheetChartData(
		Map<Integer, Pivot> dailyPivots,
		Map<Integer, Pivot> monthlyPivots
) {

}
