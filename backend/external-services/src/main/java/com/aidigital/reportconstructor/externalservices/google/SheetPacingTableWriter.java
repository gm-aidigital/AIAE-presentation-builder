package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot;
import com.aidigital.reportconstructor.service.reports.engine.Headers;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the Daily pacing / Monthly pacing / Channel Distribution tables for every active
 * tactic directly into a single shared tab, unlike {@link ChartSheetWriter} which writes one
 * tactic per copied chart-template spreadsheet. Every block is located by scanning for its
 * {@code "Daily pacing N"} / {@code "Monthly pacing N"} / {@code "Channel Distribution N"}
 * anchor label, then resolving the {@code {{tactic n ...}}} tokens within a bounded window
 * below/right of that anchor — never a fixed cell reference — so multiple tactics' blocks
 * sharing the same rows don't collide.
 */
@Component
public class SheetPacingTableWriter {

	/**
	 * Anchor label for a tactic's daily pacing block.
	 */
	static final Pattern DAILY_ANCHOR = Pattern.compile("(?i)^daily pacing\\s+(\\d+)$");
	/**
	 * Anchor label for a tactic's monthly pacing block.
	 */
	static final Pattern MONTHLY_ANCHOR = Pattern.compile("(?i)^monthly pacing\\s+(\\d+)$");
	/**
	 * Anchor label for a tactic's channel distribution block.
	 */
	static final Pattern DISTRIBUTION_ANCHOR = Pattern.compile("(?i)^channel distribution\\s+(\\d+)$");

	/**
	 * How many rows below a block's anchor cell to search for its tokens.
	 */
	static final int SEARCH_ROWS_DOWN = 12;
	/**
	 * How many columns right of a block's anchor cell to search for its tokens.
	 */
	static final int SEARCH_COLS_RIGHT = 8;
	/**
	 * How many columns left of a block's anchor cell to search for its tokens. The {@code "Daily pacing N"}
	 * anchor label sits over the impressions column, so a tactic's {@code date} token sits one or more columns
	 * to its left; the search therefore extends left as well as right. Cross-tactic bleed is impossible because
	 * every token pattern carries the exact tactic number, so a neighbour's tokens never match.
	 */
	static final int SEARCH_COLS_LEFT = 8;

	private final ChartPivot chartPivot;
	private final TacticLineItemGrouper lineItemGrouper;
	private final ChartSheetWriter chartSheetWriter;

	/**
	 * Creates the writer over its pure-data and cell-utility collaborators.
	 *
	 * @param chartPivot        BQ header parsing and daily/monthly/multi-year pivot logic
	 * @param lineItemGrouper   groups the flat tactic-to-line-item mapping by tactic number
	 * @param chartSheetWriter  reused for its {@code readValues}/{@code colLetter}/{@code sheetIdForTab} utilities
	 */
	public SheetPacingTableWriter(
			ChartPivot chartPivot, TacticLineItemGrouper lineItemGrouper, ChartSheetWriter chartSheetWriter) {
		this.chartPivot = chartPivot;
		this.lineItemGrouper = lineItemGrouper;
		this.chartSheetWriter = chartSheetWriter;
	}

	/**
	 * Reads the tab once, locates every tactic's three blocks by their anchor labels, and
	 * writes all resolved values in a single batch update.
	 *
	 * @param sheets        authenticated Sheets client
	 * @param spreadsheetId the workbook to write into
	 * @param tabName       the tab carrying the pacing/distribution blocks
	 * @param req           the pacing-table inputs
	 * @return human-readable error strings for any per-tactic/block failures (empty on full success)
	 * @throws IOException when a Sheets API call fails
	 */
	public List<String> writeAll(Sheets sheets, String spreadsheetId, String tabName, PacingTablesRequest req)
			throws IOException {
		List<String> errors = new ArrayList<>();
		Headers headers = chartPivot.parseBqHeaders(req.bqRows());
		if (!headers.valid()) {
			errors.add("Pacing tables skipped — BQ sheet Date or Impressions column not found");
			return errors;
		}

		List<List<Object>> grid = chartSheetWriter.readValues(sheets, spreadsheetId, tabName + "!A1:ZZ");
		Map<Integer, int[]> dailyAnchors = findNumberedAnchors(grid, DAILY_ANCHOR);
		Map<Integer, int[]> monthlyAnchors = findNumberedAnchors(grid, MONTHLY_ANCHOR);
		Map<Integer, int[]> distAnchors = findNumberedAnchors(grid, DISTRIBUTION_ANCHOR);

		Map<Integer, List<String>> tacticLineItems = lineItemGrouper.groupByTactic(req.lineItemMapping());
		// The monthly blocks may span a wider window than the daily ones (EOM reports on a single month but
		// charts every month since campaign start), so their multi-year labelling is decided separately.
		FlightDates monthlyTs = req.monthlyTs() != null ? req.monthlyTs() : req.flightTs();
		boolean monthlyMultiYear = chartPivot.isMultiYear(req.bqRows(), headers, monthlyTs);

		List<ValueRange> data = new ArrayList<>();
		for (int n = 1; n <= req.tacticCount(); n++) {
			List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
			String kpiType = req.tacticKpiTypes() == null ? null : req.tacticKpiTypes().get(n);

			collectPacingBlock(data, grid, dailyAnchors.get(n), tabName, n, false,
					() -> chartPivot.buildDailyPivot(req.bqRows(), liIds, headers, req.flightTs()),
					kpiType, "Daily pacing " + n, errors);
			collectPacingBlock(data, grid, monthlyAnchors.get(n), tabName, n, true,
					() -> chartPivot.buildMonthlyPivot(req.bqRows(), liIds, headers, monthlyTs, monthlyMultiYear),
					kpiType, "Monthly pacing " + n, errors);
			collectDistributionBlock(data, grid, distAnchors.get(n), tabName, n,
					req.distTacticNames().getOrDefault(n, "Tactic " + n),
					req.distTacticImps().getOrDefault(n, 0.0),
					req.distTotalImps() - req.distTacticImps().getOrDefault(n, 0.0),
					errors);
		}

		if (!data.isEmpty()) {
			sheets.spreadsheets().values().batchUpdate(spreadsheetId,
					new BatchUpdateValuesRequest().setValueInputOption("RAW").setData(data)).execute();
		}
		return errors;
	}

