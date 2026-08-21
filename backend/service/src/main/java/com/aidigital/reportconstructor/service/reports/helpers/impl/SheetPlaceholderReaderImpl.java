package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link SheetPlaceholderReader}. Pure logic over the
 * cell grid — no Google client dependency — so it is unit-testable in isolation, and
 * reuses {@link SheetRowHelper} for the right-of-label lookups it shares with raw-input
 * parsing.
 */
@Component
@RequiredArgsConstructor
public class SheetPlaceholderReaderImpl implements SheetPlaceholderReader {

	/** Max tactics the EOC template carries (summary rows and "Main slide" blocks). */
	private static final int MAX_TACTICS = 28;

	/** Column-0 tactic-name cell that marks the summary table's totals row. */
	private static final String TOTALS_LABEL = "Total";

	/**
	 * Matches a tactic-name cell that carries no real tactic: empty, whitespace, or only dash
	 * characters (ASCII hyphen and the Unicode hyphen/dash block {@code ‐}-{@code ―}).
	 * The EOC template leaves such a dash in unused summary rows, and treating it as a tactic
	 * would inflate the tactic count and leave those rows in the generated deck.
	 */
	private static final Pattern BLANK_OR_DASH = Pattern.compile("[-\\s\\u2010-\\u2015]*");

	/** Header cell that anchors the per-tactic summary table. */
	private static final String SUMMARY_ANCHOR_HEADER = "Tactic name";

	/**
	 * Matches a per-tactic detail block's anchor cell, {@code "Main slide N"} (1-based tactic
	 * number in group 1). The writer emits one such cell per block, so the reader keys each
	 * block by its explicit number rather than its position — mirroring the sheet layout.
	 */
	private static final Pattern MAIN_SLIDE_ANCHOR = Pattern.compile("(?i)^main slide\\s+(\\d+)$");

	/** Rows below the "Main slide" anchor row that a detail block can span. */
	private static final int MAIN_SLIDE_WINDOW = 20;

	/** Rows below the summary header scanned for tactic and totals rows. */
	private static final int SUMMARY_WINDOW = 30;

	/**
	 * Top info-block labels (matched in the cell to their left) mapped to the full
	 * placeholder token whose value sits in the cell to their right.
	 */
	private static final Map<String, String> INFO_LABEL_TOKENS = Map.ofEntries(
			Map.entry("Client name:", "{{client_name}}"),
			Map.entry("Campaign name:", "{{Campaign_name}}"),
			Map.entry("Flight dates:", "{{flight_dates}}"),
			Map.entry("Tactics list:", "{{tactics_list}}"),
			Map.entry("KPI:", "{{primary_kpis}}"),
			Map.entry("Geo:", "{{geo_locations}}"),
			Map.entry("Funnel:", "{{funnel_stages}}"),
			Map.entry("Audience age:", "{{audience_age}}"),
			Map.entry("Segments:", "{{audience_segments}}"),
			// EOM cover cadence. These cells are the only place the cover's dates survive into the
			// slides-from-sheet step, which never sees the media plan again — and they double as the user's
			// override, since whatever the sheet says wins over anything recomputed here. Each token is
			// registered under every label spelling the workbook has used, because a miss is silent: the
			// cover would simply print a dash.
			// The workbook writes these four without a trailing colon, unlike the labels above it; both
			// spellings are registered so a later tidy-up of the template cannot silently break the read.
			Map.entry("Reporting dates", "{{reporting filter}}"),
			Map.entry("Reporting dates:", "{{reporting filter}}"),
			Map.entry("Reporting month", "{{reporting month}}"),
			Map.entry("Reporting month:", "{{reporting month}}"),
			Map.entry("Campaign duration (months)", "{{total mon no}}"),
			Map.entry("Campaign duration (months):", "{{total mon no}}"),
			Map.entry("Reporting month no.", "{{mon no}}"),
			Map.entry("Reporting month no.:", "{{mon no}}"),
			Map.entry("Reporting month no:", "{{mon no}}"),
			// EOM north-star slide: the channel lists grouped off the media plan's Goal column when the
			// workbook was built. Reading them back here is what lets the user regroup a channel in the sheet
			// and have the deck print the edit. Registered with and without the trailing colon, like the
			// cadence labels above, because a miss is silent — the slide would simply print a dash.
			Map.entry("Awareness channels", "{{awareness channels}}"),
			Map.entry("Awareness channels:", "{{awareness channels}}"),
			Map.entry("Consideration channels", "{{consideration channels}}"),
			Map.entry("Consideration channels:", "{{consideration channels}}"),
			Map.entry("Conversions channels", "{{conversions channels}}"),
			Map.entry("Conversions channels:", "{{conversions channels}}"));

