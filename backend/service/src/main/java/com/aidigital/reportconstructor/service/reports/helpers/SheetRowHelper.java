package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
	 * Collects the distinct, non-empty values running down the column beneath the first header cell
	 * whose normalised text matches one of {@code headerSynonyms}.
	 *
	 * <p>Unlike {@link #findLabelValueBelow(List, String)} — which reads only the single cell directly
	 * beneath the header and therefore misses real media-plan grids where a section-title row sits
	 * between the header and the data — this walks every row below the header, gathering the column's
	 * values until a totals/footer row (a leading cell starting with {@code "total"}) or a scan cap is
	 * reached. Values are de-duplicated case-insensitively, preserving first-seen order and original
	 * casing, so a field repeated across many line items collapses to one entry.
	 *
	 * @param rows           2-D sheet grid to scan (may be {@code null})
	 * @param headerSynonyms normalised header texts (lowercase, punctuation collapsed to single spaces) any of which
	 *                       identifies the target column; see {@code MediaPlanColumn}
	 * @return the ordered, de-duplicated column values, or an empty list when no header matches
	 */
	List<String> collectColumnValuesBelow(List<List<String>> rows, Set<String> headerSynonyms);

	/**
	 * Reports whether a resolved geo value merely points at another workbook tab instead of naming
	 * locations (e.g. {@code "See Geo tab"}, {@code "See 'Geo' Tab"}, {@code "see locations sheet"}), so
	 * the caller can push the whole workbook to Claude for summarisation. Matching ignores case,
	 * surrounding quotes, and other punctuation between the words.
	 *
	 * @param value the resolved geo cell value (may be {@code null})
	 * @return {@code true} when the value references another tab rather than listing locations
	 */
	boolean referencesGeoTab(String value);

	/**
	 * Detects the full min/max date range present in the raw-data (Elevate "Basic" tab / BigQuery
	 * export) rows. Locates the delivery header row (a row carrying {@code date}, {@code channel},
	 * {@code cost} and {@code impressions} columns) and returns the earliest/latest parseable value
	 * in its {@code date} column. This is the source of truth for the report flight window and the
	 * {@code {{flight_dates}}} placeholder; the media plan is never consulted for dates.
	 *
	 * @param rows raw-data grid to scan (may be {@code null})
	 * @return the inclusive {@link FlightDates} range covering every dated row, or {@code null} when
	 *         no delivery header or no parseable date is found
	 */
	FlightDates detectDataDateRange(List<List<String>> rows);

	/**
	 * Formats a flight date pair for display in placeholders.
	 *
	 * @param minStart inclusive flight start date
	 * @param maxEnd   inclusive flight end date; when {@code null} or equal to start, only start is shown
	 * @return formatted flight-date range string
	 */
	String formatFlightDates(LocalDate minStart, LocalDate maxEnd);

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