	/**
	 * Resolves one tactic's daily/monthly pacing block and appends its cell writes to
	 * {@code data}. Records an error instead of writing anything when the anchor is missing,
	 * the pivot has no data, or a required column could not be resolved.
	 *
	 * @param data      collector for the batched cell writes
	 * @param grid      the tab's full cell grid
	 * @param anchor    the block's anchor cell {@code [row, col]}, or {@code null} when not found
	 * @param tabName   the tab carrying the block
	 * @param tacticNum one-based tactic number
	 * @param monthly   {@code true} for the monthly block, {@code false} for the daily block
	 * @param pivotOf   supplier for the tactic's pivot (invoked only when the anchor is found)
	 * @param kpiType   tactic KPI type ({@code "ctr"}/{@code "vcr"}), or {@code null} to derive from the pivot
	 * @param blockTag  label used in error messages
	 * @param errors    collector for non-fatal per-block errors
	 */
	void collectPacingBlock(
			List<ValueRange> data, List<List<Object>> grid, int[] anchor, String tabName,
			int tacticNum, boolean monthly, Supplier<Pivot> pivotOf,
			String kpiType, String blockTag, List<String> errors) {
		if (anchor == null) {
			errors.add(blockTag + ": anchor not found");
			return;
		}
		Pivot pivot = pivotOf.get();
		if (pivot.isEmpty()) {
			errors.add(blockTag + ": no BQ data for tactic " + tacticNum);
			return;
		}
		PacingColumns cols = findPacingColumns(grid, anchor, tacticNum, monthly);
		if (cols.dateCol() < 0 || cols.impsCol() < 0 || cols.dataStartRow() < 0) {
			errors.add(blockTag + ": date/impressions tokens not found near anchor");
			return;
		}

		boolean useClicks = kpiType != null ? "ctr".equalsIgnoreCase(kpiType) : pivot.hasClicks();
		int startRow = cols.dataStartRow() + 1;
		int endRow = startRow + pivot.data().size() - 1;

		List<List<Object>> dates = new ArrayList<>();
		List<List<Object>> imps = new ArrayList<>();
		List<List<Object>> metrics = new ArrayList<>();
		for (String label : pivot.data().keySet()) {
			dates.add(List.of(label));
		}
		for (double[] v : pivot.data().values()) {
			imps.add(List.of(Math.round(v[0])));
			metrics.add(List.of(Math.round(useClicks ? v[1] : v[2])));
		}

		data.add(rangeValue(tabName, cols.dateCol(), startRow, cols.dateCol(), endRow, dates));
		data.add(rangeValue(tabName, cols.impsCol(), startRow, cols.impsCol(), endRow, imps));
		if (cols.metricCol() >= 0 && (pivot.hasClicks() || pivot.hasCompletions())) {
			data.add(rangeValue(tabName, cols.metricCol(), startRow, cols.metricCol(), endRow, metrics));
		}
		if (cols.kpiHeaderCol() >= 0 && cols.kpiHeaderRow() >= 0) {
			data.add(rangeValue(tabName, cols.kpiHeaderCol(), cols.kpiHeaderRow() + 1,
					cols.kpiHeaderCol(), cols.kpiHeaderRow() + 1,
					List.of(List.of(kpiHeaderLabel(useClicks, kpiType)))));
		}
	}