	/** Info-block header whose value sits in the cell <em>below</em> it. */
	private static final String RFP_HEADER = "RFP Input";
	private static final String RFP_TOKEN = "{{RFP info}}";

	/**
	 * Prefix of the change-log block's label cell. Matched as a prefix, and the value taken from either the
	 * cell to the right or the cell below, because this one label is spelled differently across template
	 * revisions ({@code "Change log"}, {@code "Change log:"}, {@code "Change Log Input"}) and laid out both
	 * ways — and a miss here is silent: the slides step would simply run without the change log.
	 */
	private static final String CHANGE_LOG_LABEL_PREFIX = "change log";
	private static final String CHANGE_LOG_TOKEN = "{{change log}}";

	/**
	 * Summary-table column headers mapped to the per-tactic token suffix. The value is
	 * assembled as {@code "{{tactic " + n + suffix + "}}"}; the tactic-name column uses
	 * an empty suffix ({@code {{tactic n}}}). The en dash in the benchmark token matches
	 * the template token exactly.
	 */
	private static final Map<String, String> SUMMARY_TACTIC_SUFFIXES = Map.ofEntries(
			Map.entry("Tactic name", ""),
			Map.entry("Benchmark", " – bench"),
			Map.entry("KPI type", " KPI type"),
			Map.entry("KPI (fact)", " KPI"),
			Map.entry("Impressions Fact", " imps"),
			Map.entry("Impressions Plan", " imps plan"),
			// Same token as "Impressions Plan": the column now carries whichever unit the tactic was
			// bought in (clicks/completions/impressions), so the template may be relabelled "Unit Plan".
			Map.entry("Unit Plan", " imps plan"),
			Map.entry("Clicks Fact", " clicks"),
			Map.entry("Clicks Plan", " clicks plan"),
			Map.entry("Completions Fact", " complitions"),
			Map.entry("Completions Plan", " completions plan"),
			Map.entry("CTR Fact", " ctr"),
			Map.entry("CTR Plan", " ctr plan"),
			Map.entry("VCR Fact", " vcr"),
			Map.entry("VCR Plan", " vcr plan"),
			Map.entry("Spend Fact", " spend"),
			Map.entry("Spend Plan", " spend plan"),
			Map.entry("Reach", " reach"),
			Map.entry("Frequency", " f"),
			Map.entry("Market Volume", " volume"));

	/**
	 * Summary-table column headers whose per-tactic token is not a {@code {{tactic n …}}} token, mapped
	 * to the token format their value is emitted under ({@code %d} is the 1-based tactic number). The
	 * EOM "Unit rate" / "Rate type" columns are filled from the {@code {{unit N rate}}} and
	 * {@code {{rate type N}}} template tokens, so they are read back under the same names. The "SO WHAT?"
	 * column is registered under both spellings of its header, so dropping the question mark in the template
	 * does not silently stop the read-back — which is what lets the user rewrite the generated phrase in the
	 * sheet and have the deck print the edit.
	 */
	private static final Map<String, String> SUMMARY_TACTIC_TOKEN_FORMATS = Map.of(
			"Unit rate", "{{unit %d rate}}",
			"Rate type", "{{rate type %d}}",
			"So what?", "{{so what %d}}",
			"So what", "{{so what %d}}");

