package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-data pivot logic: turns raw BigQuery export rows into per-tactic daily /
 * monthly series (no Google API calls) so the {@code ChartProvider} can write them
 * to the helper chart spreadsheets.
 *
 * <p>Each pivot value is a {@code double[]} of {@code {imps, clicks, completions}}.
 * The branching is surfaced as {@code hasClicks} / {@code hasCompletions}:
 * display/social tactics have clicks, video/CTV have completions, DOOH/Audio have
 * neither.
 */
@Component
public class ChartPivot {
    private final SheetRowHelper sheetRows;
    private final LineItemNamingHelper lineItemNaming;
    private final ReportNumberParser reportNumbers;

    public ChartPivot(
        SheetRowHelper sheetRows,
        LineItemNamingHelper lineItemNaming,
        ReportNumberParser reportNumbers
    ) {
        this.sheetRows = sheetRows;
        this.lineItemNaming = lineItemNaming;
        this.reportNumbers = reportNumbers;
    }

    /**
     * Zero-based column indices parsed from the BQ export header row; {@code -1} when a column is absent.
     *
     * @param dateCol index of the {@code Date} column
     * @param impsCol index of the {@code Impressions} column
     * @param clicksCol index of the {@code Clicks} column, or {@code -1}
     * @param completionsCol index of the {@code Completions} column, or {@code -1}
     * @param l1NamingCol index of the {@code Level 1 Naming} column, or {@code -1}
     */
    public record Headers(int dateCol, int impsCol, int clicksCol, int completionsCol, int l1NamingCol) {
        /** @return {@code true} when both the {@code Date} and {@code Impressions} columns were located. */
        public boolean valid() {
            return dateCol >= 0 && impsCol >= 0;
        }
    }

    /**
     * Pivot result for one tactic.
     *
     * @param data insertion-ordered (chronological) map of label → {@code {imps, clicks, completions}}
     * @param hasClicks {@code true} when any row carried clicks (display/social → CTR line series)
     * @param hasCompletions {@code true} when any row carried completions (video/CTV → VCR line series)
     */
    public record Pivot(LinkedHashMap<String, double[]> data, boolean hasClicks, boolean hasCompletions) {
        /** @return {@code true} when no rows were pivoted (no chart should be written). */
        public boolean isEmpty() {
            return data.isEmpty();
        }
    }

