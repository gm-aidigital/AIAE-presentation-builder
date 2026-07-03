package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Low-level lookups over the 2-D {@code raw_rows} grids returned by the Sheets API.
 *
 * <p>No dependency on any other engine class. Date handling is implemented with
 * {@link LocalDate} and accepts the range of date formats Google Sheets emits.
 */
@Component
public class SheetRowHelperImpl implements SheetRowHelper {

	/** Maximum number of rows scanned below a matched column header before giving up. */
	private static final int COLUMN_SCAN_LIMIT = 200;

	/** Number of leading cells inspected for a {@code "total"} label when detecting a footer row. */
	private static final int FOOTER_LABEL_SCAN_COLUMNS = 5;

	@Override
	public String findLabelValue(List<List<String>> rows, String label) {

		if (rows == null) {
			return null;
		}
		String needle = label.trim().toLowerCase(Locale.ROOT);
		for (List<String> row : rows) {
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).toLowerCase(Locale.ROOT).equals(needle)) {
					return j + 1 < row.size() ? cell(row, j + 1) : null;
				}
			}
		}
		return null;
	}

	@Override
	public String findLabelValueBelow(List<List<String>> rows, String label) {

		if (rows == null) {
			return null;
		}
		String needle = label.trim().toLowerCase(Locale.ROOT);
		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).toLowerCase(Locale.ROOT).equals(needle)) {
					List<String> next = i + 1 < rows.size() ? rows.get(i + 1) : null;
					return next != null && j < next.size() ? cell(next, j) : null;
				}
			}
		}
		return null;
	}

	@Override
	public List<String> collectColumnValuesBelow(List<List<String>> rows, Set<String> headerSynonyms) {

		if (rows == null || headerSynonyms == null || headerSynonyms.isEmpty()) {
			return List.of();
		}
		int headerRow = -1;
		int headerCol = -1;
		outer:
		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (headerSynonyms.contains(normalizeHeader(cell(row, j)))) {
					headerRow = i;
					headerCol = j;
					break outer;
				}
			}
		}
		if (headerRow < 0) {
			return List.of();
		}

		Set<String> seen = new LinkedHashSet<>();
		List<String> values = new ArrayList<>();
		int limit = Math.min(rows.size(), headerRow + 1 + COLUMN_SCAN_LIMIT);
		for (int i = headerRow + 1; i < limit; i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			if (isFooterRow(row)) {
				break;
			}
			String value = cellAt(row, headerCol);
			if (value.isEmpty()) {
				continue;
			}
			if (seen.add(value.toLowerCase(Locale.ROOT))) {
				values.add(value);
			}
		}
		return values;
	}

	/**
	 * Normalises a header cell for synonym matching: lowercased, every run of non-alphanumeric
	 * characters collapsed to a single space, and trimmed.
	 *
	 * @param raw the raw header cell text
	 * @return the normalised header token (e.g. {@code "Targeted\nLocations"} → {@code "targeted locations"})
	 */
	String normalizeHeader(String raw) {

		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/**
	 * Reports whether a row is a totals/footer row, detected by any of its leading cells starting with
	 * {@code "total"} (e.g. {@code "Totals:"}), so column collection stops before summary rows.
	 *
	 * @param row the row to inspect
	 * @return {@code true} when the row looks like a totals/footer row
	 */
	boolean isFooterRow(List<String> row) {

		int limit = Math.min(FOOTER_LABEL_SCAN_COLUMNS, row.size());
		for (int j = 0; j < limit; j++) {
			if (cell(row, j).toLowerCase(Locale.ROOT).startsWith("total")) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean referencesGeoTab(String value) {

		if (value == null) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim();
		if (normalized.contains("geo tab") || normalized.contains("geo sheet")) {
			return true;
		}
		// Generic pointer to another tab/sheet, e.g. "see locations tab", "see targeting sheet".
		return normalized.startsWith("see ") && (normalized.endsWith(" tab") || normalized.endsWith(" sheet"));
	}

	@Override
	public FlightDates detectDataDateRange(List<List<String>> rows) {

		if (rows == null) {
			return null;
		}
		int headerRowIdx = -1;
		int dateCol = -1;

		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			int dCol = -1;
			boolean hasChannel = false;
			boolean hasCost = false;
			boolean hasImps = false;
			for (int j = 0; j < row.size(); j++) {
				String val = cell(row, j).toLowerCase(Locale.ROOT);
				switch (val) {
					case "date" -> dCol = j;
					case "channel" -> hasChannel = true;
					case "cost" -> hasCost = true;
					case "impressions" -> hasImps = true;
					default -> {
					}
				}
			}
			if (dCol >= 0 && hasChannel && hasCost && hasImps) {
				headerRowIdx = i;
				dateCol = dCol;
				break;
			}
		}

		if (headerRowIdx < 0) {
			return null;
		}

		LocalDate min = null;
		LocalDate max = null;
		for (int i = headerRowIdx + 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			String val = cellAt(row, dateCol);
			if (val.isEmpty()) {
				continue;
			}
			LocalDate d = parseDate(val);
			if (d == null) {
				continue;
			}
			if (min == null || d.isBefore(min)) {
				min = d;
			}
			if (max == null || d.isAfter(max)) {
				max = d;
			}
		}

		if (min == null) {
			return null;
		}
		return new FlightDates(min, max);
	}

	@Override
	public String formatFlightDates(LocalDate minStart, LocalDate maxEnd) {

		if (maxEnd == null || maxEnd.equals(minStart)) {
			return MDY.format(minStart);
		}
		if (minStart.getYear() == maxEnd.getYear()) {
			return MD.format(minStart) + " \u2013 " + MDY.format(maxEnd);
		}
		return MDY.format(minStart) + " \u2013 " + MDY.format(maxEnd);
	}

	// ── date parsing (handles the date formats Sheets emits) ──

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

	@Override
	public LocalDate parseDate(String raw) {

		if (raw == null) {
			return null;
		}
		String s = raw.trim().replace('\u00A0', ' ');
		if (s.isEmpty()) {
			return null;
		}
		for (DateTimeFormatter f : datePatterns) {
			try {
				return LocalDate.parse(s, f);
			} catch (Exception ignored) {
				// try next pattern
			}
		}
		// Month + day without a year → assume the current year.
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

	@Override
	public String cellAt(List<String> row, int idx) {

		if (row == null || idx < 0 || idx >= row.size()) {
			return "";
		}
		return cell(row, idx);
	}

	@Override
	public String joinLower(List<String> row, int n) {

		StringBuilder sb = new StringBuilder();
		int limit = Math.min(n, row.size());
		for (int i = 0; i < limit; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(cell(row, i));
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}
}