	/**
	 * Summary-table column headers mapped to the campaign-level total token read from the
	 * {@code "Total"} row (columns without a totals token in the template are simply absent).
	 */
	private static final Map<String, String> SUMMARY_TOTAL_TOKENS = Map.ofEntries(
			Map.entry("Impressions Fact", "{{total imps}}"),
			Map.entry("Impressions Plan", "{{total imps plan}}"),
			Map.entry("Clicks Fact", "{{total clicks}}"),
			Map.entry("Completions Fact", "{{total complitions}}"),
			Map.entry("CTR Fact", "{{total ctr}}"),
			Map.entry("CTR Plan", "{{total ctr plan}}"),
			Map.entry("VCR Fact", "{{total vcr}}"),
			Map.entry("Spend Fact", "{{total_investment}}"),
			Map.entry("Spend Plan", "{{total_investment_plan}}"),
			Map.entry("Reach", "{{reach}}"),
			Map.entry("Frequency", "{{reach_f}}"),
			Map.entry("Market Volume", "{{market volume}}"));

	/**
	 * "Main slide" block field labels (matched in the block's anchor column) mapped to the
	 * per-tactic token suffix; the value sits in the next column.
	 */
	private static final Map<String, String> MAIN_SLIDE_SUFFIXES = Map.ofEntries(
			Map.entry("Tactic Goal", " goal"),
			Map.entry("Weekdays", " weekdays"),
			Map.entry("Weekends", " weekends"),
			Map.entry("Male", " male"),
			Map.entry("Female", " female"),
			Map.entry("Creative Name:", " top creative name"),
			Map.entry("Impressions:", " top creative imps"),
			Map.entry("Clicks:", " top creative clicks"));

	/**
	 * Header cell that anchors a per-tactic metric table — the workbook block that faces the deck's
	 * channel slide (METRIC × month goal / month actual / vs goal / EOC goal / EOC projection).
	 */
	private static final String METRIC_ANCHOR_HEADER = "METRIC";

	/** Rows below a metric header that the block's metric rows can span. */
	private static final int METRIC_TABLE_WINDOW = 10;

	/** Columns right of a metric header scanned for the block's value columns. */
	private static final int METRIC_COLUMN_WINDOW = 8;

	/** Rows above a metric header scanned for the {@code "Main slide N"} anchor that numbers the block. */
	private static final int METRIC_TACTIC_WINDOW = 30;

	/** Metric-table column position: the reporting month's goal. */
	private static final int METRIC_COL_MONTH_GOAL = 0;

	/** Metric-table column position: the reporting month's actual. */
	private static final int METRIC_COL_MONTH_ACTUAL = 1;

	/** Metric-table column position: actual against the month's goal. */
	private static final int METRIC_COL_VS_GOAL = 2;

	/** Metric-table column position: the full-flight (end-of-campaign) goal. */
	private static final int METRIC_COL_EOC_GOAL = 3;

	/** Metric-table column position: the end-of-campaign projection. */
	private static final int METRIC_COL_EOC_PROJ = 4;

	/** Number of value columns a metric table carries, right of its label column. */
	private static final int METRIC_COL_COUNT = 5;

	/**
	 * Metric-table row labels mapped to the per-tactic token suffix of each of the five value columns,
	 * in {@link #METRIC_COL_MONTH_GOAL} … {@link #METRIC_COL_EOC_PROJ} order. The names are the template's
	 * own: this block is read back under exactly the tokens the channel slide prints, so an edit in the
	 * workbook reaches the slide unchanged.
	 *
	 * <p>CTR and CPM deliberately repeat one suffix across the month-goal and EOC-goal columns — the
	 * template fills both cells from the same token, because a rate's monthly goal is its flight goal.
	 * Spend does not: its month goal is the month's budget ({@code spend plan}, which the summary table
	 * above already carries) while its flight goal is the media plan's total cost, so the EOC column has
	 * a token of its own ({@code spend plan eoc}) rather than two meanings under one name.
	 */
	static final Map<String, List<String>> METRIC_ROW_SUFFIXES = Map.of(
			"impressions", List.of(" planned imps", " fact imps", " imps pacing", " eoc planned imps",
					" proj imps"),
			"ctr", List.of(" ctr plan", " ctr", " ctr pacing", " ctr plan", " ctr proj"),
			"clicks", List.of(" clicks plan", " clicks", " clicks pacing", " clicks mp", " clicks proj"),
			"reach", List.of(" reach plan", " reach", " reach pacing", " reach plan eoc", " reach proj"),
			"cpm", List.of(" planned cpm", " fact cpm", " cpm pacing", " planned cpm", " cpm proj"),
			"spend", List.of(" planned budget", " fact budget", " budget pacing", " spend plan eoc",
					" spend proj"));

