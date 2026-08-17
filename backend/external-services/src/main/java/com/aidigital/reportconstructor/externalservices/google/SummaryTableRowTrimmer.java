package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides which rows of an "Our results" summary table are surplus for a deck with fewer tactics than the
 * template draws, and builds the {@code deleteTableRow} requests that remove exactly those rows.
 *
 * <p>The rows are located by reading the live table rather than by assuming fixed indices. The old
 * index-based rule ("row 0 is the header, tactic rows are 1..7") silently produced an off-by-one on a
 * template whose header sits in its own shape above the table: every delete landed one row too low, which
 * left a raw {@code {{tactic N}}} row in the deck and deleted the Totals row instead. Reading the table
 * makes the trim independent of how many header rows the template puts inside it.
 *
 * <p>The two structural facts the trim does rely on are template invariants, the same in all four group
 * tables: the Totals row, when present, is the table's last row, and the {@code tacticsPerGroup} tactic
 * rows sit directly above it. Anything above them (header rows, spacer rows) is untouched. When the table
 * does not fit that shape the trim is skipped with a warning — an untrimmed table is a visible, recoverable
 * defect, a mis-trimmed one destroys real data.
 */
@Slf4j
@Component
public class SummaryTableRowTrimmer {

	/**
	 * Matches a Totals row: the literal "Total"/"Totals" label the template prints, or a
	 * {@code {{total imps}}} / {@code {{total_investment}}} token carried when the totals went unfilled.
	 * Matched anywhere in the lower-cased row text, so a template that puts the label in a merged or later
	 * cell is still recognized. Only ever applied to a table's last row.
	 */
	private static final Pattern TOTALS_ROW = Pattern.compile("(^|[\\s{_])totals?([\\s}_]|$)");

	/**
	 * Matches a raw, unfilled per-tactic token ({@code {{tactic 3}}}, {@code {{so what 3}}}) left in a row
	 * the deck fill did not reach. Used only to cross-check the structural result, never to drive it.
	 */
	private static final Pattern RAW_TACTIC_TOKEN =
			Pattern.compile("\\{\\{\\s*(tactic|so what)\\s+\\d+", Pattern.CASE_INSENSITIVE);

	/** Separator appended between cells when a row is flattened into one string. */
	private static final String CELL_SEPARATOR = " ";

	/**
	 * Builds the requests that delete the unused tactic rows of one summary table.
	 *
	 * @param slides          the deck's slides, from {@code presentations.get} with the table-cell text mask
	 * @param tableObjectId   the summary table to trim
	 * @param tacticsPerGroup how many tactic rows the template draws in the table
	 * @param usedRows        how many of those rows carry a real tactic (1..{@code tacticsPerGroup})
	 * @return the delete requests, bottom-up so earlier indices do not shift; empty when nothing is to be
	 *         deleted or the table cannot be trimmed safely
	 */
	public List<Request> deleteRowRequests(
			List<Page> slides, String tableObjectId, int tacticsPerGroup, int usedRows) {
		List<Request> requests = new ArrayList<>();
		if (tableObjectId == null || tableObjectId.isBlank() || usedRows >= tacticsPerGroup) {
			return requests;
		}
		Table table = findTable(slides, tableObjectId);
		if (table == null) {
			log.warn("[slides] trimTactics: summary table {} not found in the deck - skipping its row trim",
					tableObjectId);
			return requests;
		}
		List<String> rows = rowTexts(table);
		int lastTacticRow = rows.size() - 1 - (hasTotalsRow(rows) ? 1 : 0);
		int firstTacticRow = lastTacticRow - tacticsPerGroup + 1;
		if (firstTacticRow < 0) {
			log.warn("[slides] trimTactics: summary table {} has {} row(s), too few for {} tactic rows "
					+ "(+ totals) - skipping its row trim", tableObjectId, rows.size(), tacticsPerGroup);
			return requests;
		}
		int firstSurplusRow = firstTacticRow + usedRows;
		warnOnUnexpectedTokens(tableObjectId, rows, firstSurplusRow, lastTacticRow);
		for (int row = lastTacticRow; row >= firstSurplusRow; row--) {
			requests.add(new Request().setDeleteTableRow(new DeleteTableRowRequest()
					.setTableObjectId(tableObjectId)
					.setCellLocation(new TableCellLocation().setRowIndex(row).setColumnIndex(0))));
		}
		log.info("[slides] trimTactics: table {} ({} rows) keeps tactic rows {}..{} and deletes rows {}..{}",
				tableObjectId, rows.size(), firstTacticRow, firstSurplusRow - 1, firstSurplusRow, lastTacticRow);
		return requests;
	}

