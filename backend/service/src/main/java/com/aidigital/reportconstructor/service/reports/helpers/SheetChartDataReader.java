package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;

import java.util.List;
import java.util.Map;

/**
 * Reconstructs the per-tactic daily/monthly pacing {@code Pivot}s from a filled (and possibly
 * user-edited) EOC workbook grid, so the "Slides from Sheet" flow can build the pacing charts
 * from the sheet the user reviewed rather than re-querying BigQuery.
 *
 * <p>Columns are located by the {@code {{tactic n date}}} / {@code {{tactic n impressions}}} /
 * {@code {{tactic n amount}}} marker cells the sheet-fill step leaves in each block's header row
 * (monthly blocks carry the {@code mon} suffix). The pacing writer places its data <em>below</em>
 * those markers, so the same markers anchor the read-back — no fixed cell references, so it
 * survives rows or columns the user inserts.
 */
public interface SheetChartDataReader {

	/**
	 * Reads the daily and monthly pacing pivots for every active tactic.
	 *
	 * @param grid            the filled workbook's first tab, as trimmed cell strings (may be {@code null})
	 * @param tacticCount     number of active tactics to read (1..7)
	 * @param tacticKpiTypes  tactic number &rarr; KPI type ({@code "ctr"}/{@code "vcr"}, else {@code null}),
	 *                        deciding whether the single metric column is read as clicks or completions
	 * @return the reconstructed daily and monthly pivots; a tactic whose block is absent yields an empty pivot
	 */
	SheetChartData read(List<List<String>> grid, int tacticCount, Map<Integer, String> tacticKpiTypes);
}
