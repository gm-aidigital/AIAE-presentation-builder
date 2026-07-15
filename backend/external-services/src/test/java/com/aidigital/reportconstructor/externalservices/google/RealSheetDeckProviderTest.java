package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RealSheetDeckProviderTest {

	private RealSheetDeckProvider newProvider() {
		GoogleCredentialsFactory creds = Mockito.mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(new NetHttpTransport());
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> {
		});
		GoogleProperties props = Mockito.mock(GoogleProperties.class);
		when(props.getSheetsTemplateId()).thenReturn("template");
		when(props.getSheetsTargetFolderId()).thenReturn("");
		SheetPacingTableWriter writer = Mockito.mock(SheetPacingTableWriter.class);
		GoogleRequestRetrier retrier = Mockito.mock(GoogleRequestRetrier.class);
		return new RealSheetDeckProvider(creds, props, writer, retrier);
	}

	@Test
	void summaryRowDashRequests_dashesUnusedSlotsAndLeavesTotalsRowInPlaceTest() {
		// Given: a summary table whose header sits at row index 2, so its 28 tactic slots occupy rows 3..30,
		// and only the first three tactics are active
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("intro"));
		grid.add(List.of("more"));
		grid.add(List.of("Tactic name", "Benchmark", "KPI type", "Imps Plan"));
		int sheetId = 7;
		int tacticCount = 3;

		// When:
		List<Request> requests = provider.summaryRowDashRequests(grid, sheetId, tacticCount);

		// Then: one dash-fill per unused slot (28 - 3 = 25), the first covering row index 2+4 across the
		// table's four columns, writing an em-dash without touching formatting — and no totals row is moved
		assertThat(requests).hasSize(25);
		Request first = requests.get(0);
		assertThat(first.getRepeatCell()).isNotNull();
		assertThat(first.getRepeatCell().getFields()).isEqualTo("userEnteredValue");
		assertThat(first.getRepeatCell().getRange().getStartRowIndex()).isEqualTo(6);
		assertThat(first.getRepeatCell().getRange().getEndRowIndex()).isEqualTo(7);
		assertThat(first.getRepeatCell().getRange().getStartColumnIndex()).isEqualTo(0);
		assertThat(first.getRepeatCell().getRange().getEndColumnIndex()).isEqualTo(4);
		assertThat(first.getRepeatCell().getCell().getUserEnteredValue().getStringValue()).isEqualTo("—");
		assertThat(requests).noneMatch(r -> r.getCopyPaste() != null);
	}

	@Test
	void summaryRowDashRequests_noRequestsWhenHeaderMissingTest() {
		// Given: a grid with no summary-table header row
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = List.of(List.of("nothing", "here"));

		// When:
		List<Request> requests = provider.summaryRowDashRequests(grid, 1, 3);

		// Then: the dash-fill is skipped rather than guessing a location
		assertThat(requests).isEmpty();
	}

	@Test
	void breakdownClearRequests_clearsOnlyUnselectedSectionsPerTacticTest() {
		// Given: a "Breakdowns" tab with two 18-row tactic blocks (headers at rows 0 and 18), each carrying
		// the five section anchors at their template columns (A/E/J/M/R) and 22 columns wide
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		for (int block = 0; block < 2; block++) {
			for (int row = 0; row < 18; row++) {
				List<String> cells = new ArrayList<>(Collections.nCopies(22, ""));
				if (row == 0) {
					int tactic = block + 1;
					cells.set(0, "Top Publishers " + tactic);
					cells.set(4, "Creative analysis " + tactic);
					cells.set(9, "Geo analysis " + tactic);
					cells.set(12, "Audience analysis " + tactic);
					cells.set(17, "Device breakdown " + tactic);
					cells.set(21, "filler"); // establishes the block's 22-column right edge
				}
				grid.add(cells);
			}
		}
		int sheetId = 5;
		// Tactic 1 keeps Top Publishers + Geo; tactic 2 enables nothing (all five cleared).
		Map<Integer, Set<BreakdownType>> enabledByTactic = Map.of(
				1, EnumSet.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO),
				2, EnumSet.noneOf(BreakdownType.class));

		// When:
		List<Request> requests = provider.breakdownClearRequests(grid, sheetId, enabledByTactic);

		// Then: three sections cleared for tactic 1 (Creative, Audience, Device) + all five for tactic 2 = 8
		assertThat(requests).hasSize(8);
		assertThat(requests).allSatisfy(r -> {
			assertThat(r.getRepeatCell()).isNotNull();
			assertThat(r.getRepeatCell().getFields()).isEqualTo("*");
			assertThat(r.getRepeatCell().getRange().getSheetId()).isEqualTo(sheetId);
		});

		// And: tactic 1's kept sections (Top Publishers cols 0..4, Geo cols 9..12) produce no clear request,
		// while its cleared sections span the correct column ranges over the block's 18 rows (0..18)
		assertThat(ranges(requests, 0, 0)).isEmpty(); // Top Publishers kept (block 1, start col 0)
		assertThat(ranges(requests, 0, 9)).isEmpty(); // Geo kept (block 1, start col 9)
		assertThat(ranges(requests, 0, 4)).singleElement().satisfies(range -> { // Creative E..I
			assertThat(range.getStartColumnIndex()).isEqualTo(4);
			assertThat(range.getEndColumnIndex()).isEqualTo(9);
			assertThat(range.getStartRowIndex()).isEqualTo(0);
			assertThat(range.getEndRowIndex()).isEqualTo(18);
		});
		assertThat(ranges(requests, 0, 12)).singleElement().satisfies(range -> // Audience M..Q
				assertThat(range.getEndColumnIndex()).isEqualTo(17));
		assertThat(ranges(requests, 0, 17)).singleElement().satisfies(range -> // Device R..V, to the right edge
				assertThat(range.getEndColumnIndex()).isEqualTo(22));

		// And: tactic 2's block (rows 18..36) has all five sections cleared
		assertThat(requests.stream().filter(r -> r.getRepeatCell().getRange().getStartRowIndex() == 18)).hasSize(5);
	}

	@Test
	void breakdownClearRequests_emptyWhenNoAnchorsTest() {
		// Given: a grid carrying no breakdown section anchors
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = List.of(List.of("Publisher", "Impressions"), List.of("1", "2"));

		// When:
		List<Request> requests = provider.breakdownClearRequests(grid, 3, Map.of());

		// Then: nothing to clear
		assertThat(requests).isEmpty();
	}

	/**
	 * Filters clear requests to those starting at the given start row and start column.
	 *
	 * @param requests the clear requests to filter
	 * @param startRow the block's start row
	 * @param startCol the section's start column
	 * @return the matching grid ranges
	 */
	private List<GridRange> ranges(List<Request> requests, int startRow, int startCol) {
		return requests.stream()
				.map(r -> r.getRepeatCell().getRange())
				.filter(range -> range.getStartRowIndex() == startRow && range.getStartColumnIndex() == startCol)
				.toList();
	}

	@Test
	void publisherTables_readsRowsByHeaderNameForTheRequestedTacticTest() {
		// Given: a "Breakdowns" tab whose tactic-1 block carries a header row and two filled publisher rows
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = publisherGrid(Map.of(
				1, List.of(List.of("YouTube", "1,200,000", "26%"), List.of("Hulu", "800,000", "17%"))));

		// When:
		Map<Integer, List<PublisherRow>> tables = provider.publisherTables(grid, Set.of(1));

		// Then: both rows come back verbatim, in sheet order
		assertThat(tables.get(1)).containsExactly(
				new PublisherRow("YouTube", "1,200,000", "26%"),
				new PublisherRow("Hulu", "800,000", "17%"));
	}

	@Test
	void publisherTables_doesNotMatchTactic15BlockWhenReadingTactic1Test() {
		// Given: a tab carrying both a "Top Publishers 1" and a "Top Publishers 15" block — the anchor of the
		// first is a prefix of the second, so a loose match would pull tactic 15's rows into tactic 1
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = publisherGrid(Map.of(
				1, List.of(List.of("YouTube", "1,200,000", "26%")),
				15, List.of(List.of("Roku", "500,000", "11%"))));

		// When:
		Map<Integer, List<PublisherRow>> tables = provider.publisherTables(grid, Set.of(1));

		// Then: only tactic 1's own row is returned and tactic 15's block is untouched
		assertThat(tables.get(1)).containsExactly(new PublisherRow("YouTube", "1,200,000", "26%"));
		assertThat(tables).doesNotContainKey(15);
	}

	@Test
	void publisherTables_returnsEmptyListForATacticWhoseTableWasNeverFilledTest() {
		// Given: tactic 1's block has its header but no rows under it
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = publisherGrid(Map.of(1, List.of()));

		// When:
		Map<Integer, List<PublisherRow>> tables = provider.publisherTables(grid, Set.of(1));

		// Then: the tactic is present with no rows rather than absent, so the caller can still ship the slide
		assertThat(tables).containsKey(1);
		assertThat(tables.get(1)).isEmpty();
	}

	@Test
	void publisherTables_doesNotReadTheBlocksTotalRowAsAPublisherTest() {
		// Given: the template's real layout — anchor, header, 15 numbered rows, then a "Total" row carrying
		// {{tactic 1 imps}}. The Total row falls inside the block's 18-row window, so only its blank
		// publisher cell keeps it out of the results.
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(new ArrayList<>(List.of("Top Publishers 1", "{{tactic 1}}", "", "")));
		grid.add(new ArrayList<>(List.of("#", "Publisher", "Impressions", "Share of voice")));
		grid.add(new ArrayList<>(List.of("1", "YouTube", "1,200,000", "26%")));
		for (int i = 2; i <= 15; i++) {
			grid.add(new ArrayList<>(List.of(String.valueOf(i), "", "", "")));
		}
		grid.add(new ArrayList<>(List.of("Total", "", "{{tactic 1 imps}}", "")));
		grid.add(new ArrayList<>(List.of("Top Publishers 2", "{{tactic 2}}", "", "")));

		// When:
		Map<Integer, List<PublisherRow>> tables = provider.publisherTables(grid, Set.of(1));

		// Then: only the one filled publisher comes back — the Total row is not mistaken for a 16th
		assertThat(tables.get(1)).containsExactly(new PublisherRow("YouTube", "1,200,000", "26%"));
	}

	@Test
	void findPublisherHeader_resolvesColumnsByHeaderTextRatherThanFixedOffsetsTest() {
		// Given: a block whose columns sit in a different order than the template's default
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Top Publishers 1", "", "", ""));
		grid.add(List.of("#", "Share of voice", "Publisher", "Impressions"));

		// When:
		int[] header = provider.findPublisherHeader(grid, 0, 2, 0, 4);

		// Then: each column is found where its own header is, not where the template usually puts it
		assertThat(header).containsExactly(1, 2, 3, 1);
	}

	/**
	 * Builds a "Breakdowns" tab carrying an 18-row Top Publishers block per given tactic: the anchor row,
	 * a header row, then the tactic's filled rows.
	 *
	 * @param rowsByTactic tactic number → its publisher rows as {@code [name, impressions, sov]}
	 * @return the tab as trimmed cell strings
	 */
	private List<List<String>> publisherGrid(Map<Integer, List<List<String>>> rowsByTactic) {
		List<List<String>> grid = new ArrayList<>();
		for (Map.Entry<Integer, List<List<String>>> entry : new java.util.TreeMap<>(rowsByTactic).entrySet()) {
			List<List<String>> block = new ArrayList<>();
			block.add(new ArrayList<>(List.of("Top Publishers " + entry.getKey(), "", "", "")));
			block.add(new ArrayList<>(List.of("#", "Publisher", "Impressions", "Share of voice")));
			for (List<String> row : entry.getValue()) {
				block.add(new ArrayList<>(List.of("", row.get(0), row.get(1), row.get(2))));
			}
			while (block.size() < 18) {
				block.add(new ArrayList<>(Collections.nCopies(4, "")));
			}
			grid.addAll(block);
		}
		return grid;
	}
}
