package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

/**
 * Low-level lookups over the 2-D {@code raw_rows} grids returned by the Sheets API.
 *
 * <p>No dependency on any other engine class. Date handling is implemented with
 * {@link LocalDate} and accepts the range of date formats Google Sheets emits.
 */
@Component
public class SheetRowHelperImpl implements SheetRowHelper {

	private final TacticCatalog catalog;

	public SheetRowHelperImpl(TacticCatalog catalog) {
		this.catalog = catalog;
	}

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
	public boolean referencesGeoTab(String value) {

		if (value == null) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim();
		return normalized.contains("geo tab");
	}

	@Override
	public FlightDates extractFlightTimestamps(List<List<String>> rows) {

		if (rows == null) {
			return null;
		}
		int headerRowIdx = -1;
		int startCol = -1;
		int endCol = -1;

		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				String val = cell(row, j).toLowerCase(Locale.ROOT);
				if (val.equals("flight start")) {
					headerRowIdx = i;
					startCol = j;
				}
				if (val.equals("flight end")) {
					endCol = j;
				}
			}
			if (headerRowIdx >= 0 && startCol >= 0 && endCol >= 0) {
				break;
			}
		}

		if (headerRowIdx < 0 || startCol < 0 || endCol < 0) {
			return null;
		}

		LocalDate minStart = null;
		LocalDate maxEnd = null;

		outer:
		for (int i = headerRowIdx + 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			String rowText = joinLower(row, 4);
			for (String stop : catalog.sheetStopWords()) {
				if (rowText.contains(stop)) {
					break outer;
				}
			}

			String startVal = cellAt(row, startCol);
			String endVal = cellAt(row, endCol);
			if (startVal.isEmpty()) {
				continue;
			}
			LocalDate ds = parseDate(startVal);
			if (ds == null) {
				continue;
			}

			if (minStart == null || ds.isBefore(minStart)) {
				minStart = ds;
			}
			LocalDate de = endVal.isEmpty() ? null : parseDate(endVal);
			if (de != null && (maxEnd == null || de.isAfter(maxEnd))) {
				maxEnd = de;
			}
		}

		if (minStart == null) {
			return null;
		}
		return new FlightDates(minStart, maxEnd != null ? maxEnd : minStart);
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

	@Override
	public String extractFlightDates(List<List<String>> rows) {

		FlightDates fd = extractFlightTimestamps(rows);
		return fd == null ? null : formatFlightDates(fd.start(), fd.end());
	}

	@Override
	public FlightDates parseFlightDateRange(String dateStr) {

		if (dateStr == null) {
			return null;
		}
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
				if (m.find()) {
					rawStart = rawStart + ", " + m.group(1);
				}
			}
			LocalDate ds = parseDate(rawStart.trim());
			LocalDate de = parseDate(rawEnd.trim());
			if (ds != null && de != null) {
				return new FlightDates(ds, de);
			}
		}

		LocalDate single = parseDate(s);
		if (single != null) {
			return new FlightDates(single, single);
		}
		return null;
	}

	/**
	 * Implementation note: resolution priority is manual "Flight dates:" in adjustments →
	 * manual in sheet → auto-detected flight columns in adjustments → in sheet.
	 */
	@Override
	public FlightDates resolveFlightTimestamps(List<List<String>> sheetRows, List<List<String>> adjRows) {

		String manual = findLabelValue(adjRows, "Flight dates:");
		if (manual != null) {
			FlightDates parsed = parseFlightDateRange(manual);
			if (parsed != null) {
				return parsed;
			}
		}
		manual = findLabelValue(sheetRows, "Flight dates:");
		if (manual != null) {
			FlightDates parsed = parseFlightDateRange(manual);
			if (parsed != null) {
				return parsed;
			}
		}
		FlightDates fromAdj = extractFlightTimestamps(adjRows);
		if (fromAdj != null) {
			return fromAdj;
		}
		return extractFlightTimestamps(sheetRows);
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