	/**
	 * Finds the table with the given object id anywhere in the deck.
	 *
	 * @param slides        the deck's slides (may be {@code null})
	 * @param tableObjectId the table's page-element object id
	 * @return the table, or {@code null} when the deck carries no such table
	 */
	Table findTable(List<Page> slides, String tableObjectId) {
		if (slides == null) {
			return null;
		}
		for (Page page : slides) {
			if (page.getPageElements() == null) {
				continue;
			}
			for (PageElement element : page.getPageElements()) {
				if (element.getTable() != null && tableObjectId.equals(element.getObjectId())) {
					return element.getTable();
				}
			}
		}
		return null;
	}

	/**
	 * Flattens each table row into one lower-cased string, cell by cell, so a row can be classified by its
	 * text regardless of how Slides split it into runs.
	 *
	 * @param table the table to read
	 * @return one entry per row, in row order
	 */
	List<String> rowTexts(Table table) {
		List<String> texts = new ArrayList<>();
		if (table.getTableRows() == null) {
			return texts;
		}
		for (TableRow row : table.getTableRows()) {
			StringBuilder joined = new StringBuilder();
			if (row.getTableCells() != null) {
				for (TableCell cell : row.getTableCells()) {
					appendText(cell.getText(), joined);
					joined.append(CELL_SEPARATOR);
				}
			}
			texts.add(joined.toString().toLowerCase(Locale.ROOT));
		}
		return texts;
	}

	/**
	 * Appends one table cell's text runs to the accumulator.
	 *
	 * @param text   the cell's text container (may be {@code null})
	 * @param joined the accumulating row text
	 */
	void appendText(TextContent text, StringBuilder joined) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
	}

	/**
	 * Whether the table's last row is the Totals row, which the trim must keep.
	 *
	 * @param rows the flattened row texts, in row order
	 * @return {@code true} when the last row is a Totals row
	 */
	boolean hasTotalsRow(List<String> rows) {
		return !rows.isEmpty() && TOTALS_ROW.matcher(rows.get(rows.size() - 1)).find();
	}

	/**
	 * Logs a warning when the rows still carrying raw per-tactic tokens are not exactly the rows about to be
	 * deleted. The structural result stands either way - this only makes a template that no longer matches
	 * the assumed shape visible in the logs instead of silently shipping a wrong table.
	 *
	 * @param tableObjectId   the table being trimmed, for the log line
	 * @param rows            the flattened row texts, in row order
	 * @param firstSurplusRow index of the first row to delete
	 * @param lastTacticRow   index of the last row to delete
	 */
	void warnOnUnexpectedTokens(String tableObjectId, List<String> rows, int firstSurplusRow, int lastTacticRow) {
		for (int row = 0; row < rows.size(); row++) {
			boolean raw = RAW_TACTIC_TOKEN.matcher(rows.get(row)).find();
			boolean deleted = row >= firstSurplusRow && row <= lastTacticRow;
			if (raw && !deleted) {
				log.warn("[slides] trimTactics: table {} row {} still carries an unfilled tactic token but is "
						+ "not deleted - the summary table may no longer match the expected shape",
						tableObjectId, row);
			}
		}
	}
}
