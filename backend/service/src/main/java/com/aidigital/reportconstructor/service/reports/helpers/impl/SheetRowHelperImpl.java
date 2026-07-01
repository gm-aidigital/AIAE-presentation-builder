package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
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
