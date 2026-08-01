package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
			Map.entry("Segments:", "{{audience_segments}}"));

	/** Info-block header whose value sits in the cell <em>below</em> it. */
	private static final String RFP_HEADER = "RFP Input";
	private static final String RFP_TOKEN = "{{RFP info}}";

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
	 * EOM "Unit rate" column is filled from the {@code {{unit N rate}}} template token, so it is read
	 * back under the same name.
	 */
	private static final Map<String, String> SUMMARY_TACTIC_TOKEN_FORMATS = Map.of(
			"Unit rate", "{{unit %d rate}}");

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
		return out;
	}

	/**
	 * Reads the top info block: each {@link #INFO_LABEL_TOKENS} label's right-hand value and
	 * the {@link #RFP_TOKEN} value beneath the {@link #RFP_HEADER} header.
	 *
	 * @param grid the workbook grid
	 * @param out  the accumulating placeholder map
	 */
	void readInfoBlock(List<List<String>> grid, Map<String, String> out) {
		for (Map.Entry<String, String> e : INFO_LABEL_TOKENS.entrySet()) {
			emit(out, e.getValue(), rows.findLabelValue(grid, e.getKey()));
		}
		emit(out, RFP_TOKEN, rows.findLabelValueBelow(grid, RFP_HEADER));
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
	 * Puts a placeholder value into the map, skipping absent values and unreplaced template tokens
	 * (a leftover {@code {{…}}} means the sheet step never filled that cell, so it is not a real value).
	 *
	 * @param out   the accumulating placeholder map
	 * @param token the full placeholder token key
	 * @param value the cell value (may be {@code null})
	 */
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