	private final SheetRowHelper rows;

	@Override
	public Map<String, String> readPlaceholders(List<List<String>> grid) {
		Map<String, String> out = new LinkedHashMap<>();
		if (grid == null || grid.isEmpty()) {
			return out;
		}
		readInfoBlock(grid, out);
		readSummaryTable(grid, out);
		readMainSlideBlocks(grid, out);
		readMetricTables(grid, out);
		return out;
	}

	/**
	 * Reads the top info block: each {@link #INFO_LABEL_TOKENS} label's right-hand value, the
	 * {@link #RFP_TOKEN} value beneath the {@link #RFP_HEADER} header, and the change-log block.
	 *
	 * @param grid the workbook grid
	 * @param out  the accumulating placeholder map
	 */
	void readInfoBlock(List<List<String>> grid, Map<String, String> out) {
		for (Map.Entry<String, String> e : INFO_LABEL_TOKENS.entrySet()) {
			emit(out, e.getValue(), rows.findLabelValue(grid, e.getKey()));
		}
		emit(out, RFP_TOKEN, rows.findLabelValueBelow(grid, RFP_HEADER));
		emit(out, CHANGE_LOG_TOKEN, findChangeLogValue(grid));
	}

	/**
	 * Finds the change-log text: locates the first cell whose text starts with
	 * {@link #CHANGE_LOG_LABEL_PREFIX} and returns the value to its right, or — when that cell is empty —
	 * the value beneath it. Tolerating both layouts is deliberate; see the constant's note.
	 *
	 * @param grid the workbook grid
	 * @return the change-log text, or {@code null} when the sheet carries no change-log block or it is empty
	 */
	String findChangeLogValue(List<List<String>> grid) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				if (!rows.cellAt(row, c).toLowerCase(Locale.ROOT).startsWith(CHANGE_LOG_LABEL_PREFIX)) {
					continue;
				}
				String right = rows.cellAt(row, c + 1);
				if (!right.isEmpty()) {
					return right;
				}
				String below = r + 1 < grid.size() ? rows.cellAt(grid.get(r + 1), c) : "";
				return below.isEmpty() ? null : below;
			}
		}
		return null;
	}

	/**
	 * Reads the per-tactic summary table: locates the header row by {@link #SUMMARY_ANCHOR_HEADER},
	 * maps each known header to its column, then reads one tactic per row until the {@link #TOTALS_LABEL}
	 * row — which is read into the campaign-level total tokens.
	 *
	 * @param grid the workbook grid
	 * @param out  the accumulating placeholder map
	 */
	void readSummaryTable(List<List<String>> grid, Map<String, String> out) {
		int headerRow = findRowContaining(grid, SUMMARY_ANCHOR_HEADER);
		if (headerRow < 0) {
			return;
		}
		Map<String, Integer> headerCols = mapHeaderColumns(grid.get(headerRow));
		Integer nameCol = headerCols.get(SUMMARY_ANCHOR_HEADER.toLowerCase(Locale.ROOT));
		if (nameCol == null) {
			return;
		}

		int tactic = 0;
		int limit = Math.min(grid.size(), headerRow + 1 + SUMMARY_WINDOW);
		for (int r = headerRow + 1; r < limit; r++) {
			List<String> row = grid.get(r);
			String name = rows.cellAt(row, nameCol);
			if (TOTALS_LABEL.equalsIgnoreCase(name)) {
				readSummaryColumns(row, headerCols, SUMMARY_TOTAL_TOKENS, out);
				return;
			}
			if (isBlankOrDash(name)) {
				continue;
			}
			if (++tactic > MAX_TACTICS) {
				return;
			}
			readTacticRow(row, headerCols, tactic, out);
		}
	}

	/**
	 * Emits every {@link #SUMMARY_TACTIC_SUFFIXES} column of one tactic's summary row as a
	 * {@code {{tactic n <suffix>}}} placeholder, plus the {@link #SUMMARY_TACTIC_TOKEN_FORMATS} columns
	 * whose token is named differently.
	 *
	 * @param row        the tactic's summary row
	 * @param headerCols header text (lowercased) to column index
	 * @param tactic     1-based tactic number
	 * @param out        the accumulating placeholder map
	 */
	void readTacticRow(List<String> row, Map<String, Integer> headerCols, int tactic, Map<String, String> out) {
		for (Map.Entry<String, String> e : SUMMARY_TACTIC_SUFFIXES.entrySet()) {
			Integer col = headerCols.get(e.getKey().toLowerCase(Locale.ROOT));
			if (col != null) {
				emit(out, "{{tactic " + tactic + e.getValue() + "}}", rows.cellAt(row, col));
			}
		}
		for (Map.Entry<String, String> e : SUMMARY_TACTIC_TOKEN_FORMATS.entrySet()) {
			Integer col = headerCols.get(e.getKey().toLowerCase(Locale.ROOT));
			if (col != null) {
				emit(out, String.format(Locale.ROOT, e.getValue(), tactic), rows.cellAt(row, col));
			}
		}
	}

	/**
	 * Emits the campaign-level totals row by mapping each summary column to its total token.
	 *
	 * @param row         the totals row
	 * @param headerCols  header text (lowercased) to column index
	 * @param totalTokens header text to the full total placeholder token
	 * @param out         the accumulating placeholder map
	 */
	void readSummaryColumns(List<String> row, Map<String, Integer> headerCols,
			Map<String, String> totalTokens, Map<String, String> out) {
		for (Map.Entry<String, String> e : totalTokens.entrySet()) {
			Integer col = headerCols.get(e.getKey().toLowerCase(Locale.ROOT));
			if (col != null) {
				emit(out, e.getValue(), rows.cellAt(row, col));
			}
		}
	}

	/**
	 * Reads the per-tactic "Main slide" detail blocks. Every {@link #MAIN_SLIDE_ANCHOR} cell in the
	 * grid anchors one block; its captured number is the block's 1-based tactic number, so blocks are
	 * keyed by their explicit label ({@code "Main slide 1"} … {@code "Main slide 28"}) rather than their
	 * position. Within each block, every {@link #MAIN_SLIDE_SUFFIXES} label is located in the block's
	 * anchor column and its value read from the next column.
	 *
	 * @param grid the workbook grid
	 * @param out  the accumulating placeholder map
	 */
	void readMainSlideBlocks(List<List<String>> grid, Map<String, String> out) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				Matcher m = MAIN_SLIDE_ANCHOR.matcher(rows.cellAt(row, c));
				if (m.matches()) {
					int tactic = Integer.parseInt(m.group(1));
					if (tactic >= 1 && tactic <= MAX_TACTICS) {
						readMainSlideBlock(grid, r, c, tactic, out);
					}
				}
			}
		}
	}

	/**
	 * Reads one "Main slide" block: for each field label, scans down the block's anchor column within
	 * {@link #MAIN_SLIDE_WINDOW} rows and emits the value from the adjacent column. The scan stops early
	 * at the next {@link #MAIN_SLIDE_ANCHOR} cell so a block stacked directly below does not leak its
	 * values into this tactic's placeholders.
	 *
	 * @param grid      the workbook grid
	 * @param anchorRow row of the block's "Main slide" anchor cell
	 * @param col       the block's anchor column
	 * @param tactic    1-based tactic number for this block
	 * @param out       the accumulating placeholder map
	 */
	void readMainSlideBlock(List<List<String>> grid, int anchorRow, int col, int tactic, Map<String, String> out) {
		int limit = Math.min(grid.size(), anchorRow + 1 + MAIN_SLIDE_WINDOW);
		for (int r = anchorRow + 1; r < limit; r++) {
			String label = rows.cellAt(grid.get(r), col);
			if (MAIN_SLIDE_ANCHOR.matcher(label).matches()) {
				return;
			}
			String suffix = MAIN_SLIDE_SUFFIXES.get(label);
			if (suffix != null) {
				emit(out, "{{tactic " + tactic + suffix + "}}", rows.cellAt(grid.get(r), col + 1));
			}
		}
	}

	/**
	 * Reads the per-tactic metric tables — the workbook blocks that face the deck's channel slide. Every
	 * {@link #METRIC_ANCHOR_HEADER} cell anchors one block; the block belongs to the tactic whose
	 * {@code "Main slide N"} anchor sits closest above it in the same column, so the tables inherit the
	 * explicit numbering of the detail blocks they follow rather than being counted by position.
	 *
	 * @param grid the workbook grid
	 * @param out  the accumulating placeholder map
	 */
	void readMetricTables(List<List<String>> grid, Map<String, String> out) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				if (!METRIC_ANCHOR_HEADER.equalsIgnoreCase(rows.cellAt(row, c))) {
					continue;
				}
				int tactic = findMetricTacticNumber(grid, r, c);
				if (tactic >= 1 && tactic <= MAX_TACTICS) {
					readMetricTable(grid, r, c, tactic, out);
				}
			}
		}
	}

	/**
	 * Finds the tactic number of a metric table by scanning up its anchor column for the nearest
	 * {@code "Main slide N"} cell within {@link #METRIC_TACTIC_WINDOW} rows.
	 *
	 * @param grid      the workbook grid
	 * @param headerRow row of the block's {@code METRIC} header cell
	 * @param col       the block's anchor column
	 * @return the 1-based tactic number, or {@code -1} when no detail block sits above this table
	 */
	int findMetricTacticNumber(List<List<String>> grid, int headerRow, int col) {
		int limit = Math.max(0, headerRow - METRIC_TACTIC_WINDOW);
		for (int r = headerRow - 1; r >= limit; r--) {
			Matcher m = MAIN_SLIDE_ANCHOR.matcher(rows.cellAt(grid.get(r), col));
			if (m.matches()) {
				return Integer.parseInt(m.group(1));
			}
		}
		return -1;
	}

	/**
	 * Reads one metric table: maps its header row to the five value-column positions, then emits every
	 * {@link #METRIC_ROW_SUFFIXES} row's cells under the matching per-tactic tokens.
	 *
	 * <p>Values already in the map win, so a figure the summary table above has already supplied is never
	 * overwritten here: the two blocks are filled from the same resolver, and the summary table is the one
	 * the rest of the deck reads.
	 *
	 * @param grid      the workbook grid
	 * @param headerRow row of the block's {@code METRIC} header cell
	 * @param col       the block's anchor column
	 * @param tactic    1-based tactic number for this block
	 * @param out       the accumulating placeholder map
	 */
	void readMetricTable(List<List<String>> grid, int headerRow, int col, int tactic, Map<String, String> out) {
		int[] valueCols = mapMetricColumns(grid.get(headerRow), col);
		int limit = Math.min(grid.size(), headerRow + 1 + METRIC_TABLE_WINDOW);
		for (int r = headerRow + 1; r < limit; r++) {
			String label = rows.cellAt(grid.get(r), col);
			if (METRIC_ANCHOR_HEADER.equalsIgnoreCase(label) || MAIN_SLIDE_ANCHOR.matcher(label).matches()) {
				return;
			}
			List<String> suffixes = METRIC_ROW_SUFFIXES.get(label.toLowerCase(Locale.ROOT));
			if (suffixes == null) {
				continue;
			}
			for (int k = 0; k < METRIC_COL_COUNT; k++) {
				if (valueCols[k] >= 0) {
					emitIfAbsent(out, "{{tactic " + tactic + suffixes.get(k) + "}}",
							rows.cellAt(grid.get(r), valueCols[k]));
				}
			}
		}
	}

	/**
	 * Maps a metric table's header row to the grid column of each of its five value columns, by the text of
	 * the header itself rather than its offset — the reporting month's number is part of two of these
	 * headers, and users insert columns.
	 *
	 * @param header the block's header row
	 * @param col    the block's anchor column, holding the {@code METRIC} label
	 * @return column index per {@link #METRIC_COL_MONTH_GOAL} … {@link #METRIC_COL_EOC_PROJ} position,
	 *         {@code -1} where the block carries no such column
	 */
	int[] mapMetricColumns(List<String> header, int col) {
		int[] cols = new int[METRIC_COL_COUNT];
		Arrays.fill(cols, -1);
		int limit = col + METRIC_COLUMN_WINDOW;
		for (int c = col + 1; c <= limit; c++) {
			String text = rows.cellAt(header, c);
			if (text.isEmpty() || METRIC_ANCHOR_HEADER.equalsIgnoreCase(text)) {
				return cols;
			}
			int position = classifyMetricColumn(text);
			if (position >= 0 && cols[position] < 0) {
				cols[position] = c;
			}
		}
		return cols;
	}

	/**
	 * Classifies one metric-table header cell into a column position. Matched loosely and most specific
	 * first, because two of the five headers carry the reporting month's number ({@code "MONTH 2 GOAL"}) and
	 * three of them contain the word "goal".
	 *
	 * @param header the header cell text
	 * @return the {@link #METRIC_COL_MONTH_GOAL} … {@link #METRIC_COL_EOC_PROJ} position, or {@code -1}
	 *         when the header is none of them
	 */
	int classifyMetricColumn(String header) {
		String text = header.toLowerCase(Locale.ROOT);
		if (text.contains("proj")) {
			return METRIC_COL_EOC_PROJ;
		}
		if (text.contains("eoc")) {
			return METRIC_COL_EOC_GOAL;
		}
		if (text.contains("actual")) {
			return METRIC_COL_MONTH_ACTUAL;
		}
		if (text.contains("vs goal")) {
			return METRIC_COL_VS_GOAL;
		}
		if (text.contains("goal")) {
			return METRIC_COL_MONTH_GOAL;
		}
		return -1;
	}

	/**
	 * Builds a case-insensitive {@code header text → column index} map from a header row, keeping the
	 * first occurrence of any repeated header.
	 *
	 * @param header the header row
	 * @return lowercased header text to its zero-based column index
	 */
	Map<String, Integer> mapHeaderColumns(List<String> header) {
		Map<String, Integer> cols = new TreeMap<>();
		if (header == null) {
			return cols;
		}
		for (int c = 0; c < header.size(); c++) {
			String key = rows.cellAt(header, c).toLowerCase(Locale.ROOT);
			if (!key.isEmpty()) {
				cols.putIfAbsent(key, c);
			}
		}
		return cols;
	}

	/**
	 * Finds the first row containing a cell equal (case-insensitively) to the given text.
	 *
	 * @param grid the workbook grid
	 * @param text the anchor text to find
	 * @return the zero-based row index, or {@code -1} when absent
	 */
	int findRowContaining(List<List<String>> grid, String text) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row == null) {
				continue;
			}
			for (int c = 0; c < row.size(); c++) {
				if (text.equalsIgnoreCase(rows.cellAt(row, c))) {
					return r;
				}
			}
		}
		return -1;
	}

	/**
	 * Tells whether a tactic-name cell holds no real tactic — it is {@code null}, empty, whitespace,
	 * or only dash characters. Unused summary rows in the EOC template carry a dash, and must not be
	 * counted as tactics.
	 *
	 * @param name the raw tactic-name cell value (may be {@code null})
	 * @return {@code true} when the cell marks an unused tactic slot
	 */
	boolean isBlankOrDash(String name) {
		return name == null || BLANK_OR_DASH.matcher(name.trim()).matches();
	}

	/**
	 * Puts a placeholder value into the map only when the token has no value yet, so a block read later
	 * cannot overwrite a figure an earlier, more authoritative block already supplied.
	 *
	 * @param out   the accumulating placeholder map
	 * @param token the full placeholder token key
	 * @param value the cell value (may be {@code null})
	 */
	void emitIfAbsent(Map<String, String> out, String token, String value) {
		if (!out.containsKey(token)) {
			emit(out, token, value);
		}
	}

	/**
	 * Puts a placeholder value into the map, skipping absent values and unreplaced template tokens
	 * (a leftover {@code {{…}}} means the sheet step never filled that cell, so it is not a real value).
	 *
	 * @param out   the accumulating placeholder map
	 * @param token the full placeholder token key
	 * @param value the cell value (may be {@code null})
	 */
	void emit(Map<String, String> out, String token, String value) {
		if (value == null) {
			return;
		}
		String trimmed = value.trim();
		if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
			return;
		}
		out.put(token, trimmed);
	}
}
