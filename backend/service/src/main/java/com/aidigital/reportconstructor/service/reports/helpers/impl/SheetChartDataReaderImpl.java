package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PacingHeader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/** Anchor label for a tactic's daily pacing block, capturing the trailing tactic number. */
	private static final Pattern DAILY_ANCHOR = Pattern.compile("(?i)^daily pacing\\s+(\\d+)$");

	/** Anchor label for a tactic's monthly pacing block, capturing the trailing tactic number. */
	private static final Pattern MONTHLY_ANCHOR = Pattern.compile("(?i)^monthly pacing\\s+(\\d+)$");

	/** How many rows below a block's anchor cell to search for its header row. */
	private static final int SEARCH_ROWS_DOWN = 12;

	/** How many columns right of a block's anchor cell to search for its header cells. */
	private static final int SEARCH_COLS_RIGHT = 8;

	private final SheetRowHelper rows;
	private final ReportNumberParser numbers;

	@Override
	public SheetChartData read(List<List<String>> grid, int tacticCount, Map<Integer, String> tacticKpiTypes) {
		Map<Integer, Pivot> daily = new LinkedHashMap<>();
		Map<Integer, Pivot> monthly = new LinkedHashMap<>();
		if (grid == null || grid.isEmpty()) {
			return new SheetChartData(daily, monthly);
		}
		Map<Integer, int[]> dailyAnchors = findNumberedAnchors(grid, DAILY_ANCHOR);
		Map<Integer, int[]> monthlyAnchors = findNumberedAnchors(grid, MONTHLY_ANCHOR);
		int count = Math.clamp(tacticCount, 0, 7);
		for (int n = 1; n <= count; n++) {
			String kpiType = tacticKpiTypes == null ? null : tacticKpiTypes.get(n);
			daily.put(n, readPivot(grid, dailyAnchors.get(n), kpiType));
			monthly.put(n, readPivot(grid, monthlyAnchors.get(n), kpiType));
		}
		return new SheetChartData(daily, monthly);
	}

	/**
	 * Reconstructs one pacing pivot for the block anchored at {@code anchor}. Locates the block's header
	 * row (Date / Impressions / Amount) within a bounded window below/right of the anchor — the pacing
	 * writer overwrites the {@code {{tactic n ...}}} marker cells with the data, so the surviving header
	 * labels, not the markers, anchor the read-back — then reads the value rows beneath that header until
	 * the first blank date. The single metric column is mapped to clicks or completions per {@code kpiType}.
	 *
	 * @param grid    the workbook grid
	 * @param anchor  the block's {@code "Daily/Monthly pacing N"} anchor cell {@code [row, col]}, or {@code null}
	 * @param kpiType tactic KPI type deciding the metric's slot, or {@code null}
	 * @return the reconstructed pivot; empty when the anchor or its header row is absent
	 */
	Pivot readPivot(List<List<String>> grid, int[] anchor, String kpiType) {
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		if (anchor == null) {
			return new Pivot(data, false, false);
		}
		PacingHeader header = findHeader(grid, anchor);
		if (header == null) {
			return new Pivot(data, false, false);
		}

		boolean ctr = KPI_CTR.equalsIgnoreCase(kpiType);
		boolean vcr = KPI_VCR.equalsIgnoreCase(kpiType);
		boolean anyMetric = false;

		for (int r = header.headerRow() + 1; r < grid.size(); r++) {
			String label = rows.cellAt(grid.get(r), header.dateCol());
			if (label.isBlank()) {
				break;
			}
			double imps = header.impsCol() < 0
					? 0.0 : numbers.parseReportNumber(rows.cellAt(grid.get(r), header.impsCol()));
			double metric = header.metricCol() < 0
					? 0.0 : numbers.parseReportNumber(rows.cellAt(grid.get(r), header.metricCol()));
			if (metric != 0.0) {
				anyMetric = true;
			}
			data.put(label, new double[] {imps, ctr ? metric : 0.0, vcr ? metric : 0.0});
		}
		return new Pivot(data, ctr && anyMetric, vcr && anyMetric);
	}

	/**
	 * Locates a pacing block's header row and its Date/Impressions/Amount columns within a bounded
	 * window below/right of the block anchor. The header row is the first row inside the window whose
	 * anchor-column window carries a {@code "Date"} cell; Impressions/Amount are then taken from that
	 * same row, leftmost first, so a neighbouring block sharing the window's rightmost columns is ignored.
	 *
	 * @param grid   the workbook grid
	 * @param anchor the block's anchor cell {@code [row, col]}
	 * @return the resolved header, or {@code null} when no Date header is found in the window
	 */
	PacingHeader findHeader(List<List<String>> grid, int[] anchor) {
		int rowTo = Math.min(grid.size(), anchor[0] + SEARCH_ROWS_DOWN);
		for (int r = anchor[0]; r < rowTo; r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			int colTo = Math.min(row.size(), anchor[1] + SEARCH_COLS_RIGHT);
			int dateCol = -1;
			int impsCol = -1;
			int metricCol = -1;
			for (int c = anchor[1]; c < colTo; c++) {
				String cell = rows.cellAt(row, c);
				if (dateCol < 0 && "date".equalsIgnoreCase(cell)) {
					dateCol = c;
				} else if (impsCol < 0 && "impressions".equalsIgnoreCase(cell)) {
					impsCol = c;
				} else if (metricCol < 0 && isMetricHeader(cell)) {
					metricCol = c;
				}
			}
			if (dateCol >= 0) {
				return new PacingHeader(r, dateCol, impsCol, metricCol);
			}
		}
		return null;
	}

	/**
	 * Reports whether a header cell labels the block's single metric (clicks/completions) column.
	 *
	 * @param cell the header cell text
	 * @return {@code true} when the cell is the {@code Amount}/{@code Clicks}/{@code Completions} header
	 */
	boolean isMetricHeader(String cell) {
		String lower = cell.toLowerCase(Locale.ROOT);
		return lower.equals("amount") || lower.equals("clicks") || lower.equals("completions");
	}

	/**
	 * Finds every {@code "<label> N"} block anchor in the grid, keyed by the captured tactic number.
	 *
	 * @param grid    the workbook grid
	 * @param pattern the anchor label pattern with a single digit capturing group
	 * @return tactic number (1-based) to its anchor cell's zero-based {@code [row, col]}
	 */
	Map<Integer, int[]> findNumberedAnchors(List<List<String>> grid, Pattern pattern) {
		Map<Integer, int[]> anchors = new LinkedHashMap<>();
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				Matcher m = pattern.matcher(rows.cellAt(row, c));
				if (m.matches()) {
					anchors.putIfAbsent(Integer.parseInt(m.group(1)), new int[] {r, c});
				}
			}
		}
		return anchors;
	}
}
