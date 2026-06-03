package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-data port of the pivot logic from PHP {@code api/chart_builder.php}
 * ({@code parseBqHeaders}, {@code buildPivot}, {@code buildMonthlyPivot},
 * {@code extractLiIdFromL1Naming}, {@code _isMultiYear}). No Google API calls
 * — turns raw BigQuery export rows into per-tactic daily / monthly series so the
 * {@code ChartProvider} can write them to the helper chart spreadsheets.
 *
 * <p>Each pivot value is a {@code double[]} of {@code {imps, clicks, completions}}.
 * The branching the PHP relies on is surfaced as {@code hasClicks} /
 * {@code hasCompletions}: display/social tactics have clicks, video/CTV have
 * completions, DOOH/Audio have neither.
 */
public final class ChartPivot {

    private ChartPivot() {}

    /** Column indices parsed from the BQ header row; {@code -1} when absent. */
    public record Headers(int dateCol, int impsCol, int clicksCol, int completionsCol, int l1NamingCol) {
        public boolean valid() {
            return dateCol >= 0 && impsCol >= 0;
        }
    }

    /**
     * Pivot result. {@code data} is an insertion-ordered (chronological) map of
     * label → {@code {imps, clicks, completions}}.
     */
    public record Pivot(LinkedHashMap<String, double[]> data, boolean hasClicks, boolean hasCompletions) {
        public boolean isEmpty() {
            return data.isEmpty();
        }
    }

    /**
     * Parses the BQ header row, mirroring PHP {@code parseBqHeaders}. Scans rows
     * until the one containing a {@code Date} column, then records the indices.
     */
    public static Headers parseBqHeaders(List<List<String>> rows) {
        int dateCol = -1, impsCol = -1, clicksCol = -1, completionsCol = -1, l1NamingCol = -1;
        if (rows == null) {
            return new Headers(-1, -1, -1, -1, -1);
        }
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            boolean hasDate = false;
            for (int j = 0; j < row.size(); j++) {
                String v = cell(row, j).toLowerCase(Locale.ROOT);
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
     * Extracts the line-item id from a Level 1 Naming string. The id is the 9th
     * underscore segment (index 8) and must be all digits. Mirrors PHP
     * {@code extractLiIdFromL1Naming}.
     */
    public static String extractLiIdFromL1Naming(String naming) {
        if (naming == null) {
            return "";
        }
        String[] parts = naming.split("_", -1);
        String id = parts.length > 8 ? parts[8].trim() : "";
        return !id.isEmpty() && id.chars().allMatch(Character::isDigit) ? id : "";
    }

    /**
     * Daily pivot: date ({@code yyyy-MM-dd}) → {@code {imps, clicks, completions}},
     * filtered by line-item ids (via Level 1 Naming) and the flight window.
     * Mirrors PHP {@code buildPivot}.
     */
    public static Pivot buildDailyPivot(
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
                String dateRaw = cellAt(row, h.dateCol());
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
                    String liVal = extractLiIdFromL1Naming(cellAt(row, h.l1NamingCol()));
                    if (liVal.isEmpty() || !liSet.contains(liVal)) {
                        continue;
                    }
                }
                LocalDate ts = SheetUtils.parseDate(dateRaw);
                if (ts == null) {
                    continue;
                }
                if (filterDate && (ts.isBefore(flightTs.start()) || ts.isAfter(flightTs.end()))) {
                    continue;
                }
                String dateKey = ts.toString(); // yyyy-MM-dd
                double imps = num(cellAt(row, h.impsCol()));
                double clicks = h.clicksCol() >= 0 ? num(cellAt(row, h.clicksCol())) : 0.0;
                double completions = h.completionsCol() >= 0 ? num(cellAt(row, h.completionsCol())) : 0.0;

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
     * in chronological order. Mirrors PHP {@code buildMonthlyPivot}.
     */
    public static Pivot buildMonthlyPivot(
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
                String dateRaw = cellAt(row, h.dateCol());
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
                    String liVal = extractLiIdFromL1Naming(cellAt(row, h.l1NamingCol()));
                    if (liVal.isEmpty() || !liSet.contains(liVal)) {
                        continue;
                    }
                }
                LocalDate ts = SheetUtils.parseDate(dateRaw);
                if (ts == null) {
                    continue;
                }
                if (filterDate && (ts.isBefore(flightTs.start()) || ts.isAfter(flightTs.end()))) {
                    continue;
                }
                String monthKey = String.format(Locale.ROOT, "%04d-%02d", ts.getYear(), ts.getMonthValue());
                double imps = num(cellAt(row, h.impsCol()));
                double clicks = h.clicksCol() >= 0 ? num(cellAt(row, h.clicksCol())) : 0.0;
                double completions = h.completionsCol() >= 0 ? num(cellAt(row, h.completionsCol())) : 0.0;

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
     * monthly labels carry the year. Mirrors PHP {@code _isMultiYear}.
     */
    public static boolean isMultiYear(List<List<String>> rows, Headers h, FlightDates flightTs) {
        if (flightTs != null) {
            return flightTs.start().getYear() != flightTs.end().getYear();
        }
        if (rows == null) {
            return false;
        }
        java.util.Set<Integer> years = new java.util.HashSet<>();
        boolean headerSkipped = false;
        for (List<String> row : rows) {
            String dateRaw = cellAt(row, h.dateCol());
            if (!headerSkipped) {
                if (dateRaw.toLowerCase(Locale.ROOT).equals("date")) {
                    headerSkipped = true;
                    continue;
                }
            }
            if (dateRaw.isEmpty()) {
                continue;
            }
            LocalDate ts = SheetUtils.parseDate(dateRaw);
            if (ts != null) {
                years.add(ts.getYear());
                if (years.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String monthLabel(LocalDate ts, boolean multiYear) {
        String name = Month.of(ts.getMonthValue()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return multiYear ? name + " " + ts.getYear() : name;
    }

    // ── numeric cleanup: strip commas + non [0-9.], parse leading number ───────
    private static final Pattern LEADING_NUM = Pattern.compile("^[0-9]*\\.?[0-9]+");

    private static double num(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String s = raw.replace(",", "").replaceAll("[^0-9.]", "");
        Matcher m = LEADING_NUM.matcher(s);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static String cell(List<String> row, int idx) {
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    private static String cellAt(List<String> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size()) {
            return "";
        }
        return cell(row, idx);
    }
}
