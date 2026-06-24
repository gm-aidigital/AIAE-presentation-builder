package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BasicChartSeries;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.ChartSourceRange;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ColorStyle;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.PieChartSpec;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateChartSpecRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and re-applies embedded chart specs from helper spreadsheets (combo + pie).
 */
@Component
public class ChartSpecBuilder {

	private static final String CHART_DATA_TAB = "Sheet1";

	private final ChartTemplateCatalog templates;

	/**
	 * Creates the builder backed by the catalog of chart template ids and default styling.
	 *
	 * @param templates catalog supplying the in-sheet chart id and the pie default color matrix
	 */
	public ChartSpecBuilder(ChartTemplateCatalog templates) {
		this.templates = templates;
	}

	/**
	 * Reads the embedded chart spec matching the configured in-sheet chart id from the spreadsheet.
	 *
	 * @param sheets        authenticated Google Sheets API client used to fetch the spreadsheet
	 * @param spreadsheetId id of the helper spreadsheet whose embedded charts are scanned
	 * @return the matching chart's {@link ChartSpec}, or {@code null} if no sheet/chart matches the configured id
	 * @throws IOException if the Sheets API request fails
	 */
	public ChartSpec readChartSpec(Sheets sheets, String spreadsheetId) throws IOException {
		Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId)
				.setIncludeGridData(false)
				.setFields("sheets(properties(sheetId,title),charts(chartId,spec))")
				.execute();
		if (ss.getSheets() == null) {
			return null;
		}
		int chartId = templates.getChartIdInSheet();
		for (Sheet s : ss.getSheets()) {
			if (s.getCharts() == null) {
				continue;
			}
			for (EmbeddedChart chart : s.getCharts()) {
				if (chart.getChartId() != null && chart.getChartId() == chartId) {
					return chart.getSpec();
				}
			}
		}
		return null;
	}

	/**
	 * Resolves the data tab name ({@code Sheet1} preferred) on a copied spreadsheet.
	 *
	 * @param sheets        authenticated Google Sheets API client used to list sheet titles
	 * @param spreadsheetId id of the copied spreadsheet whose tabs are inspected
	 * @return {@code "Sheet1"} when present, otherwise the title of the first tab, falling back to {@code "Sheet1"}
	 * when no tab has a title
	 * @throws IOException if the Sheets API request fails
	 */
	public String findDataTab(Sheets sheets, String spreadsheetId) throws IOException {
		Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId)
				.setIncludeGridData(false)
				.setFields("sheets.properties.title")
				.execute();
		String first = null;
		if (ss.getSheets() != null) {
			for (Sheet s : ss.getSheets()) {
				String title = s.getProperties() == null ? null : s.getProperties().getTitle();
				if (title == null) {
					continue;
				}
				if (first == null) {
					first = title;
				}
				if (CHART_DATA_TAB.equals(title)) {
					return CHART_DATA_TAB;
				}
			}
		}
		return first == null ? CHART_DATA_TAB : first;
	}

	/**
	 * Re-creates the data series the COMBO templates are missing. Reuses the
	 * template domain's row range, retargets every source range at the copy's
	 * data tab, and adds Impressions (columns / left axis) plus, when a metric
	 * exists, the CTR/VCR rate (line / right axis).
	 *
	 * @param spec        the combo chart spec to mutate in place; a {@code null} spec or one without basic-chart
	 *                       domains is ignored
	 * @param dataSheetId sheet id of the copy's data tab that every series and domain source range is retargeted to
	 * @param withRate    when {@code true}, also adds the CTR/VCR rate series as a line on the right axis
	 */
	public void injectComboSeries(ChartSpec spec, int dataSheetId, boolean withRate) {
		if (spec == null) {
			return;
		}
		BasicChartSpec bc = spec.getBasicChart();
		if (bc == null || bc.getDomains() == null || bc.getDomains().isEmpty()) {
			return;
		}
		int rowStart = 0;
		int rowEnd = 50;
		if (bc.getDomains().getFirst().getDomain() != null
				&& bc.getDomains().get(0).getDomain().getSourceRange() != null) {
			ChartSourceRange domSrc = bc.getDomains().get(0).getDomain().getSourceRange();
			if (domSrc.getSources() != null && !domSrc.getSources().isEmpty()) {
				GridRange g = domSrc.getSources().get(0);
				if (g.getStartRowIndex() != null) {
					rowStart = g.getStartRowIndex();
				}
				if (g.getEndRowIndex() != null) {
					rowEnd = g.getEndRowIndex();
				}
				g.setSheetId(dataSheetId);
			}
		}
		List<BasicChartSeries> series = new ArrayList<>();
		series.add(comboSeries(dataSheetId, rowStart, rowEnd, ChartSheetWriter.IMPS_COL, "COLUMN", "LEFT_AXIS",
				templates.comboColumnColorComponents()));
		if (withRate) {
			series.add(comboSeries(dataSheetId, rowStart, rowEnd, ChartSheetWriter.RATE_COL, "LINE", "RIGHT_AXIS",
					templates.comboLineColorComponents()));
		}
		bc.setSeries(series);
		if (bc.getHeaderCount() == null) {
			bc.setHeaderCount(1);
		}
	}

	/**
	 * Batch-updates the embedded chart spec on a copied helper spreadsheet.
	 *
	 * @param sheets        authenticated Google Sheets API client used to issue the batch update
	 * @param spreadsheetId id of the copied spreadsheet whose embedded chart is replaced
	 * @param spec          the new chart spec applied to the configured in-sheet chart id
	 * @throws IOException if the Sheets API batch update fails
	 */
	public void applyChartSpec(Sheets sheets, String spreadsheetId, ChartSpec spec) throws IOException {
		com.google.api.services.sheets.v4.model.Request req =
				new com.google.api.services.sheets.v4.model.Request().setUpdateChartSpec(
						new UpdateChartSpecRequest()
								.setChartId(templates.getChartIdInSheet())
								.setSpec(spec));
		sheets.spreadsheets().batchUpdate(spreadsheetId,
				new BatchUpdateSpreadsheetRequest().setRequests(List.of(req))).execute();
	}

	/**
	 * Forces pie slice colors into the spec (PHP {@code _injectPieSliceColors}).
	 * The non-standard {@code slices} field may be rejected by the API — callers treat as best-effort.
	 *
	 * @param spec the chart spec to mutate; returned unchanged when it has no pie chart
	 * @return the same {@code spec} instance, with per-slice background colors written (preserving any existing slice
	 * colors, otherwise falling back to the catalog's default color matrix)
	 */
	@SuppressWarnings("unchecked")
	public ChartSpec injectPieSliceColors(ChartSpec spec) {
		PieChartSpec pie = spec.getPieChart();
		if (pie == null) {
			return spec;
		}
		List<Map<String, Object>> colors = new ArrayList<>();
		Object existing = pie.get("slices");
		if (existing instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> m && m.get("backgroundColor") != null) {
					colors.add(Map.of("backgroundColor", m.get("backgroundColor")));
				}
			}
		}
		double[][] defaults = templates.pieDefaultColorMatrix();
		if (colors.isEmpty()) {
			for (double[] c : defaults) {
				colors.add(Map.of("backgroundColor", rgb(c)));
			}
		}

		int sliceCount = Math.max(2, colors.size());
		List<Map<String, Object>> newSlices = new ArrayList<>(sliceCount);
		for (int i = 0; i < sliceCount; i++) {
			Map<String, Object> color = i < colors.size()
					? colors.get(i)
					: Map.of("backgroundColor", rgb(defaults[i % defaults.length]));
			newSlices.add(color);
		}
		pie.set("slices", newSlices);
		return spec;
	}

	/**
	 * Builds one combo-chart data series over a single column of the copy's data tab, pinned to an explicit color so
	 * the chart keeps its brand styling instead of reverting to the sheet's default theme once linked into Slides.
	 *
	 * @param sheetId    sheet id of the copy's data tab the source range points at
	 * @param rowStart   inclusive start row index of the series range
	 * @param rowEnd     exclusive end row index of the series range
	 * @param col        zero-based column index supplying the series values
	 * @param type       the basic-chart series type, e.g. {@code "COLUMN"} or {@code "LINE"}
	 * @param targetAxis the axis the series is plotted against, e.g. {@code "LEFT_AXIS"} or {@code "RIGHT_AXIS"}
	 * @param color      normalized {@code {red, green, blue}} components applied to the series
	 * @return the configured series
	 */
	BasicChartSeries comboSeries(int sheetId, int rowStart, int rowEnd, int col,
	                             String type, String targetAxis, double[] color) {
		GridRange range = new GridRange()
				.setSheetId(sheetId)
				.setStartRowIndex(rowStart)
				.setEndRowIndex(rowEnd)
				.setStartColumnIndex(col)
				.setEndColumnIndex(col + 1);
		Color seriesColor = new Color()
				.setRed((float) color[0])
				.setGreen((float) color[1])
				.setBlue((float) color[2]);
		return new BasicChartSeries()
				.setSeries(new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of(range))))
				.setType(type)
				.setTargetAxis(targetAxis)
				.setColor(seriesColor)
				.setColorStyle(new ColorStyle().setRgbColor(seriesColor));
	}

	Map<String, Object> rgb(double[] c) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("red", c[0]);
		m.put("green", c[1]);
		m.put("blue", c[2]);
		return m;
	}
}