    /**
     * Parses the BQ header row: scans rows until the one containing a {@code Date}
     * column, then records the indices.
     *
     * @param rows raw BQ export rows (may be {@code null})
     * @return the located column indices; {@link Headers#valid()} is {@code false} when Date/Impressions are missing
     */
    public Headers parseBqHeaders(List<List<String>> rows) {

        int dateCol = -1;
        int impsCol = -1;
        int clicksCol = -1;
        int completionsCol = -1;
        int l1NamingCol = -1;
        if (rows == null) {
            return new Headers(-1, -1, -1, -1, -1);
        }
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            boolean hasDate = false;
            for (int j = 0; j < row.size(); j++) {
                String v = sheetRows.cellAt(row, j).toLowerCase(Locale.ROOT);
                switch (v) {
                    case "date" -> { dateCol = j; hasDate = true; }
                    case "impressions", "imps" -> impsCol = j;
                    case "clicks", "click" -> clicksCol = j;
                    case "completions", "completion", "complete" -> completionsCol = j;
                    case "level 1 naming", "level_1_naming", "l1_naming", "level1naming" -> l1NamingCol = j;
                    default -> { }
                }
            }
            if (hasDate) {
                break;
            }
        }
        return new Headers(dateCol, impsCol, clicksCol, completionsCol, l1NamingCol);
    }

    /**
     * Extracts the line-item id from a Level 1 Naming string. Delegates to
     * {@link LineItemNamingHelper#extractLineItemIdOrBlank(String)}.
     *
     * @param naming the {@code Level 1 Naming} cell value (may be {@code null})
     * @return the digits-only line-item id, or an empty string when absent/non-numeric
     */
    public String extractLiIdFromL1Naming(String naming) {
        return lineItemNaming.extractLineItemIdOrBlank(naming);
    }

    /**
     * Daily pivot: date ({@code yyyy-MM-dd}) → {@code {imps, clicks, completions}},
     * filtered by line-item ids (via Level 1 Naming) and the flight window.
     *
     * @param rows raw BQ export rows (may be {@code null})
     * @param liIds line-item ids for this tactic; when empty (or no naming column) no id filter is applied
     * @param h column indices from {@link #parseBqHeaders(List)}
     * @param flightTs flight window to clip rows to, or {@code null} to include all dates
     * @return chronological daily pivot for the tactic
     */
    public Pivot buildDailyPivot(
        List<List<String>> rows,
        List<String> liIds,
        Headers h,
        FlightDates flightTs
    ) {
        TreeMap<String, double[]> pivot = new TreeMap<>();
        double totalClicks = 0.0;
        double totalCompletions = 0.0;
        boolean filterLi = liIds != null && !liIds.isEmpty() && h.l1NamingCol() >= 0;
        boolean filterDate = flightTs != null;
        java.util.Set<String> liSet = liIds == null ? java.util.Set.of() : new java.util.HashSet<>(liIds);
        boolean headerSkipped = false;

        if (rows != null) {
            for (List<String> row : rows) {
                String dateRaw = sheetRows.cellAt(row, h.dateCol());
                if (!headerSkipped) {
                    if (dateRaw.toLowerCase(Locale.ROOT).equals("date")) {
                        headerSkipped = true;
                        continue;
                    }
                }
                if (dateRaw.isEmpty()) {
                    continue;
                }
                if (filterLi) {
                    String liVal = extractLiIdFromL1Naming(sheetRows.cellAt(row, h.l1NamingCol()));
                    if (liVal.isEmpty() || !liSet.contains(liVal)) {
                        continue;
                    }
                }
                LocalDate ts = sheetRows.parseDate(dateRaw);
                if (ts == null) {
                    continue;
                }
                if (filterDate && (ts.isBefore(flightTs.start()) || ts.isAfter(flightTs.end()))) {
                    continue;
                }
                String dateKey = ts.toString(); // yyyy-MM-dd
                double imps = reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.impsCol()));
                double clicks = h.clicksCol() >= 0 ? reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.clicksCol())) : 0.0;
                double completions = h.completionsCol() >= 0 ? reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.completionsCol())) : 0.0;

                double[] agg = pivot.computeIfAbsent(dateKey, k -> new double[3]);
                agg[0] += imps;
                agg[1] += clicks;
                agg[2] += completions;
                totalClicks += clicks;
                totalCompletions += completions;
            }
        }
        return new Pivot(new LinkedHashMap<>(pivot), totalClicks > 0, totalCompletions > 0);
    }

    /**
     * Monthly pivot: month label ({@code March} or {@code March 2024} when the
     * campaign spans multiple years) → {@code {imps, clicks, completions}}, kept
     * in chronological order.
     *
     * @param rows raw BQ export rows (may be {@code null})
     * @param liIds line-item ids for this tactic; when empty (or no naming column) no id filter is applied
     * @param h column indices from {@link #parseBqHeaders(List)}
     * @param flightTs flight window to clip rows to, or {@code null} to include all dates
     * @param multiYear when {@code true}, month labels carry the year (e.g. {@code March 2024})
     * @return chronological monthly pivot for the tactic
     */
    public Pivot buildMonthlyPivot(
        List<List<String>> rows,
        List<String> liIds,
        Headers h,
        FlightDates flightTs,
        boolean multiYear
    ) {
        TreeMap<String, double[]> raw = new TreeMap<>(); // yyyy-MM → totals
        TreeMap<String, String> labels = new TreeMap<>(); // yyyy-MM → display label
        double totalClicks = 0.0;
        double totalCompletions = 0.0;
        boolean filterLi = liIds != null && !liIds.isEmpty() && h.l1NamingCol() >= 0;
        boolean filterDate = flightTs != null;
        java.util.Set<String> liSet = liIds == null ? java.util.Set.of() : new java.util.HashSet<>(liIds);
        boolean headerSkipped = false;

        if (rows != null) {
            for (List<String> row : rows) {
                String dateRaw = sheetRows.cellAt(row, h.dateCol());
                if (!headerSkipped) {
                    if (dateRaw.toLowerCase(Locale.ROOT).equals("date")) {
                        headerSkipped = true;
                        continue;
                    }
                }
                if (dateRaw.isEmpty()) {
                    continue;
                }
                if (filterLi) {
                    String liVal = extractLiIdFromL1Naming(sheetRows.cellAt(row, h.l1NamingCol()));
                    if (liVal.isEmpty() || !liSet.contains(liVal)) {
                        continue;
                    }
                }
                LocalDate ts = sheetRows.parseDate(dateRaw);
                if (ts == null) {
                    continue;
                }
                if (filterDate && (ts.isBefore(flightTs.start()) || ts.isAfter(flightTs.end()))) {
                    continue;
                }
                String monthKey = String.format(Locale.ROOT, "%04d-%02d", ts.getYear(), ts.getMonthValue());
                double imps = reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.impsCol()));
                double clicks = h.clicksCol() >= 0 ? reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.clicksCol())) : 0.0;
                double completions = h.completionsCol() >= 0 ? reportNumbers.parseReportNumber(sheetRows.cellAt(row, h.completionsCol())) : 0.0;

                double[] agg = raw.computeIfAbsent(monthKey, k -> new double[3]);
                agg[0] += imps;
                agg[1] += clicks;
                agg[2] += completions;
                labels.computeIfAbsent(monthKey, k -> monthLabel(ts, multiYear));
                totalClicks += clicks;
                totalCompletions += completions;
            }
        }

        LinkedHashMap<String, double[]> pivot = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : raw.entrySet()) {
            pivot.put(labels.get(e.getKey()), e.getValue());
        }
        return new Pivot(pivot, totalClicks > 0, totalCompletions > 0);
    }

    /**
     * Whether the campaign spans more than one calendar year — when it does the
     * monthly labels carry the year.
     *
     * @param rows raw BQ export rows, scanned only when {@code flightTs} is {@code null}
     * @param h column indices from {@link #parseBqHeaders(List)}
     * @param flightTs flight window; when present its start/end years decide the result directly
     * @return {@code true} when the data (or flight window) covers more than one calendar year
     */
    public boolean isMultiYear(List<List<String>> rows, Headers h, FlightDates flightTs) {

        if (flightTs != null) {
            return flightTs.start().getYear() != flightTs.end().getYear();
        }
        if (rows == null) {
            return false;
        }
        java.util.Set<Integer> years = new java.util.HashSet<>();
        boolean headerSkipped = false;
        for (List<String> row : rows) {
            String dateRaw = sheetRows.cellAt(row, h.dateCol());
            if (!headerSkipped) {
                if (dateRaw.toLowerCase(Locale.ROOT).equals("date")) {
                    headerSkipped = true;
                    continue;
                }
            }
            if (dateRaw.isEmpty()) {
                continue;
            }
            LocalDate ts = sheetRows.parseDate(dateRaw);
            if (ts != null) {
                years.add(ts.getYear());
                if (years.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    String monthLabel(LocalDate ts, boolean multiYear) {

        String name = Month.of(ts.getMonthValue()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return multiYear ? name + " " + ts.getYear() : name;
    }
}
