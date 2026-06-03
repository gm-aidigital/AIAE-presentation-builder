package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

/**
 * Java port of {@code api/placeholders/sheet_utils.php}.
 *
 * <p>Low-level lookups over the 2-D {@code raw_rows} grids returned by the
 * Sheets API. No dependency on any other engine class. Date handling mirrors
 * the PHP {@code strtotime}/{@code date} behaviour using {@link LocalDate}.
 */
@Component
public class SheetUtils {
    private static final String[] STOP_WORDS = {"added value", "totals", "please note", "total:"};

    /**
     * Finds a label cell and returns the value in the cell to its RIGHT.
     * e.g. label "Client name:" in B3 → value of C3. {@code null} if not found.
     */
    public String findLabelValue(List<List<String>> rows, String label) {

        if (rows == null) return null;
        String needle = label.trim().toLowerCase(Locale.ROOT);
        for (List<String> row : rows) {
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals(needle)) {
                    return j + 1 < row.size() ? cell(row, j + 1) : null;
                }
            }
        }
        return null;
    }

    /**
     * Finds a label cell and returns the value in the cell BELOW it
     * (same column, next row). {@code null} if not found.
     */
    public String findLabelValueBelow(List<List<String>> rows, String label) {

        if (rows == null) return null;
        String needle = label.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals(needle)) {
                    List<String> next = i + 1 < rows.size() ? rows.get(i + 1) : null;
                    return next != null && j < next.size() ? cell(next, j) : null;
                }
            }
        }
        return null;
    }

    /**
     * Reads the Flight Start / Flight End columns and returns
     * {@code [minStart, maxEnd]}, or {@code null} when not found.
     * Mirrors PHP {@code _extractFlightTimestampsRaw}.
     */
    public FlightDates extractFlightTimestamps(List<List<String>> rows) {

        if (rows == null) return null;
        int headerRowIdx = -1;
        int startCol = -1;
        int endCol = -1;

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                String val = cell(row, j).toLowerCase(Locale.ROOT);
                if (val.equals("flight start")) { headerRowIdx = i; startCol = j; }
                if (val.equals("flight end")) { endCol = j; }
            }
            if (headerRowIdx >= 0 && startCol >= 0 && endCol >= 0) break;
        }

        if (headerRowIdx < 0 || startCol < 0 || endCol < 0) return null;

        LocalDate minStart = null;
        LocalDate maxEnd = null;

        outer:
        for (int i = headerRowIdx + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) continue;
            String rowText = joinLower(row, 4);
            for (String stop : STOP_WORDS) {
                if (rowText.contains(stop)) break outer;
            }

            String startVal = cellAt(row, startCol);
            String endVal = cellAt(row, endCol);
            if (startVal.isEmpty()) continue;
            LocalDate ds = parseDate(startVal);
            if (ds == null) continue;

            if (minStart == null || ds.isBefore(minStart)) minStart = ds;
            LocalDate de = endVal.isEmpty() ? null : parseDate(endVal);
            if (de != null && (maxEnd == null || de.isAfter(maxEnd))) maxEnd = de;
        }

        if (minStart == null) return null;
        return new FlightDates(minStart, maxEnd != null ? maxEnd : minStart);
    }

    /**
     * Formats a flight date pair into e.g. {@code "Feb 12 – May 9, 2026"}.
     * Same year is written once at the end. Mirrors PHP {@code formatFlightDates}.
     */
    public String formatFlightDates(LocalDate minStart, LocalDate maxEnd) {

        if (maxEnd == null || maxEnd.equals(minStart)) {
            return MDY.format(minStart);
        }
        if (minStart.getYear() == maxEnd.getYear()) {
            return MD.format(minStart) + " \u2013 " + MDY.format(maxEnd);
        }
        return MDY.format(minStart) + " \u2013 " + MDY.format(maxEnd);
    }

    /** Mirrors PHP {@code extractFlightDates} — formatted range or {@code null}. */
    public String extractFlightDates(List<List<String>> rows) {

        FlightDates fd = extractFlightTimestamps(rows);
        return fd == null ? null : formatFlightDates(fd.start(), fd.end());
    }

    /**
     * Parses a date-range string like {@code "Mar 1 – Mar 31, 2026"} back into
     * a pair. Supports same-year shorthand, cross-year, and single dates.
     * Mirrors PHP {@code parseFlightDateRange}.
     */
    public FlightDates parseFlightDateRange(String dateStr) {

        if (dateStr == null) return null;
        String s = dateStr
            .replace('\u00A0', ' ')
            .replace('\u2013', '\u2013')
            .replace('\u2014', '\u2013')
            .trim();

        String[] parts = s.split("\\s*[\u2013\\-]\\s*", 2);
        if (parts.length == 2) {
            String rawStart = parts[0];
            String rawEnd = parts[1];
            if (!rawStart.matches(".*\\d{4}.*")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})").matcher(rawEnd);
                if (m.find()) rawStart = rawStart + ", " + m.group(1);
            }
            LocalDate ds = parseDate(rawStart.trim());
            LocalDate de = parseDate(rawEnd.trim());
            if (ds != null && de != null) return new FlightDates(ds, de);
        }

        LocalDate single = parseDate(s);
        if (single != null) return new FlightDates(single, single);
        return null;
    }

    /**
     * Resolves flight boundaries. Priority:
     * manual "Flight dates:" in adj → in sheet → auto columns in adj → in sheet.
     * Mirrors PHP {@code resolveFlightTimestamps}.
     */
    public FlightDates resolveFlightTimestamps(List<List<String>> sheetRows, List<List<String>> adjRows) {

        String manual = findLabelValue(adjRows, "Flight dates:");
        if (manual != null) {
            FlightDates parsed = parseFlightDateRange(manual);
            if (parsed != null) return parsed;
        }
        manual = findLabelValue(sheetRows, "Flight dates:");
        if (manual != null) {
            FlightDates parsed = parseFlightDateRange(manual);
            if (parsed != null) return parsed;
        }
        FlightDates fromAdj = extractFlightTimestamps(adjRows);
        if (fromAdj != null) return fromAdj;
        return extractFlightTimestamps(sheetRows);
    }

    // ── date parsing (PHP strtotime equivalent for the formats Sheets emits) ──

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final DateTimeFormatter[] datePatterns = {
        flexible("MMM d, yyyy"),
        flexible("MMMM d, yyyy"),
        flexible("MMM d yyyy"),
        flexible("MMMM d yyyy"),
        flexible("d MMM yyyy"),
        flexible("d MMMM yyyy"),
        flexible("yyyy-M-d"),
        flexible("yyyy/M/d"),
        flexible("M/d/yyyy"),
        flexible("M-d-yyyy"),
        flexible("M/d/yy"),
    };

    DateTimeFormatter flexible(String pattern) {
        return new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.SMART);
    }

    /** Parses a single date string, mirroring PHP {@code strtotime}; {@code null} on failure. */
    public LocalDate parseDate(String raw) {

        if (raw == null) return null;
        String s = raw.trim().replace('\u00A0', ' ');
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : datePatterns) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignored) {
                // try next pattern
            }
        }
        // Month + day without a year → assume current year (PHP strtotime behaviour).
        for (String p : new String[]{"MMM d", "MMMM d", "d MMM", "d MMMM"}) {
            try {
                DateTimeFormatter f = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern(p)
                    .parseDefaulting(java.time.temporal.ChronoField.YEAR, LocalDate.now().getYear())
                    .toFormatter(Locale.ENGLISH);
                return LocalDate.parse(s, f);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    String cell(List<String> row, int idx) {

        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    String cellAt(List<String> row, int idx) {

        return idx >= 0 && idx < row.size() ? cell(row, idx) : "";
    }

    String joinLower(List<String> row, int n) {

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(n, row.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(' ');
            sb.append(cell(row, i));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
