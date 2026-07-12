package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;

import java.util.List;
import java.util.Map;

/**
 * Reconstructs the per-tactic daily/monthly pacing {@code Pivot}s from a filled (and possibly
 * user-edited) EOC workbook grid, so the "Slides from Sheet" flow can build the pacing charts
 * from the sheet the user reviewed rather than re-querying BigQuery.
 *
 * <p>The pacing writer overwrites each block's {@code {{tactic n ...}}} marker cells with the data
 * itself, so the markers do not survive into the filled workbook. Columns are therefore located by
 * the block's {@code "Daily pacing N"} / {@code "Monthly pacing N"} anchor label and its surviving
 * {@code Date} / {@code Impressions} / {@code Amount} header cells — no fixed cell references, so it
 * survives rows or columns the user inserts.
 */
public interface SheetChartDataReader {

	/**
	 * Reads the daily and monthly pacing pivots for every active tactic.
	 *
	 * @param grid            the filled workbook's first tab, as trimmed cell strings (may be {@code null})
	 * @param tacticCount     number of active tactics to read (1..28)
	 * @param tacticKpiTypes  tactic number &rarr; KPI type ({@code "ctr"}/{@code "vcr"}, else {@code null}),
	 *                        deciding whether the single metric column is read as clicks or completions
	 * @return the reconstructed daily and monthly pivots; a tactic whose block is absent yields an empty pivot
	 */
	SheetChartData read(List<List<String>> grid, int tacticCount, Map<Integer, String> tacticKpiTypes);
}
