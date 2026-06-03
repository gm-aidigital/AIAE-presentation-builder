package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BasicChartSeries;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.ChartSourceRange;
import com.google.api.services.sheets.v4.model.ChartSpec;
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

    public ChartSpecBuilder(ChartTemplateCatalog templates) {
        this.templates = templates;
    }

    /** Reads the embedded chart spec from the configured in-sheet chart id. */
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

    /** Resolves the data tab name ({@code Sheet1} preferred) on a copied spreadsheet. */
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
        series.add(comboSeries(dataSheetId, rowStart, rowEnd, ChartSheetWriter.IMPS_COL, "COLUMN", "LEFT_AXIS"));
        if (withRate) {
            series.add(comboSeries(dataSheetId, rowStart, rowEnd, ChartSheetWriter.RATE_COL, "LINE", "RIGHT_AXIS"));
        }
        bc.setSeries(series);
        if (bc.getHeaderCount() == null) {
            bc.setHeaderCount(1);
        }
    }

    /** Batch-updates the embedded chart spec on a copied helper spreadsheet. */
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

    private BasicChartSeries comboSeries(int sheetId, int rowStart, int rowEnd, int col,
                                         String type, String targetAxis) {
        GridRange range = new GridRange()
            .setSheetId(sheetId)
            .setStartRowIndex(rowStart)
            .setEndRowIndex(rowEnd)
            .setStartColumnIndex(col)
            .setEndColumnIndex(col + 1);
        return new BasicChartSeries()
            .setSeries(new ChartData().setSourceRange(new ChartSourceRange().setSources(List.of(range))))
            .setType(type)
            .setTargetAxis(targetAxis);
    }

    private Map<String, Object> rgb(double[] c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("red", c[0]);
        m.put("green", c[1]);
        m.put("blue", c[2]);
        return m;
    }
}
