package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Headers;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Pivot;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider.ChartRequest;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BasicChartSeries;
import com.google.api.services.sheets.v4.model.BasicChartSpec;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ChartData;
import com.google.api.services.sheets.v4.model.ChartSourceRange;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.EmbeddedChart;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.PieChartSpec;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateChartSpecRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.CreateSheetsChartRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.PageElementProperties;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Size;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Live port of PHP {@code api/chart_builder.php}. For every active tactic it
 * builds three charts — Daily Pacing Dynamic, Monthly Impressions Distribution
 * and Weighted Impression Contribution (pie) — by copying the hardcoded helper
 * chart-template spreadsheets, writing the pivoted actuals, re-applying the saved
 * chart spec and replacing the placeholder chart objects on the deck slides with
 * live {@code LINKED} Sheets charts.
 *
 * <p>The hardcoded Google resource IDs (helper spreadsheets, in-sheet chart id,
 * slide chart object ids) are reused verbatim from the PHP engine; they are only
 * valid while the configured {@code SLIDES_TEMPLATE_ID} deck and the helper
 * spreadsheets remain the originals shared with the active principal. When a
 * helper sheet cannot be copied (e.g. not shared with the service account) the
 * failure is reported per-chart, never silently swallowed, and the rest of the
 * deck still completes.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealChartProvider implements ChartProvider {

    private static final String APPLICATION_NAME = "Report Constructor — AI Digital";
    private static final String CHART_DATA_TAB = "Sheet1";

    /** Same in-sheet chart id across every helper template (daily/monthly/dist). */
    private static final int CHART_ID_IN_SHEET = 1087145314;
    // COMBO data-tab layout: A=Date(0) B=Impressions(1) C=CTR/VCR rate(2) D=Clicks/Completions(3).
    private static final int IMPS_COL = 1;
    private static final int RATE_COL = 2;

    private static final Map<Integer, String> DAILY_TEMPLATE_SHEET_IDS = Map.of(
        1, "17da8GFAFRYt5JT6MvPV-qSi6KEd2tmI8xZMGFU-pkK4",
        2, "1bWjf8IlKzZk9TTyHinQ2LWvpUuV9OnT58MXmi8LF5bk",
        3, "1BUaf4mYlxRqHUyoCKJZEUX-Rhdx4wnVQg7ngE7cpUjw",
        4, "1fCNzy7TFCDs4-cgi_6w4lHi8i_N5Hqqz9r07We12elE",
        5, "1ZPVH6LM23phYgSpd-f2u_aP-8xer7gBE6hge4-QVsKM",
        6, "1tgYtK4bXqsSdyRVrF8JNzX2uRsicc95ZM1ReR4hlbcA",
        7, "1Ly2du2g45Hoi0Ad6ej5ZrxONa4yB_DUIeuhAlEMR1Zo"
    );

    private static final Map<Integer, String> DAILY_SLIDE_OBJECT_IDS = Map.of(
        1, "g3e2b29c26c1_0_0",
        2, "g3e2b29c26c1_0_8",
        3, "g3e2b29c26c1_0_9",
        4, "g3e2b29c26c1_0_10",
        5, "g3e2b29c26c1_0_11",
        6, "g3e2b29c26c1_0_12",
        7, "g3e2b29c26c1_0_14"
    );

    private static final Map<Integer, String> MONTHLY_TEMPLATE_SHEET_IDS = Map.of(
        1, "1k8O_reSRTjSNdvQzawfm6kRjCq1G8dy--9bv1B79P1I",
        2, "1YKj5k7juIoZpuuxWA5afr31UK444eTQMx-DEKHSYn8I",
        3, "1TzjEYRwiD6ixTt9H_J8CBI-yGiAQxk9LNMiTEsue3XY",
        4, "1y1BFnlcPcSPCOYHMYZ_DjbeqbLluuLJe7TL6YK9VjLQ",
        5, "1WAcGjCwqQ4OsayGwytaJeXE_1krkt4PMVxWq7BGNA7I",
        6, "1VRsvWp1JULRblQgU0MaHurAxUkM9m0GE34UPTAxq6nI",
        7, "1KWyRqg1FknQBpj0ahgvavZiK1mLlhkvKN8-gU_7FVbI"
    );

    private static final Map<Integer, String> MONTHLY_SLIDE_OBJECT_IDS = Map.of(
        1, "g3e2bb781e03_1_0",
        2, "g3e2bb781e03_1_1",
        3, "g3e2bb781e03_1_2",
        4, "g3e2bb781e03_1_3",
        5, "g3e2bb781e03_1_6",
        6, "g3e2bb781e03_1_7",
        7, "g3e2bb781e03_1_8"
    );

    private static final Map<Integer, String> DIST_TEMPLATE_SHEET_IDS = Map.of(
        1, "1yVZmgYkjH-pZofd87fOLUOESZYFJLOYOlHI2va-2Ego",
        2, "14UC6wUdeEeWYaEtwX5h3e9kta7mvZVgWpx260nKBhp0",
        3, "1mk1F0I6CmXoHs_X726fXZaOHMsQbfQ7uwykZGndlGY4",
        4, "1Um-9cFk85isdCzyoIDh4uxklMN4gHlmG_eCDz_VpELA",
        5, "12PE8dv6iXvQYe1vdp5RNUmnFUvhWQMziKLj_j9qlxzw",
        6, "1W6pyTiwO_bz0ssvYAyMxLx24ry-xGw5SST3ieAOQaBw",
        7, "1ezXCDyelmD0vQK6p-70WQKL4xUmqQNOjF7HfEzVSSos"
    );

    private static final Map<Integer, String> DIST_SLIDE_OBJECT_IDS = Map.of(
        1, "g3e3101095f6_0_0",
        2, "g3e3101095f6_0_1",
        3, "g3e3101095f6_0_2",
        4, "g3e3101095f6_0_3",
        5, "g3e3101095f6_0_4",
        6, "g3e3101095f6_0_5",
        7, "g3e3101095f6_0_6"
    );

    /** Forced pie palette: Teal #2C7D80, Orange #EF7D22. */
    private static final double[][] PIE_DEFAULT_COLORS = {
        {0.173, 0.490, 0.502},
        {0.937, 0.490, 0.133}
    };

    private final GoogleCredentialsFactory creds;

    public RealChartProvider(GoogleCredentialsFactory creds) {
        this.creds = creds;
        log.info("[charts] live chart provider initialised");
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public List<String> buildCharts(ChartRequest req) {
        boolean asUser = req.userGoogleAccessToken() != null && !req.userGoogleAccessToken().isBlank();
        HttpRequestInitializer init = asUser ? userInitializer(req.userGoogleAccessToken()) : creds.initializer();
        Drive drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), init).setApplicationName(APPLICATION_NAME).build();
        Sheets sheets = new Sheets.Builder(creds.transport(), creds.jsonFactory(), init).setApplicationName(APPLICATION_NAME).build();
        Slides slides = new Slides.Builder(creds.transport(), creds.jsonFactory(), init).setApplicationName(APPLICATION_NAME).build();

        log.info("[charts] presentation {} → building charts for {} tactic(s) under {}",
            req.presentationId(), req.tacticCount(), asUser ? "the signed-in user" : "the service account");

        List<String> errors = new ArrayList<>();

        // One Drive folder for all chart copies (best-effort; copies still link if it fails).
        String folderId = null;
        try {
            folderId = createFolder(drive, "Charts — " + req.campaignTitle());
        } catch (IOException ex) {
            log.warn("[charts] could not create chart folder, copies go to root: {}", ex.getMessage());
        }

        Headers headers = ChartPivot.parseBqHeaders(req.bqRows());

        errors.addAll(buildDailyCharts(drive, sheets, slides, req, headers, folderId));
        errors.addAll(buildMonthlyCharts(drive, sheets, slides, req, headers, folderId));
        errors.addAll(buildDistributionCharts(drive, sheets, slides, req, folderId));

        log.info("[charts] presentation {} → done with {} error(s)", req.presentationId(), errors.size());
        return errors;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DAILY
    // ════════════════════════════════════════════════════════════════════════

    private List<String> buildDailyCharts(Drive drive, Sheets sheets, Slides slides,
                                          ChartRequest req, Headers headers, String folderId) {
        List<String> errors = new ArrayList<>();
        if (!headers.valid()) {
            errors.add("Daily: BQ sheet — Date or Impressions column not found");
            return errors;
        }
        Map<Integer, List<String>> tacticLineItems = tacticLineItems(req.lineItemMapping());
        Map<String, Xform> transforms = loadTransforms(slides, req.presentationId(), errors, "Daily");

        for (int n = 1; n <= req.tacticCount(); n++) {
            List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
            try {
                Pivot pivot = ChartPivot.buildDailyPivot(req.bqRows(), liIds, headers, req.flightTs());
                if (pivot.isEmpty()) {
                    errors.add("Tactic " + n + ": no BQ data (line item ids: " + String.join(",", liIds) + ")");
                    continue;
                }
                renderTacticChart(drive, sheets, slides, req.presentationId(),
                    DAILY_TEMPLATE_SHEET_IDS.get(n), DAILY_SLIDE_OBJECT_IDS.get(n),
                    "Chart Tactic " + n + " — " + req.campaignTitle(), folderId, pivot, transforms,
                    "Tactic " + n, errors);
            } catch (IOException ex) {
                errors.add(describeChartError("Tactic " + n, ex));
            }
        }
        return errors;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MONTHLY
    // ════════════════════════════════════════════════════════════════════════

    private List<String> buildMonthlyCharts(Drive drive, Sheets sheets, Slides slides,
                                            ChartRequest req, Headers headers, String folderId) {
        List<String> errors = new ArrayList<>();
        if (!headers.valid()) {
            errors.add("Monthly: BQ sheet — Date or Impressions column not found");
            return errors;
        }
        Map<Integer, List<String>> tacticLineItems = tacticLineItems(req.lineItemMapping());
        boolean multiYear = ChartPivot.isMultiYear(req.bqRows(), headers, req.flightTs());
        // Slides changed after the daily pass — re-read transforms.
        Map<String, Xform> transforms = loadTransforms(slides, req.presentationId(), errors, "Monthly");

        for (int n = 1; n <= req.tacticCount(); n++) {
            List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
            try {
                Pivot pivot = ChartPivot.buildMonthlyPivot(req.bqRows(), liIds, headers, req.flightTs(), multiYear);
                if (pivot.isEmpty()) {
                    errors.add("Monthly Tactic " + n + ": no data (line item ids: " + String.join(",", liIds) + ")");
                    continue;
                }
                renderTacticChart(drive, sheets, slides, req.presentationId(),
                    MONTHLY_TEMPLATE_SHEET_IDS.get(n), MONTHLY_SLIDE_OBJECT_IDS.get(n),
                    "Monthly Chart Tactic " + n + " — " + req.campaignTitle(), folderId, pivot, transforms,
                    "Monthly Tactic " + n, errors);
            } catch (IOException ex) {
                errors.add(describeChartError("Monthly Tactic " + n, ex));
            }
        }
        return errors;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DISTRIBUTION (pie: tactic vs. Other)
    // ════════════════════════════════════════════════════════════════════════

    private List<String> buildDistributionCharts(Drive drive, Sheets sheets, Slides slides,
                                                 ChartRequest req, String folderId) {
        List<String> errors = new ArrayList<>();
        // Slides changed after the monthly pass — re-read transforms.
        Map<String, Xform> transforms = loadTransforms(slides, req.presentationId(), errors, "Distribution");

        for (int n = 1; n <= req.tacticCount(); n++) {
            String tacticName = req.distTacticNames().getOrDefault(n, "Tactic " + n);
            double tacticImp = req.distTacticImps().getOrDefault(n, 0.0);
            double otherImps = req.distTotalImps(); // {{total imps}} whole, no subtraction (PHP parity)
            String templateId = DIST_TEMPLATE_SHEET_IDS.get(n);
            String oldObjectId = DIST_SLIDE_OBJECT_IDS.get(n);
            try {
                String copiedId = copyFile(drive, templateId,
                    "Distribution Chart Tactic " + n + " — " + req.campaignTitle(), folderId);
                ChartSpec spec = readChartSpec(sheets, templateId);
                String tab = findDataTab(sheets, copiedId);
                writeDistribution(sheets, copiedId, tab, tacticName, tacticImp, otherImps);
                if (spec != null) {
                    // PHP fires applyChartSpec(_injectPieSliceColors(...)) without
                    // checking the result: the non-standard "slices" field is
                    // rejected by the Sheets API (HTTP 400) but the copied chart
                    // keeps the template's own slice colors. Treat the recolor as
                    // best-effort so a failure never blocks chart placement —
                    // otherwise the distribution slide ends up empty.
                    try {
                        applyChartSpec(sheets, copiedId, injectPieSliceColors(spec));
                    } catch (IOException colorEx) {
                        log.warn("[charts] distribution tactic {} slice recolor skipped (non-fatal): {}",
                            n, colorEx.getMessage());
                    }
                }
                replaceChartOnSlide(slides, req.presentationId(), oldObjectId, copiedId, transforms.get(oldObjectId));
            } catch (IOException ex) {
                errors.add(describeChartError("Distribution Tactic " + n, ex));
            }
        }
        return errors;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SHARED RENDER (daily + monthly)
    // ════════════════════════════════════════════════════════════════════════

    private void renderTacticChart(Drive drive, Sheets sheets, Slides slides, String presentationId,
                                   String templateId, String oldObjectId, String copyName, String folderId,
                                   Pivot pivot, Map<String, Xform> transforms, String tag,
                                   List<String> errors) throws IOException {
        if (templateId == null) {
            errors.add(tag + ": no chart-template spreadsheet id configured");
            return;
        }
        if (oldObjectId == null) {
            errors.add(tag + ": no slide chart object id configured");
            return;
        }
        String copiedId = copyFile(drive, templateId, copyName, folderId);
        ChartSpec spec = readChartSpec(sheets, templateId);
        String tab = findDataTab(sheets, copiedId);
        writePivot(sheets, copiedId, tab, pivot);
        if (spec != null) {
            // PHP ignores applyChartSpec's return value, so a failed re-apply of
            // the template spec (e.g. a 400 on an output-only field) must NOT
            // abort placement — the chart is still swapped onto the slide.
            try {
                // The COMBO chart templates ship with a date domain but their
                // data series can point at the template's own sheetId (or be
                // dropped when their source columns were cleared), so re-applying
                // the template spec verbatim renders an empty "Add a series…"
                // chart. Retarget the series/domain at the copy's data tab and
                // ensure Impressions (bars) + the rate (line) series exist.
                boolean withRate = pivot.hasClicks() || pivot.hasCompletions();
                injectComboSeries(spec, sheetIdForTab(sheets, copiedId, tab), withRate);
                applyChartSpec(sheets, copiedId, spec);
            } catch (IOException ex) {
                log.warn("[charts] {}: chart spec re-apply failed, placing chart anyway — {}",
                    tag, ex.getMessage());
            }
        }
        replaceChartOnSlide(slides, presentationId, oldObjectId, copiedId, transforms.get(oldObjectId));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DRIVE
    // ════════════════════════════════════════════════════════════════════════

    private String createFolder(Drive drive, String name) throws IOException {
        com.google.api.services.drive.model.File folder = new com.google.api.services.drive.model.File()
            .setName(name).setMimeType("application/vnd.google-apps.folder");
        return drive.files().create(folder).setFields("id").setSupportsAllDrives(true).execute().getId();
    }

    private String copyFile(Drive drive, String fileId, String name, String folderId) throws IOException {
        com.google.api.services.drive.model.File copy = new com.google.api.services.drive.model.File().setName(name);
        if (folderId != null && !folderId.isEmpty()) {
            copy.setParents(List.of(folderId));
        }
        return drive.files().copy(fileId, copy).setFields("id").setSupportsAllDrives(true).execute().getId();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SHEETS
    // ════════════════════════════════════════════════════════════════════════

    private ChartSpec readChartSpec(Sheets sheets, String spreadsheetId) throws IOException {
        Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId)
            .setIncludeGridData(false)
            .setFields("sheets(properties(sheetId,title),charts(chartId,spec))")
            .execute();
        if (ss.getSheets() == null) {
            return null;
        }
        for (Sheet s : ss.getSheets()) {
            if (s.getCharts() == null) {
                continue;
            }
            for (EmbeddedChart chart : s.getCharts()) {
                if (chart.getChartId() != null && chart.getChartId() == CHART_ID_IN_SHEET) {
                    return chart.getSpec();
                }
            }
        }
        return null;
    }

    private String findDataTab(Sheets sheets, String spreadsheetId) throws IOException {
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

    private int sheetIdForTab(Sheets sheets, String spreadsheetId, String tabName) throws IOException {
        Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId)
            .setIncludeGridData(false)
            .setFields("sheets.properties(sheetId,title)")
            .execute();
        if (ss.getSheets() != null) {
            for (Sheet s : ss.getSheets()) {
                if (s.getProperties() != null && tabName.equals(s.getProperties().getTitle())) {
                    return s.getProperties().getSheetId() == null ? 0 : s.getProperties().getSheetId();
                }
            }
        }
        return 0;
    }

    /**
     * Writes the pivot into the copied helper sheet, mirroring PHP
     * {@code writePivotToSheet}: locates placeholder/header rows, drops the
     * unused C/D columns when neither clicks nor completions exist, relabels
     * CTR→VCR for video, then writes date / impressions / metric columns.
     */
    private void writePivot(Sheets sheets, String spreadsheetId, String tabName, Pivot pivot) throws IOException {
        if (pivot.isEmpty()) {
            return;
        }
        boolean hasClicks = pivot.hasClicks();
        boolean hasCompletions = pivot.hasCompletions();

        List<List<Object>> rows = readValues(sheets, spreadsheetId, tabName + "!A1:ZZ3");

        int dataStartRow = -1, dateCol = -1, impsCol = -1, metricCol = -1, ctrHeaderCol = -1, headerRow = -1;
        java.util.regex.Pattern metricPattern = java.util.regex.Pattern.compile(
            hasClicks ? "\\{\\{tactic\\s+\\d+\\s+clicks?\\}\\}" : "\\{\\{tactic\\s+\\d+\\s+completions?\\}\\}",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
            "\\{\\{tactic\\s+\\d+\\s+date\\}\\}", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern impsPattern = java.util.regex.Pattern.compile(
            "\\{\\{tactic\\s+\\d+\\s+impressions?\\}\\}", java.util.regex.Pattern.CASE_INSENSITIVE);

        for (int ri = 0; ri < rows.size(); ri++) {
            List<Object> row = rows.get(ri);
            for (int ci = 0; ci < row.size(); ci++) {
                String cell = str(row.get(ci));
                String upper = cell.trim().toUpperCase(Locale.ROOT);
                if (datePattern.matcher(cell).find()) { dateCol = ci; dataStartRow = ri; }
                if (impsPattern.matcher(cell).find()) { impsCol = ci; dataStartRow = ri; }
                if (metricPattern.matcher(cell).find()) { metricCol = ci; dataStartRow = ri; }
                if (upper.equals("CTR") || upper.equals("VCR")) { ctrHeaderCol = ci; headerRow = ri; }
            }
            if (dataStartRow >= 0 && dateCol >= 0) {
                break;
            }
        }

        if (dataStartRow < 0) { dataStartRow = 1; }
        if (dateCol < 0) { dateCol = 0; }
        if (impsCol < 0) { impsCol = 1; }
        if (metricCol < 0) { metricCol = 3; }

        boolean noCdCols = !hasClicks && !hasCompletions;

        // No clicks and no completions → drop columns C and D (delete D first).
        if (noCdCols) {
            int sid = sheetIdForTab(sheets, spreadsheetId, tabName);
            List<com.google.api.services.sheets.v4.model.Request> del = List.of(
                new com.google.api.services.sheets.v4.model.Request().setDeleteDimension(new DeleteDimensionRequest()
                    .setRange(new DimensionRange().setSheetId(sid).setDimension("COLUMNS").setStartIndex(3).setEndIndex(4))),
                new com.google.api.services.sheets.v4.model.Request().setDeleteDimension(new DeleteDimensionRequest()
                    .setRange(new DimensionRange().setSheetId(sid).setDimension("COLUMNS").setStartIndex(2).setEndIndex(3)))
            );
            sheets.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(del)).execute();
        }

        // No clicks but completions → relabel CTR header cell to VCR.
        if (!hasClicks && hasCompletions && ctrHeaderCol >= 0 && headerRow >= 0) {
            String hdrRange = tabName + "!" + colLetter(ctrHeaderCol) + (headerRow + 1);
            sheets.spreadsheets().values().update(spreadsheetId, hdrRange,
                    new ValueRange().setValues(List.of(List.of("VCR"))))
                .setValueInputOption("RAW").execute();
        }

        int startRow = dataStartRow + 1; // 1-based
        int endRow = startRow + pivot.data().size() - 1;

        List<List<Object>> dates = new ArrayList<>();
        List<List<Object>> imps = new ArrayList<>();
        for (double[] v : pivot.data().values()) {
            imps.add(List.of(Math.round(v[0])));
        }
        for (String date : pivot.data().keySet()) {
            dates.add(List.of(date));
        }

        List<ValueRange> data = new ArrayList<>();
        data.add(new ValueRange()
            .setRange(tabName + "!" + colLetter(dateCol) + startRow + ":" + colLetter(dateCol) + endRow)
            .setValues(dates));
        data.add(new ValueRange()
            .setRange(tabName + "!" + colLetter(impsCol) + startRow + ":" + colLetter(impsCol) + endRow)
            .setValues(imps));

        if (!noCdCols) {
            List<List<Object>> metrics = new ArrayList<>();
            for (double[] v : pivot.data().values()) {
                metrics.add(List.of(Math.round(hasClicks ? v[1] : v[2])));
            }
            data.add(new ValueRange()
                .setRange(tabName + "!" + colLetter(metricCol) + startRow + ":" + colLetter(metricCol) + endRow)
                .setValues(metrics));

            // Populate the rate column (CTR/VCR, %) so the COMBO chart's line
            // series has values to plot — the template ships it empty. Skipped
            // when noCdCols (DOOH/Audio: C/D deleted), matching PHP.
            int rateCol = ctrHeaderCol >= 0 ? ctrHeaderCol : RATE_COL;
            List<List<Object>> rates = new ArrayList<>();
            for (double[] v : pivot.data().values()) {
                double num = hasClicks ? v[1] : v[2];
                double rate = v[0] > 0 ? (num / v[0]) * 100.0 : 0.0;
                rates.add(List.of(Math.round(rate * 100.0) / 100.0));
            }
            data.add(new ValueRange()
                .setRange(tabName + "!" + colLetter(rateCol) + startRow + ":" + colLetter(rateCol) + endRow)
                .setValues(rates));
        }

        sheets.spreadsheets().values().batchUpdate(spreadsheetId,
            new BatchUpdateValuesRequest().setValueInputOption("RAW").setData(data)).execute();
    }

    /**
     * Writes the two-row distribution series, mirroring PHP
     * {@code writeDistributionToSheet}: tactic row + "Other" row, located by
     * scanning column A (fallback rows 2 and 3).
     */
    private void writeDistribution(Sheets sheets, String spreadsheetId, String tabName,
                                   String tacticName, double tacticImps, double otherImps) throws IOException {
        List<List<Object>> rows = readValues(sheets, spreadsheetId, tabName + "!A1:B5");

        int tacticRow = 2;
        int otherRow = 3;
        for (int ri = 0; ri < rows.size(); ri++) {
            String cellA = rows.get(ri).isEmpty() ? "" : str(rows.get(ri).get(0)).trim();
            if (cellA.toLowerCase(Locale.ROOT).matches("(?s).*\\{\\{tactic.*")) {
                tacticRow = ri + 1;
            }
            String lower = cellA.toLowerCase(Locale.ROOT);
            if (lower.equals("total") || lower.equals("other") || lower.equals("rest")) {
                otherRow = ri + 1;
            }
        }

        List<ValueRange> data = List.of(
            new ValueRange().setRange(tabName + "!A" + tacticRow + ":B" + tacticRow)
                .setValues(List.of(List.of(tacticName, Math.round(tacticImps)))),
            new ValueRange().setRange(tabName + "!A" + otherRow + ":B" + otherRow)
                .setValues(List.of(List.of("Other", Math.round(otherImps))))
        );

        sheets.spreadsheets().values().batchUpdate(spreadsheetId,
            new BatchUpdateValuesRequest().setValueInputOption("RAW").setData(data)).execute();
    }

    /**
     * Re-creates the data series the COMBO templates are missing. Reuses the
     * template domain's row range, retargets every source range at the copy's
     * data tab, and adds Impressions (columns / left axis) plus, when a metric
     * exists, the CTR/VCR rate (line / right axis). Idempotent and fully guarded
     * so reruns and odd specs never blank the slide.
     */
    private void injectComboSeries(ChartSpec spec, int dataSheetId, boolean withRate) {
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
                g.setSheetId(dataSheetId); // retarget the domain at the copy's tab
            }
        }
        List<BasicChartSeries> series = new ArrayList<>();
        series.add(comboSeries(dataSheetId, rowStart, rowEnd, IMPS_COL, "COLUMN", "LEFT_AXIS"));
        if (withRate) {
            series.add(comboSeries(dataSheetId, rowStart, rowEnd, RATE_COL, "LINE", "RIGHT_AXIS"));
        }
        bc.setSeries(series);
        if (bc.getHeaderCount() == null) {
            bc.setHeaderCount(1);
        }
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

    private void applyChartSpec(Sheets sheets, String spreadsheetId, ChartSpec spec) throws IOException {
        com.google.api.services.sheets.v4.model.Request req =
            new com.google.api.services.sheets.v4.model.Request().setUpdateChartSpec(
                new UpdateChartSpecRequest().setChartId(CHART_ID_IN_SHEET).setSpec(spec));
        sheets.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(List.of(req))).execute();
    }

    /**
     * Forces the pie slice colors into the spec, mirroring PHP
     * {@code _injectPieSliceColors}: Google Sheets otherwise resets slice colors
     * when the underlying data changes. {@code slices} is a non-standard field so
     * it is set via {@link com.google.api.client.util.GenericData}'s dynamic map.
     */
    @SuppressWarnings("unchecked")
    private ChartSpec injectPieSliceColors(ChartSpec spec) {
        PieChartSpec pie = spec.getPieChart();
        if (pie == null) {
            return spec;
        }
        // Existing slice colors (if any) live under the non-standard "slices" key.
        List<Map<String, Object>> colors = new ArrayList<>();
        Object existing = pie.get("slices");
        if (existing instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("backgroundColor") != null) {
                    colors.add(Map.of("backgroundColor", m.get("backgroundColor")));
                }
            }
        }
        if (colors.isEmpty()) {
            for (double[] c : PIE_DEFAULT_COLORS) {
                colors.add(Map.of("backgroundColor", rgb(c)));
            }
        }

        int sliceCount = Math.max(2, colors.size());
        List<Map<String, Object>> newSlices = new ArrayList<>(sliceCount);
        for (int i = 0; i < sliceCount; i++) {
            Map<String, Object> color = i < colors.size()
                ? colors.get(i)
                : Map.of("backgroundColor", rgb(PIE_DEFAULT_COLORS[i % PIE_DEFAULT_COLORS.length]));
            newSlices.add(color);
        }
        pie.set("slices", newSlices);
        return spec;
    }

    private static Map<String, Object> rgb(double[] c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("red", c[0]);
        m.put("green", c[1]);
        m.put("blue", c[2]);
        return m;
    }

    private List<List<Object>> readValues(Sheets sheets, String spreadsheetId, String range) throws IOException {
        ValueRange vr = sheets.spreadsheets().values().get(spreadsheetId, range).execute();
        return vr.getValues() == null ? List.of() : vr.getValues();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SLIDES
    // ════════════════════════════════════════════════════════════════════════

    /** Position + size of a slide element plus the page it lives on. */
    private record Xform(Size size, AffineTransform transform, String slideId) {}

    private Map<String, Xform> loadTransforms(Slides slides, String presentationId, List<String> errors, String tag) {
        Map<String, Xform> out = new LinkedHashMap<>();
        try {
            Presentation pres = slides.presentations().get(presentationId).execute();
            if (pres.getSlides() != null) {
                for (Page slide : pres.getSlides()) {
                    String slideId = slide.getObjectId();
                    if (slide.getPageElements() == null) {
                        continue;
                    }
                    for (PageElement el : slide.getPageElements()) {
                        if (el.getObjectId() != null) {
                            out.put(el.getObjectId(), new Xform(el.getSize(), el.getTransform(), slideId));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            errors.add(tag + ": could not read presentation layout — " + ex.getMessage());
        }
        return out;
    }

    private void replaceChartOnSlide(Slides slides, String presentationId, String oldObjectId,
                                     String newSpreadsheetId, Xform xform) throws IOException {
        String newObjectId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        CreateSheetsChartRequest create = new CreateSheetsChartRequest()
            .setObjectId(newObjectId)
            .setSpreadsheetId(newSpreadsheetId)
            .setChartId(CHART_ID_IN_SHEET)
            .setLinkingMode("LINKED");

        if (xform != null && xform.size() != null && xform.transform() != null) {
            create.setElementProperties(new PageElementProperties()
                .setPageObjectId(xform.slideId() == null ? "" : xform.slideId())
                .setSize(xform.size())
                .setTransform(xform.transform()));
        }

        List<com.google.api.services.slides.v1.model.Request> requests = List.of(
            new com.google.api.services.slides.v1.model.Request()
                .setDeleteObject(new DeleteObjectRequest().setObjectId(oldObjectId)),
            new com.google.api.services.slides.v1.model.Request().setCreateSheetsChart(create)
        );

        slides.presentations().batchUpdate(presentationId,
            new BatchUpdatePresentationRequest().setRequests(requests)).execute();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private static Map<Integer, List<String>> tacticLineItems(List<LineItemMapping> mapping) {
        Map<Integer, List<String>> out = new LinkedHashMap<>();
        if (mapping == null) {
            return out;
        }
        for (LineItemMapping m : mapping) {
            int n = m.tacticNum() == null ? 0 : m.tacticNum();
            String liId = m.lineItemId() == null ? "" : m.lineItemId().trim();
            if (n > 0 && !liId.isEmpty()) {
                out.computeIfAbsent(n, k -> new ArrayList<>()).add(liId);
            }
        }
        return out;
    }

    /** Spreadsheet A1 column letter for a 0-based column index. */
    private static String colLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        do {
            sb.insert(0, (char) ('A' + (c % 26)));
            c = c / 26 - 1;
        } while (c >= 0);
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * Translates a per-chart failure into an actionable warning. A 403
     * (insufficientFilePermissions) or 404 (not-found) from copying/reading a
     * helper chart-template spreadsheet means the running principal can't reach
     * the source file (owned by a personal account, un-shared, moved or deleted),
     * so the raw Google message ("The user does not have sufficient permissions
     * for this file.") is replaced with a self-explanatory instruction. Anything
     * else falls back to the raw message so nothing is hidden.
     */
    private static String describeChartError(String tag, IOException ex) {
        if (ex instanceof GoogleJsonResponseException gjre) {
            int status = gjre.getStatusCode();
            String reason = null;
            GoogleJsonError details = gjre.getDetails();
            if (details != null && details.getErrors() != null && !details.getErrors().isEmpty()) {
                reason = details.getErrors().get(0).getReason();
            }
            String rawMessage = ex.getMessage();
            boolean permissionDenied = status == 403
                && (reason == null
                    || "insufficientFilePermissions".equals(reason)
                    || "forbidden".equals(reason)
                    || (rawMessage != null && rawMessage.toLowerCase(Locale.ROOT).contains("sufficient permissions")));
            if (permissionDenied) {
                return tag + ": chart template not accessible — sign in with a Google account that "
                    + "can open it, or re-share / re-home the template file so the running account "
                    + "has access (Google: " + rawMessage + ")";
            }
            boolean notFound = status == 404 || "notFound".equals(reason);
            if (notFound) {
                return tag + ": chart template not found — it may have been moved or deleted; "
                    + "re-share or re-create the template file (Google: " + rawMessage + ")";
            }
        }
        return tag + ": " + ex.getMessage();
    }

    private static HttpRequestInitializer userInitializer(String accessToken) {
        return new HttpCredentialsAdapter(GoogleCredentials.create(new AccessToken(accessToken, null)));
    }
}
