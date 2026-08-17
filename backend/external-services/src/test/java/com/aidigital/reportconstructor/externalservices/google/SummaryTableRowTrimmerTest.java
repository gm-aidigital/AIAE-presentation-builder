package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryTableRowTrimmerTest {

	private static final int TACTICS_PER_GROUP = 7;

	/**
	 * Builds a one-slide deck carrying a single table under the given object id, one string per row
	 * (each row's text goes into its first cell — enough for every rule the trimmer applies).
	 */
	private List<Page> deckWithTable(String tableObjectId, List<String> rowTexts) {
		List<TableRow> rows = new ArrayList<>();
		for (String text : rowTexts) {
			TextElement run = new TextElement().setTextRun(new TextRun().setContent(text));
			TableCell cell = new TableCell().setText(new TextContent().setTextElements(List.of(run)));
			rows.add(new TableRow().setTableCells(List.of(cell)));
		}
		PageElement element = new PageElement()
				.setObjectId(tableObjectId)
				.setTable(new Table().setTableRows(rows));
		return List.of(new Page().setObjectId("slide1").setPageElements(List.of(element)));
	}

	/** The 0-based row indices a request list deletes, in request order. */
	private List<Integer> deletedRows(List<Request> requests) {
		List<Integer> rows = new ArrayList<>();
		for (Request request : requests) {
			rows.add(request.getDeleteTableRow().getCellLocation().getRowIndex());
		}
		return rows;
	}

	@Test
	void deleteRowRequests_shouldKeepTheTotalsRowWhenTheHeaderLivesOutsideTheTableTest() {
		// Given: the new template's table — no header row inside it, 7 tactic rows, then Totals — filled
		// for 2 tactics, so tactic rows 3..7 still carry their raw tokens
		List<Page> deck = deckWithTable("tbl", List.of(
				"Display", "Video", "{{tactic 3}}", "{{tactic 4}}", "{{tactic 5}}", "{{tactic 6}}",
				"{{tactic 7}}", "Total"));

		// When: trimming to the 2 real tactics
		List<Request> requests = new SummaryTableRowTrimmer()
				.deleteRowRequests(deck, "tbl", TACTICS_PER_GROUP, 2);

		// Then: exactly the 5 unfilled tactic rows go, bottom-up; the Totals row (index 7) survives
		assertThat(deletedRows(requests)).containsExactly(6, 5, 4, 3, 2);
	}

	@Test
	void deleteRowRequests_shouldSkipTheHeaderRowWhenTheTableCarriesOneTest() {
		// Given: the legacy shape — header row inside the table, then 7 tactic rows, then Totals
		List<Page> deck = deckWithTable("tbl", List.of(
				"PLATFORMS", "Display", "Video", "{{tactic 3}}", "{{tactic 4}}", "{{tactic 5}}",
				"{{tactic 6}}", "{{tactic 7}}", "Total"));

		// When: trimming to the 2 real tactics
		List<Request> requests = new SummaryTableRowTrimmer()
				.deleteRowRequests(deck, "tbl", TACTICS_PER_GROUP, 2);

		// Then: the same 5 rows go, shifted by the header — the header and Totals rows are kept
		assertThat(deletedRows(requests)).containsExactly(7, 6, 5, 4, 3);
	}

	@Test
	void deleteRowRequests_shouldTrimAPartialSecondGroupTableTest() {
		// Given: 10 tactics — group 2's table carries tactics 8, 9, 10 and 4 unfilled rows
		List<Page> deck = deckWithTable("tbl2", List.of(
				"CTV", "Audio", "Native", "{{tactic 11}}", "{{tactic 12}}", "{{tactic 13}}",
				"{{tactic 14}}", "Total"));

		// When: trimming the last group down to its 3 real rows
		List<Request> requests = new SummaryTableRowTrimmer()
				.deleteRowRequests(deck, "tbl2", TACTICS_PER_GROUP, 3);

		// Then: only the 4 unfilled rows go
		assertThat(deletedRows(requests)).containsExactly(6, 5, 4, 3);
	}

	@Test
	void deleteRowRequests_shouldHandleATableWithoutATotalsRowTest() {
		// Given: a table whose last row is a tactic row (no Totals row drawn)
		List<Page> deck = deckWithTable("tbl", List.of(
				"PLATFORMS", "Display", "{{tactic 2}}", "{{tactic 3}}", "{{tactic 4}}", "{{tactic 5}}",
				"{{tactic 6}}", "{{tactic 7}}"));

		// When: trimming to the single real tactic
		List<Request> requests = new SummaryTableRowTrimmer()
				.deleteRowRequests(deck, "tbl", TACTICS_PER_GROUP, 1);

		// Then: every unfilled row goes, down to and including the table's last row
		assertThat(deletedRows(requests)).containsExactly(7, 6, 5, 4, 3, 2);
	}

	@Test
	void deleteRowRequests_shouldEmitNothingWhenTheGroupIsFullTest() {
		// Given: a full group of 7 tactics
		List<Page> deck = deckWithTable("tbl", List.of(
				"PLATFORMS", "1", "2", "3", "4", "5", "6", "7", "Total"));

		// When-Then: a full table has no surplus rows, so no read-back result is acted on
		assertThat(new SummaryTableRowTrimmer().deleteRowRequests(deck, "tbl", TACTICS_PER_GROUP, 7))
				.isEmpty();
	}

	@Test
	void deleteRowRequests_shouldSkipTheTrimWhenTheTableIsMissingOrTooShortTest() {
		// Given: a deck whose table is not the configured one, and a table too short to hold 7 tactic rows
		List<Page> otherTable = deckWithTable("other", List.of("Display", "Video", "Total"));
		List<Page> shortTable = deckWithTable("tbl", List.of("PLATFORMS", "Display", "Video", "Total"));
		SummaryTableRowTrimmer trimmer = new SummaryTableRowTrimmer();

		// When-Then: both degrade to a no-op — an untrimmed table beats a mis-trimmed one
		assertThat(trimmer.deleteRowRequests(otherTable, "tbl", TACTICS_PER_GROUP, 2)).isEmpty();
		assertThat(trimmer.deleteRowRequests(shortTable, "tbl", TACTICS_PER_GROUP, 2)).isEmpty();
	}

	@Test
	void deleteRowRequests_shouldRecognizeAnUnfilledTotalsRowTest() {
		// Given: a table whose Totals row still carries its raw tokens (the totals fill did not run)
		List<Page> deck = deckWithTable("tbl", List.of(
				"Display", "Video", "{{tactic 3}}", "{{tactic 4}}", "{{tactic 5}}", "{{tactic 6}}",
				"{{tactic 7}}", "{{total imps}}"));

		// When: trimming to the 2 real tactics
		List<Request> requests = new SummaryTableRowTrimmer()
				.deleteRowRequests(deck, "tbl", TACTICS_PER_GROUP, 2);

		// Then: the totals row is still recognized and kept, so only tactic rows go
		assertThat(deletedRows(requests)).containsExactly(6, 5, 4, 3, 2);
	}
}
