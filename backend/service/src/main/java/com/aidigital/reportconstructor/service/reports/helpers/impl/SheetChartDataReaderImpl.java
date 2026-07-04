package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spring bean implementation of {@link SheetChartDataReader}. Pure logic over the cell grid —
 * no Google client dependency — so it is unit-testable in isolation.
 */
@Component
@RequiredArgsConstructor
public class SheetChartDataReaderImpl implements SheetChartDataReader {

	/** KPI type whose single metric column carries clicks (display/social → CTR rate series). */
	private static final String KPI_CTR = "ctr";

	/** KPI type whose single metric column carries completions (video/CTV → VCR rate series). */
	private static final String KPI_VCR = "vcr";

	private final SheetRowHelper rows;
	private final ReportNumberParser numbers;

	@Override
	public SheetChartData read(List<List<String>> grid, int tacticCount, Map<Integer, String> tacticKpiTypes) {
		Map<Integer, Pivot> daily = new LinkedHashMap<>();
		Map<Integer, Pivot> monthly = new LinkedHashMap<>();
		if (grid == null || grid.isEmpty()) {
			return new SheetChartData(daily, monthly);
		}
		int count = Math.clamp(tacticCount, 0, 7);
		for (int n = 1; n <= count; n++) {
			String kpiType = tacticKpiTypes == null ? null : tacticKpiTypes.get(n);
			daily.put(n, readPivot(grid,
					"{{tactic " + n + " date}}", "{{tactic " + n + " impressions}}",
					"{{tactic " + n + " amount}}", kpiType));
			monthly.put(n, readPivot(grid,
					"{{tactic " + n + " date mon}}", "{{tactic " + n + " impressions mon}}",
					"{{tactic " + n + " amount mon}}", kpiType));
		}
		return new SheetChartData(daily, monthly);
	}

	/**
	 * Reconstructs one pacing pivot: finds the date/impressions/metric marker cells, then reads the
	 * value rows written directly beneath the date marker until the first blank date. The single metric
	 * column is mapped to clicks or completions per {@code kpiType}.
	 *
	 * @param grid        the workbook grid
	 * @param dateToken   the block's date marker token
	 * @param impsToken   the block's impressions marker token
	 * @param metricToken the block's metric (amount) marker token
	 * @param kpiType     tactic KPI type deciding the metric's slot, or {@code null}
	 * @return the reconstructed pivot; empty when the date marker is absent
	 */
	Pivot readPivot(List<List<String>> grid, String dateToken, String impsToken, String metricToken, String kpiType) {
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		int[] dateAt = findToken(grid, dateToken);
		if (dateAt == null) {
			return new Pivot(data, false, false);
		}
		int dateCol = dateAt[1];
		int impsCol = columnOf(findToken(grid, impsToken));
		int metricCol = columnOf(findToken(grid, metricToken));

		boolean ctr = KPI_CTR.equalsIgnoreCase(kpiType);
		boolean vcr = KPI_VCR.equalsIgnoreCase(kpiType);
		boolean anyMetric = false;

		for (int r = dateAt[0] + 1; r < grid.size(); r++) {
			String label = rows.cellAt(grid.get(r), dateCol);
			if (label.isBlank()) {
				break;
			}
			double imps = impsCol < 0 ? 0.0 : numbers.parseReportNumber(rows.cellAt(grid.get(r), impsCol));
			double metric = metricCol < 0 ? 0.0 : numbers.parseReportNumber(rows.cellAt(grid.get(r), metricCol));
			if (metric != 0.0) {
				anyMetric = true;
			}
			data.put(label, new double[] {imps, ctr ? metric : 0.0, vcr ? metric : 0.0});
		}
		return new Pivot(data, ctr && anyMetric, vcr && anyMetric);
	}

	/**
	 * Finds the first cell equal (case-insensitively) to the given token.
	 *
	 * @param grid  the workbook grid
	 * @param token the marker token to locate
	 * @return the token's zero-based {@code [row, col]}, or {@code null} when absent
	 */
	int[] findToken(List<List<String>> grid, String token) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				if (token.equalsIgnoreCase(rows.cellAt(row, c))) {
					return new int[] {r, c};
				}
			}
		}
		return null;
	}

	/**
	 * Returns the column of a located marker, or {@code -1} when the marker was absent.
	 *
	 * @param at the marker's {@code [row, col]}, or {@code null}
	 * @return the column index, or {@code -1}
	 */
	int columnOf(int[] at) {
		return at == null ? -1 : at[1];
	}
}
