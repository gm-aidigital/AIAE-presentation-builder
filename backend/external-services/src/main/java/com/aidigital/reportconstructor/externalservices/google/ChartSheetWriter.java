package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Writes pivoted BQ actuals into copied daily/monthly chart helper spreadsheets.
 * Owns column geometry (A=date, B=imps, C=CTR/VCR formula, D=clicks/completions).
 */
@Component
public class ChartSheetWriter {

	/**
	 * Column B — impressions (bars / left axis).
	 */
	public static final int IMPS_COL = 1;
	/**
	 * Column C — CTR/VCR rate (line / right axis); template ships a %-formatted formula.
	 */
	public static final int RATE_COL = 2;

	/**
	 * Decimal CTR/VCR ratio for a percentage-formatted cell ({@code 0.025} displays as 2.5%).
	 * Legacy PHP never multiplies by 100 before writing — doing so yields 250% in the sheet.
	 *
	 * @param imps        impressions denominator
	 * @param metricCount clicks or completions numerator
	 * @return ratio in {@code [0,1]}, or {@code 0} when {@code imps <= 0}
	 */
	public double dailyComboRateRatio(double imps, double metricCount) {
		if (imps <= 0) {
			return 0.0;
		}
		return metricCount / imps;
	}

	/**
	 * Legacy {@code writePivotToSheet} does not write column C — the daily template's
	 * CTR/VCR formula stays live. The combo line series reads column C from the sheet.
	 *
	 * @return {@code false} — column C is owned by the template formula, never overwritten
	 */
	public boolean shouldWriteDailyRateColumn() {
		return false;
	}

	/**
	 * Rate column body rows when explicitly writing values (normally empty — template formula).
	 *
	 * @param pivot      pivoted series
	 * @param hasClicks  whether numerator is clicks (else completions)
	 * @param forceWrite when {@code true}, bypass {@link #shouldWriteDailyRateColumn()} (tests only)
	 * @return cell values per row, or empty when column C must not be overwritten
	 */
	public Optional<List<List<Object>>> rateColumnValuesForPivot(
			Pivot pivot, boolean hasClicks, boolean forceWrite) {
		if (!forceWrite && !shouldWriteDailyRateColumn()) {
			return Optional.empty();
		}
		if (pivot.isEmpty()) {
			return Optional.empty();
		}
		List<List<Object>> rates = new ArrayList<>();
		for (double[] v : pivot.data().values()) {
			double num = hasClicks ? v[1] : v[2];
			double ratio = dailyComboRateRatio(v[0], num);
			rates.add(List.of(Math.round(ratio * 10000.0) / 10000.0));
		}
		return Optional.of(rates);
	}

	/**
	 * Rate column body rows, honouring {@link #shouldWriteDailyRateColumn()} (never forced).
	 *
	 * @param pivot     pivoted series
	 * @param hasClicks whether the numerator is clicks (else completions)
	 * @return cell values per row, or empty when column C must not be overwritten
	 * @see #rateColumnValuesForPivot(Pivot, boolean, boolean)
	 */
	public Optional<List<List<Object>>> rateColumnValuesForPivot(Pivot pivot, boolean hasClicks) {
		return rateColumnValuesForPivot(pivot, hasClicks, false);
	}

	/**
	 * Writes the pivot into the copied helper sheet, mirroring PHP
	 * {@code writePivotToSheet}: date (A), impressions (B), metric (D) only — never column C.
	 *
	 * @param sheets        authenticated Sheets client
	 * @param spreadsheetId id of the copied helper spreadsheet
	 * @param tabName       data tab to write into
	 * @param pivot         chronological daily/monthly series for the tactic
	 * @throws IOException when a Sheets API call fails
	 */
	public void writePivot(Sheets sheets, String spreadsheetId, String tabName, Pivot pivot) throws IOException {
		if (pivot.isEmpty()) {
			return;
		}
		boolean hasClicks = pivot.hasClicks();
		boolean hasCompletions = pivot.hasCompletions();

		List<List<Object>> rows = readValues(sheets, spreadsheetId, tabName + "!A1:ZZ3");

		int dataStartRow = -1;
		int dateCol = -1;
		int impsCol = -1;
		int metricCol = -1;
		int ctrHeaderCol = -1;
		int headerRow = -1;
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
				if (datePattern.matcher(cell).find()) {
					dateCol = ci;
					dataStartRow = ri;
				}
				if (impsPattern.matcher(cell).find()) {
					impsCol = ci;
					dataStartRow = ri;
				}
				if (metricPattern.matcher(cell).find()) {
					metricCol = ci;
					dataStartRow = ri;
				}
				if (upper.equals("CTR") || upper.equals("VCR")) {
					ctrHeaderCol = ci;
					headerRow = ri;
				}
			}
			if (dataStartRow >= 0 && dateCol >= 0) {
				break;
			}
		}

		if (dataStartRow < 0) {
			dataStartRow = 1;
		}
		if (dateCol < 0) {
			dateCol = 0;
		}
		if (impsCol < 0) {
			impsCol = 1;
		}
		if (metricCol < 0) {
			metricCol = 3;
		}

		boolean noCdCols = !hasClicks && !hasCompletions;

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

		if (!hasClicks && hasCompletions && ctrHeaderCol >= 0 && headerRow >= 0) {
			String hdrRange = tabName + "!" + colLetter(ctrHeaderCol) + (headerRow + 1);
			sheets.spreadsheets().values().update(spreadsheetId, hdrRange,
							new ValueRange().setValues(List.of(List.of("VCR"))))
					.setValueInputOption("RAW").execute();
		}

		int startRow = dataStartRow + 1;
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
		}

		sheets.spreadsheets().values().batchUpdate(spreadsheetId,
				new BatchUpdateValuesRequest().setValueInputOption("RAW").setData(data)).execute();
	}

	/**
	 * Writes the two-row distribution series (tactic + Other), mirroring PHP
	 * {@code writeDistributionToSheet}.
	 *
	 * @param sheets        authenticated Sheets client
	 * @param spreadsheetId id of the copied helper spreadsheet
	 * @param tabName       data tab to write into
	 * @param tacticName    label for the tactic slice
	 * @param tacticImps    impressions for the tactic slice
	 * @param otherImps     impressions for the "Other" slice, i.e. total impressions minus this tactic's impressions
	 * @throws IOException when a Sheets API call fails
	 */
	public void writeDistribution(
			Sheets sheets, String spreadsheetId, String tabName,
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
	 * Converts a 0-based column index to an A1 column letter (e.g. {@code 0 → A}, {@code 26 → AA}).
	 *
	 * @param col 0-based column index
	 * @return the A1 column letter(s)
	 */
	public String colLetter(int col) {
		StringBuilder sb = new StringBuilder();
		int c = col;
		do {
			sb.insert(0, (char) ('A' + (c % 26)));
			c = c / 26 - 1;
		} while (c >= 0);
		return sb.toString();
	}

	List<List<Object>> readValues(Sheets sheets, String spreadsheetId, String range) throws IOException {
		ValueRange vr = sheets.spreadsheets().values().get(spreadsheetId, range).execute();
		return vr.getValues() == null ? List.of() : vr.getValues();
	}

	int sheetIdForTab(Sheets sheets, String spreadsheetId, String tabName) throws IOException {
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

	String str(Object o) {
		return o == null ? "" : o.toString();
	}
}
