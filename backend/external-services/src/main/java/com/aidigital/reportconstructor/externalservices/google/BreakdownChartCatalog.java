package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Chart-source spreadsheet ids and in-sheet chart ids for the per-tactic audience/device breakdown
 * charts — bound from {@code external.google.breakdown-charts.*} and keyed by {@link
 * com.aidigital.reportconstructor.service.reports.dto.BreakdownType} wire code ({@code "aud"},
 * {@code "dev"}).
 *
 * <p>Separate from {@link ChartTemplateCatalog}: the main-deck charts share one in-sheet chart id, but
 * each breakdown source workbook carries its own chart, so the chart id is per code here. Defaults are
 * empty, which disables the feature as a safe no-op.
 */
@Component
@ConfigurationProperties(prefix = "external.google.breakdown-charts")
public class BreakdownChartCatalog {

	/** Default data-block start row, used when a series configures none: row 1 is the workbook's header. */
	private static final int DEFAULT_DATA_START_ROW = 2;

	private Map<String, String> sourceSheetIds = Map.of();
	private Map<String, String> chartIdInSheet = Map.of();
	private Map<String, Integer> dataStartRow = Map.of();

	/**
	 * Returns the 1-based row each series' data block starts on, for the series whose category labels are
	 * written into the workbook rather than matched against it (see {@code BreakdownChartSeries}). A series
	 * absent from the map uses row 2 — the row after a single header row.
	 *
	 * @return series code &rarr; the workbook's first data row
	 */
	public Map<String, Integer> getDataStartRow() {
		return dataStartRow;
	}

	/**
	 * Sets the per-series data-block start rows, defaulting to an empty map when null.
	 *
	 * @param dataStartRow series code &rarr; the workbook's first data row (may be null)
	 */
	public void setDataStartRow(Map<String, Integer> dataStartRow) {
		this.dataStartRow = dataStartRow == null ? Map.of() : dataStartRow;
	}

	/**
	 * Resolves the data-block start row for one series, falling back to row 2.
	 *
	 * @param seriesCode the chart series' configuration code
	 * @return the 1-based first data row of that series' source workbook
	 */
	public int dataStartRowFor(String seriesCode) {
		Integer configured = dataStartRow.get(seriesCode);
		return configured == null || configured < 1 ? DEFAULT_DATA_START_ROW : configured;
	}

	/**
	 * Returns the chart-source template spreadsheet id per breakdown code — the workbook (with its
	 * embedded chart) copied once per tactic and filled with that tactic's impressions.
	 *
	 * @return breakdown wire code &rarr; source template spreadsheet id
	 */
	public Map<String, String> getSourceSheetIds() {
		return sourceSheetIds;
	}

	/**
	 * Sets the chart-source template spreadsheet ids, defaulting to an empty map when null.
	 *
	 * @param sourceSheetIds breakdown wire code &rarr; source template spreadsheet id (may be null)
	 */
	public void setSourceSheetIds(Map<String, String> sourceSheetIds) {
		this.sourceSheetIds = sourceSheetIds == null ? Map.of() : sourceSheetIds;
	}

	/**
	 * Returns the embedded chart's in-sheet id per breakdown code, used to link the source workbook's
	 * chart into the slide. Kept as strings so an empty configuration default binds cleanly; callers parse
	 * to an int and treat a blank/non-numeric value as unconfigured.
	 *
	 * @return breakdown wire code &rarr; chart id inside that code's source workbook, as configured text
	 */
	public Map<String, String> getChartIdInSheet() {
		return chartIdInSheet;
	}

	/**
	 * Sets the in-sheet chart ids, defaulting to an empty map when null.
	 *
	 * @param chartIdInSheet breakdown wire code &rarr; chart id inside that code's source workbook (may be null)
	 */
	public void setChartIdInSheet(Map<String, String> chartIdInSheet) {
		this.chartIdInSheet = chartIdInSheet == null ? Map.of() : chartIdInSheet;
	}
}