	/**
	 * Chooses the KPI column header for a pacing block: {@code "CTR"} for click-led tactics, otherwise the
	 * completion-rate label — {@code "ACR"} when the tactic's series token is {@code "acr"} (audio), else
	 * {@code "VCR"}.
	 *
	 * @param useClicks whether the block renders the clicks/CTR series
	 * @param kpiType   the tactic's KPI-series token ({@code "ctr"}/{@code "vcr"}/{@code "acr"}, or {@code null})
	 * @return the header label to write
	 */
	String kpiHeaderLabel(boolean useClicks, String kpiType) {
		if (useClicks) {
			return "CTR";
		}
		return "acr".equalsIgnoreCase(kpiType) ? "ACR" : "VCR";
	}

	/**
	 * Resolves one tactic's channel distribution block and appends its cell writes to
	 * {@code data}. Records an error instead of writing anything when the anchor or the
	 * tactic's slice row could not be located.
	 *
	 * @param data       collector for the batched cell writes
	 * @param grid       the tab's full cell grid
	 * @param anchor     the block's anchor cell {@code [row, col]}, or {@code null} when not found
	 * @param tabName    the tab carrying the block
	 * @param tacticNum  one-based tactic number
	 * @param tacticName display label for the tactic's slice
	 * @param tacticImps impressions for the tactic's slice
	 * @param otherImps  impressions for the "Other" slice (total minus this tactic's impressions)
	 * @param errors     collector for non-fatal per-block errors
	 */
	void collectDistributionBlock(
			List<ValueRange> data, List<List<Object>> grid, int[] anchor, String tabName,
			int tacticNum, String tacticName, double tacticImps, double otherImps, List<String> errors) {
		if (anchor == null) {
			errors.add("Channel Distribution " + tacticNum + ": anchor not found");
			return;
		}
		DistributionColumns cols = findDistributionColumns(grid, anchor, tacticNum, tacticName);
		if (cols.tacticRow() < 0) {
			errors.add("Channel Distribution " + tacticNum + ": {{tactic " + tacticNum + "}} slice row not found");
			return;
		}

		data.add(rangeValue(tabName, cols.labelCol(), cols.tacticRow() + 1, cols.labelCol() + 1, cols.tacticRow() + 1,
				List.of(List.of(tacticName, Math.round(tacticImps)))));
		if (cols.otherRow() >= 0) {
			data.add(rangeValue(tabName, cols.labelCol(), cols.otherRow() + 1, cols.labelCol() + 1, cols.otherRow() + 1,
					List.of(List.of("Other", Math.round(otherImps)))));
		}
	}

	/**
	 * Scans the whole grid for cells matching {@code labelPattern}, capturing the trailing
	 * tactic number.
	 *
	 * @param grid         the tab's full cell grid
	 * @param labelPattern anchor label pattern with a single digit capturing group
	 * @return tactic number (1-based) to its anchor cell's zero-based {@code [row, col]}
	 */
	Map<Integer, int[]> findNumberedAnchors(List<List<Object>> grid, Pattern labelPattern) {
		Map<Integer, int[]> anchors = new LinkedHashMap<>();
		for (int r = 0; r < grid.size(); r++) {
			List<Object> row = grid.get(r);
			for (int c = 0; c < row.size(); c++) {
				Matcher m = labelPattern.matcher(str(row.get(c)).trim());
				if (m.matches()) {
					anchors.put(Integer.parseInt(m.group(1)), new int[] {r, c});
				}
			}
		}
		return anchors;
	}

