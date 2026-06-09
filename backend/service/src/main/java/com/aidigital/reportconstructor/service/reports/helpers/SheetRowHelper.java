package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.time.LocalDate;
import java.util.List;

/**
 * Label/value lookups, safe cell access, and flight-date parsing over sheet row grids.
 */
public interface SheetRowHelper {

    /**
     * Finds a label cell and returns the value in the cell to its right.
     *
     * @param rows  2-D sheet grid to scan (may be {@code null})
     * @param label label text to match case-insensitively
     * @return the trimmed value immediately to the right of the label, or {@code null} when absent
     */
    String findLabelValue(List<List<String>> rows, String label);

    /**
     * Finds a label cell and returns the value in the cell below it (same column).
     *
     * @param rows  2-D sheet grid to scan (may be {@code null})
     * @param label label text to match case-insensitively
     * @return the trimmed value beneath the label, or {@code null} when absent
     */
    String findLabelValueBelow(List<List<String>> rows, String label);

    /**
     * Reads Flight Start / Flight End columns and returns the min/max flight window.
     *
     * @param rows sheet grid containing flight columns (may be {@code null})
     * @return parsed flight boundaries, or {@code null} when headers or dates are missing
     */
    FlightDates extractFlightTimestamps(List<List<String>> rows);

    /**
     * Formats a flight date pair for display in placeholders.
     *
     * @param minStart inclusive flight start date
     * @param maxEnd   inclusive flight end date; when {@code null} or equal to start, only start is shown
     * @return formatted flight-date range string
     */
    String formatFlightDates(LocalDate minStart, LocalDate maxEnd);

    /**
     * Extracts and formats flight dates from a sheet grid.
     *
     * @param rows sheet grid containing flight columns (may be {@code null})
     * @return formatted flight-date range, or {@code null} when none can be extracted
     */
    String extractFlightDates(List<List<String>> rows);

    /**
     * Parses a formatted flight-date range string back into boundaries.
     *
     * @param dateStr formatted range or single date (may be {@code null})
     * @return parsed start/end dates, or {@code null} when parsing fails
     */
    FlightDates parseFlightDateRange(String dateStr);

    /**
     * Resolves flight boundaries from adjustments and sheet rows.
     *
     * @param sheetRows main data sheet rows (lower precedence)
     * @param adjRows   adjustments rows (higher precedence)
     * @return resolved flight window, or {@code null} when none found
     */
    FlightDates resolveFlightTimestamps(List<List<String>> sheetRows, List<List<String>> adjRows);

    /**
     * Parses a single date string from common Sheets formats.
     *
     * @param raw date text (may be {@code null})
     * @return parsed date, or {@code null} when blank or unparseable
     */
    LocalDate parseDate(String raw);

    /**
     * Returns a trimmed cell value, or an empty string when the index is out of range.
     *
     * @param row zero-based row cells (may be {@code null})
     * @param idx zero-based column index
     * @return trimmed cell text, never {@code null}
     */
    String cellAt(List<String> row, int idx);

    /**
     * Joins the first {@code n} cells lowercased for stop-word row scanning.
     *
     * @param row sheet row cells
     * @param n   maximum number of leading cells to join
     * @return lowercased, space-separated prefix of the row
     */
    String joinLower(List<String> row, int n);
}
