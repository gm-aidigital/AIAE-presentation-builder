package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
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
}