	/**
	 * Resolves a tactic's daily/monthly pacing columns within a bounded window below the block
	 * anchor and spanning columns on both sides of it (the anchor label sits over the impressions
	 * column, so the {@code date} token is to its left), matching only that tactic's number and,
	 * for the monthly block, only tokens carrying the {@code mon} suffix.
	 *
	 * @param grid      the tab's full cell grid
	 * @param anchor    the block's anchor cell {@code [row, col]}
	 * @param tacticNum one-based tactic number to match exactly
	 * @param monthly   {@code true} to match {@code {{tactic n date mon}}}-style tokens instead of plain ones
	 * @return the located columns (each {@code -1} when not found within the window)
	 */
	PacingColumns findPacingColumns(List<List<Object>> grid, int[] anchor, int tacticNum, boolean monthly) {
		String prefix = "\\{\\{tactic\\s+" + tacticNum + "\\s+";
		String suffix = monthly ? "\\s+mon\\}\\}" : "\\}\\}";
		Pattern datePattern = Pattern.compile(prefix + "date" + suffix, Pattern.CASE_INSENSITIVE);
		Pattern impsPattern = Pattern.compile(prefix + "impressions?" + suffix, Pattern.CASE_INSENSITIVE);
		Pattern metricPattern = Pattern.compile(
				prefix + "(?:clicks?|completions?|amount)" + suffix, Pattern.CASE_INSENSITIVE);
		Pattern kpiPattern = Pattern.compile(prefix + "kpi\\s+type" + suffix, Pattern.CASE_INSENSITIVE);

		int dataStartRow = -1;
		int dateCol = -1;
		int impsCol = -1;
		int metricCol = -1;
		int kpiHeaderRow = -1;
		int kpiHeaderCol = -1;

		int rowTo = Math.min(grid.size(), anchor[0] + SEARCH_ROWS_DOWN);
		for (int r = anchor[0]; r < rowTo; r++) {
			List<Object> row = grid.get(r);
			int colFrom = Math.max(0, anchor[1] - SEARCH_COLS_LEFT);
			int colTo = Math.min(row.size(), anchor[1] + SEARCH_COLS_RIGHT);
			for (int c = colFrom; c < colTo; c++) {
				String cell = str(row.get(c));
				if (datePattern.matcher(cell).find()) {
					dateCol = c;
					dataStartRow = r;
				}
				if (impsPattern.matcher(cell).find()) {
					impsCol = c;
					dataStartRow = r;
				}
				if (metricPattern.matcher(cell).find()) {
					metricCol = c;
					dataStartRow = r;
				}
				if (kpiPattern.matcher(cell).find()) {
					kpiHeaderRow = r;
					kpiHeaderCol = c;
				}
			}
			if (dataStartRow >= 0 && dateCol >= 0) {
				break;
			}
		}
		return new PacingColumns(dataStartRow, dateCol, impsCol, metricCol, kpiHeaderRow, kpiHeaderCol);
	}

	/**
	 * Resolves a tactic's channel-distribution slice row and its "Other" row within a
	 * bounded window below its block anchor, searching only the anchor's own column so a
	 * neighbouring tactic's block never matches. The slice row is matched either by the
	 * literal {@code {{tactic n}}} token or by the already-resolved tactic name, because on
	 * the SHEET target the token is replaced with the tactic name by the earlier
	 * find/replace pass before this writer runs.
	 *
	 * @param grid       the tab's full cell grid
	 * @param anchor     the block's anchor cell {@code [row, col]}
	 * @param tacticNum  one-based tactic number to match exactly
	 * @param tacticName resolved tactic name the token was replaced with, or {@code null}
	 * @return the located rows (each {@code -1} when not found within the window)
	 */
	DistributionColumns findDistributionColumns(
			List<List<Object>> grid, int[] anchor, int tacticNum, String tacticName) {
		Pattern tacticExact = Pattern.compile("\\{\\{tactic\\s+" + tacticNum + "\\}\\}", Pattern.CASE_INSENSITIVE);
		String resolvedName = tacticName == null ? "" : tacticName.trim();
		int labelCol = anchor[1];
		int tacticRow = -1;
		int otherRow = -1;

		int rowTo = Math.min(grid.size(), anchor[0] + SEARCH_ROWS_DOWN);
		for (int r = anchor[0]; r < rowTo; r++) {
			List<Object> row = grid.get(r);
			String cell = labelCol < row.size() ? str(row.get(labelCol)).trim() : "";
			boolean matchesSlice = tacticExact.matcher(cell).matches()
					|| (!resolvedName.isEmpty() && cell.equalsIgnoreCase(resolvedName));
			if (tacticRow < 0 && matchesSlice) {
				tacticRow = r;
				continue;
			}
			if (tacticRow >= 0 && r > tacticRow) {
				String lower = cell.toLowerCase(Locale.ROOT);
				if (lower.equals("total") || lower.equals("other") || lower.equals("rest")) {
					otherRow = r;
					break;
				}
			}
		}
		return new DistributionColumns(labelCol, tacticRow, otherRow);
	}

	/**
	 * Builds a rectangular {@link ValueRange} from zero-based row/column bounds (both inclusive).
	 *
	 * @param tabName    the tab the range belongs to
	 * @param startCol   zero-based inclusive start column
	 * @param startRow   one-based inclusive start row
	 * @param endCol     zero-based inclusive end column
	 * @param endRow     one-based inclusive end row
	 * @param values     the cell values, one inner list per row
	 * @return the populated range
	 */
	ValueRange rangeValue(String tabName, int startCol, int startRow, int endCol, int endRow,
			List<List<Object>> values) {
		String range = tabName + "!" + chartSheetWriter.colLetter(startCol) + startRow
				+ ":" + chartSheetWriter.colLetter(endCol) + endRow;
		return new ValueRange().setRange(range).setValues(values);
	}

	/**
	 * Null-safe cell-to-string conversion.
	 *
	 * @param o raw cell value from the Sheets API (may be {@code null})
	 * @return the cell's string form, or an empty string when {@code null}
	 */
	String str(Object o) {
		return o == null ? "" : o.toString();
	}
}
