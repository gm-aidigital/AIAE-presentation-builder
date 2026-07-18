package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
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

	@Test
	void creativeTables_readsStatsAndRowsForTheRequestedTacticTest() {
		// Given: a "Breakdowns" tab whose tactic-1 creative block carries all four stat tiles and two rows
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = creativeGrid(Map.of(
				1, List.of(
						List.of("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"),
						List.of("Cutdown 6s", "600,000", "0.31%", "71.2%", "$2,100"))));

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(1));

		// Then: the stat tiles come back as typed
		CreativeTable table = tables.get(1);
		assertThat(table.creativesLive()).isEqualTo("12");
		assertThat(table.bestKpi()).isEqualTo("0.58");
		assertThat(table.avgKpi()).isEqualTo("0.42");
		assertThat(table.topCreative()).isEqualTo("Hero 15s");

		// Then: both rows come back verbatim, in sheet order
		assertThat(table.rows()).containsExactly(
				new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"),
				new CreativeRow("Cutdown 6s", "600,000", "0.31%", "71.2%", "$2,100"));
	}

	@Test
	void creativeTables_doesNotMatchTactic15BlockWhenReadingTactic1Test() {
		// Given: a tab carrying both a "Creative analysis 1" and a "Creative analysis 15" block — the anchor
		// of the first is a prefix of the second, so a loose match would pull tactic 15's rows into tactic 1
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = creativeGrid(Map.of(
				1, List.of(List.of("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800")),
				15, List.of(List.of("Banner 300x250", "500,000", "0.11%", "—", "$900"))));

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(1));

		// Then: only tactic 1's own row is returned and tactic 15's block is untouched
		assertThat(tables.get(1).rows())
				.containsExactly(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"));
		assertThat(tables).doesNotContainKey(15);
	}

	@Test
	void creativeTables_returnsEmptyTableForATacticWhoseBlockWasNeverFilledTest() {
		// Given: a tab carrying no creative anchor for the requested tactic
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = creativeGrid(Map.of(1, List.of()));

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(2));

		// Then: the tactic is present with an empty table rather than absent, so the slide still ships
		assertThat(tables).containsKey(2);
		assertThat(tables.get(2).isEmpty()).isTrue();
	}

	@Test
	void findCreativeHeader_resolvesColumnsByHeaderTextRatherThanFixedOffsetsTest() {
		// Given: a block whose metric columns sit in a different order than the template's default
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Creative analysis 1", "", "", "", ""));
		grid.add(List.of("Spend", "VCR", "Creative", "CTR", "Impressions"));

		// When:
		int[] header = provider.findCreativeHeader(grid, 0, 2, 0, 5);

		// Then: each column is found where its own header is, not where the template usually puts it
		assertThat(header).containsExactly(1, 2, 4, 3, 1, 0);
	}

	@Test
	void summaryValue_stopsAtTheBlocksRightEdgeRatherThanReadingTheNextSectionTest() {
		// Given: a block whose "CREATIVES LIVE" value cell is blank, with the neighbouring section's text
		// sitting just past the block's right edge
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(new ArrayList<>(List.of("Creative analysis 1", "", "Geo analysis 1", "")));
		grid.add(new ArrayList<>(List.of("CREATIVES LIVE", "", "MARKETS ACTIVATED", "14")));

		// When: the block is bounded to its own two columns
		String value = provider.summaryValue(grid, 0, 2, 0, 2, "CREATIVES LIVE");

		// Then: the blank reads as blank — the next section's value is never pulled in
		assertThat(value).isEmpty();
	}

	@Test
	void geoTables_readsStatsAndRowsForTheRequestedTacticTest() {
		// Given: a "Breakdowns" tab whose tactic-1 geo block carries its three stat tiles and two rows
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = geoGrid(Map.of(
				1, List.of(
						List.of("Miami", "1,200,000", "0.48%"),
						List.of("Atlanta", "900,000", "0.46%"))));

		// When:
		Map<Integer, GeoTable> tables = provider.geoTables(grid, Set.of(1));

		// Then: the stat tiles come back as typed, including the prefix-matched "MOST EFFICIENT CTR" tile
		GeoTable table = tables.get(1);
		assertThat(table.marketsActivated()).isEqualTo("42");
		assertThat(table.topGeo()).isEqualTo("Miami");
		assertThat(table.topKpi()).isEqualTo("0.48%");

		// Then: both rows come back verbatim, in sheet order
		assertThat(table.rows()).containsExactly(
				new GeoRow("Miami", "1,200,000", "0.48%"),
				new GeoRow("Atlanta", "900,000", "0.46%"));
	}

	@Test
	void geoTables_doesNotMatchTactic15BlockWhenReadingTactic1Test() {
		// Given: a tab carrying both a "Geo analysis 1" and a "Geo analysis 15" block — the anchor of the
		// first is a prefix of the second, so a loose match would pull tactic 15's rows into tactic 1
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = geoGrid(Map.of(
				1, List.of(List.of("Miami", "1,200,000", "0.48%")),
				15, List.of(List.of("Boise", "50,000", "0.11%"))));

		// When:
		Map<Integer, GeoTable> tables = provider.geoTables(grid, Set.of(1));

		// Then: only tactic 1's own row is returned and tactic 15's block is untouched
		assertThat(tables.get(1).rows()).containsExactly(new GeoRow("Miami", "1,200,000", "0.48%"));
		assertThat(tables).doesNotContainKey(15);
	}

	@Test
	void geoTables_returnsEmptyTableForATacticWhoseBlockWasNeverFilledTest() {
		// Given: a tab carrying no geo anchor for the requested tactic
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = geoGrid(Map.of(1, List.of()));

		// When:
		Map<Integer, GeoTable> tables = provider.geoTables(grid, Set.of(2));

		// Then: the tactic is present with an empty table rather than absent, so the slide still ships
		assertThat(tables).containsKey(2);
		assertThat(tables.get(2).isEmpty()).isTrue();
	}

	@Test
	void findGeoHeader_takesKpiColumnAsTheNextPopulatedHeaderAfterImpsTest() {
		// Given: a geo header row whose KPI column header is the tactic's resolved KPI type, not a fixed word
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Geo analysis 1", "", ""));
		grid.add(List.of("Geo", "IMPS", "VCR"));

		// When:
		int[] header = provider.findGeoHeader(grid, 0, 2, 0, 3);

		// Then: name and IMPS resolve by header text, and the KPI column is the next populated cell after IMPS
		assertThat(header).containsExactly(1, 0, 1, 2);
	}

	@Test
	void geoSummaryValueByPrefix_matchesTheMostEfficientLabelDespiteItsTrailingKpiTypeTest() {
		// Given: the "MOST EFFICIENT" stat tile whose label cell carries the KPI type after the fixed prefix
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(new ArrayList<>(List.of("Geo analysis 1", "")));
		grid.add(new ArrayList<>(List.of("MOST EFFICIENT CTR", "0.52%")));

		// When:
		String value = provider.geoSummaryValueByPrefix(grid, 0, 2, 0, 2, "MOST EFFICIENT");

		// Then: the value beside the prefix-matched label is returned
		assertThat(value).isEqualTo("0.52%");
	}

	@Test
	void audienceTables_readsStatTilesAndBothSubTablesForTheRequestedTacticTest() {
		// Given: a "Breakdowns" tab whose tactic-1 audience block carries its two stat tiles, its filled
		// age rows and its filled segment rows
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = audienceGrid(Map.of(
				1, new AudienceTable("25-34", "58% F / 42% M",
						List.of(new AudienceAgeRow("18-24", "800,000"), new AudienceAgeRow("25-34", "1,200,000")),
						List.of(new AudienceSegmentRow("Auto Intenders", "142"),
								new AudienceSegmentRow("Sports Fans", "128")))));

		// When:
		Map<Integer, AudienceTable> tables = provider.audienceTables(grid, Set.of(1));

		// Then: the stat tiles come back as typed
		AudienceTable table = tables.get(1);
		assertThat(table.ageDistribution()).isEqualTo("25-34");
		assertThat(table.genderDemographics()).isEqualTo("58% F / 42% M");

		// Then: both sub-tables come back verbatim, in sheet order
		assertThat(table.ageRows()).containsExactly(
				new AudienceAgeRow("18-24", "800,000"),
				new AudienceAgeRow("25-34", "1,200,000"));
		assertThat(table.segmentRows()).containsExactly(
				new AudienceSegmentRow("Auto Intenders", "142"),
				new AudienceSegmentRow("Sports Fans", "128"));
	}

	@Test
	void audienceTables_keepsAgeRowsOnlyWhereTheUserTypedImpressionsTest() {
		// Given: the template pre-fills every age bucket label, but the user typed impressions for only one
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = audienceGrid(Map.of(
				1, new AudienceTable("25-34", "",
						List.of(new AudienceAgeRow("18-24", ""), new AudienceAgeRow("25-34", "1,200,000")),
						List.of())));

		// When:
		Map<Integer, AudienceTable> tables = provider.audienceTables(grid, Set.of(1));

		// Then: only the impressions-filled bucket is kept, so a blank age table never reads as data
		assertThat(tables.get(1).ageRows()).containsExactly(new AudienceAgeRow("25-34", "1,200,000"));
	}

	@Test
	void audienceTables_doesNotMatchTactic15BlockWhenReadingTactic1Test() {
		// Given: a tab carrying both an "Audience analysis 1" and an "Audience analysis 15" block — the
		// anchor of the first is a prefix of the second, so a loose match would pull tactic 15's rows in
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = audienceGrid(Map.of(
				1, new AudienceTable("25-34", "58% F", List.of(),
						List.of(new AudienceSegmentRow("Auto Intenders", "142"))),
				15, new AudienceTable("55-64", "50% F", List.of(),
						List.of(new AudienceSegmentRow("Retirees", "90")))));

		// When:
		Map<Integer, AudienceTable> tables = provider.audienceTables(grid, Set.of(1));

		// Then: only tactic 1's own segment is returned and tactic 15's block is untouched
		assertThat(tables.get(1).segmentRows()).containsExactly(new AudienceSegmentRow("Auto Intenders", "142"));
		assertThat(tables).doesNotContainKey(15);
	}

	@Test
	void audienceTables_returnsEmptyTableForATacticWhoseBlockWasNeverFilledTest() {
		// Given: a tab carrying no audience anchor for the requested tactic
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = audienceGrid(Map.of(1, AudienceTable.EMPTY));

		// When:
		Map<Integer, AudienceTable> tables = provider.audienceTables(grid, Set.of(2));

		// Then: the tactic is present with an empty table rather than absent, so the slide still ships
		assertThat(tables).containsKey(2);
		assertThat(tables.get(2).isEmpty()).isTrue();
	}

	@Test
	void findAudienceHeaders_resolveEachSubTablesColumnsByTextOnTheSharedHeaderRowTest() {
		// Given: the audience block's shared header row — age/impressions on the left, Segment/Affinity
		// index on the right, separated by a spacer column
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Audience analysis 1", "", "", "", ""));
		grid.add(List.of("age", "impressions", "", "Segment", "Affinity index"));

		// When:
		int[] ageHeader = provider.findAudienceAgeHeader(grid, 0, 2, 0, 5);
		int[] segmentHeader = provider.findAudienceSegmentHeader(grid, 0, 2, 0, 5);

		// Then: each sub-table's two columns resolve by header text, not by fixed offset
		assertThat(ageHeader).containsExactly(1, 0, 1);
		assertThat(segmentHeader).containsExactly(1, 3, 4);
	}

	@Test
	void deviceTables_readsAllFiveStatTilesAndTheDeviceRowsForTheRequestedTacticTest() {
		// Given: a "Breakdowns" tab whose tactic-1 device block carries its five stat tiles and its filled
		// per-device rows
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = deviceGrid(Map.of(
				1, new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
						List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"),
								new DeviceRow("Desktop", "300,000", "0.90%", "85%", "$1,000")))));

		// When:
		Map<Integer, DeviceTable> tables = provider.deviceTables(grid, Set.of(1));

		// Then: every stat tile comes back as typed
		DeviceTable table = tables.get(1);
		assertThat(table.highestCtr()).isEqualTo("1.20%");
		assertThat(table.bestCompletion()).isEqualTo("82%");
		assertThat(table.devicesTracked()).isEqualTo("4");
		assertThat(table.topDevice()).isEqualTo("Mobile");
		assertThat(table.topDeviceImpressionsPct()).isEqualTo("61%");

		// Then: the table's filled rows come back verbatim, in sheet order
		assertThat(table.rows()).containsExactly(
				new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"),
				new DeviceRow("Desktop", "300,000", "0.90%", "85%", "$1,000"));
	}

	@Test
	void deviceTables_keepsRowsOnlyWhereTheUserTypedImpressionsTest() {
		// Given: the template pre-fills every device label, but the user typed impressions for only one
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = deviceGrid(Map.of(
				1, new DeviceTable("1.20%", "", "", "", "",
						List.of(new DeviceRow("Mobile", "", "", "", ""),
								new DeviceRow("Desktop", "300,000", "0.90%", "85%", "$1,000")))));

		// When:
		Map<Integer, DeviceTable> tables = provider.deviceTables(grid, Set.of(1));

		// Then: only the impressions-filled device is kept, so a blank device row never reads as data
		assertThat(tables.get(1).rows())
				.containsExactly(new DeviceRow("Desktop", "300,000", "0.90%", "85%", "$1,000"));
	}

	@Test
	void deviceTables_doesNotMatchTactic15BlockWhenReadingTactic1Test() {
		// Given: a tab carrying both a "Device breakdown 1" and a "Device breakdown 15" block — the anchor
		// of the first is a prefix of the second, so a loose match would pull tactic 15's rows in
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = deviceGrid(Map.of(
				1, new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
						List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"))),
				15, new DeviceTable("0.40%", "60%", "2", "Desktop", "70%",
						List.of(new DeviceRow("Desktop", "500,000", "0.40%", "60%", "$900")))));

		// When:
		Map<Integer, DeviceTable> tables = provider.deviceTables(grid, Set.of(1));

		// Then: only tactic 1's own row is returned and tactic 15's block is untouched
		assertThat(tables.get(1).rows())
				.containsExactly(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"));
		assertThat(tables).doesNotContainKey(15);
	}

	@Test
	void deviceTables_returnsEmptyTableForATacticWhoseBlockWasNeverFilledTest() {
		// Given: a tab carrying no device anchor for the requested tactic
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = deviceGrid(Map.of(1, DeviceTable.EMPTY));

		// When:
		Map<Integer, DeviceTable> tables = provider.deviceTables(grid, Set.of(2));

		// Then: the tactic is present with an empty table rather than absent, so the slide still ships
		assertThat(tables).containsKey(2);
		assertThat(tables.get(2).isEmpty()).isTrue();
	}

	@Test
	void deviceTables_readsTopDevicePctTileWithoutMatchingItToTheTopDeviceTileTest() {
		// Given: the two "TOP DEVICE" tiles sit on adjacent rows — the whole-cell match must not let the
		// shorter "TOP DEVICE" label read the "% OF IMPRESSIONS" tile's value or vice versa
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = deviceGrid(Map.of(
				1, new DeviceTable("1.20%", "82%", "4", "Mobile", "61%", List.of())));

		// When:
		DeviceTable table = provider.deviceTables(grid, Set.of(1)).get(1);

		// Then: each tile keeps its own value
		assertThat(table.topDevice()).isEqualTo("Mobile");
		assertThat(table.topDeviceImpressionsPct()).isEqualTo("61%");
	}

	@Test
	void findDeviceHeader_resolvesTheFiveColumnsByTextOnTheHeaderRowTest() {
		// Given: the device table's header row
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Device breakdown 1", "", "", "", ""));
		grid.add(List.of("Device", "Impressions", "CTR", "VCR", "Spend"));

		// When:
		int[] header = provider.findDeviceHeader(grid, 0, 2, 0, 5);

		// Then: the five columns resolve by header text, not by fixed offset
		assertThat(header).containsExactly(1, 0, 1, 2, 3, 4);
	}

	@Test
	void creativeTables_findsBlocksByAnchorWhereverTheySitNotByTacticOrderTest() {
		// Given: the real shape after Step-3 toggles — tactic 1 did NOT enable Creative analysis, so its
		// section was cleared and the first surviving anchor is "Creative analysis 2", 18 rows down the tab.
		// Tactics 2 and 3 both enabled it. Every other section of tactic 1's block stays put.
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		for (int block = 1; block <= 3; block++) {
			for (int row = 0; row < 18; row++) {
				List<String> cells = new ArrayList<>(Collections.nCopies(22, ""));
				if (row == 0) {
					cells.set(0, "Top Publishers " + block);
					if (block > 1) {
						cells.set(4, "Creative analysis " + block);
					}
					cells.set(9, "Geo analysis " + block);
				}
				if (block > 1) {
					// The block's structure relative to its own anchor never changes, wherever it lands.
					if (row == 1) {
						cells.set(4, "CREATIVES LIVE");
						cells.set(5, "1" + block);
					}
					if (row == 5) {
						cells.set(4, "Creative");
						cells.set(5, "Impressions");
						cells.set(6, "CTR");
						cells.set(7, "VCR");
						cells.set(8, "Spend");
					}
					if (row == 6) {
						cells.set(4, "Hero " + block);
						cells.set(5, "1,200,00" + block);
						cells.set(6, "0.5" + block + "%");
						cells.set(7, "82.9%");
						cells.set(8, "$4,800");
					}
				}
				grid.add(cells);
			}
		}

		// When: both surviving tactics are read
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(2, 3));

		// Then: each block is read off its own anchor, so neither picks up the other's row
		assertThat(tables.get(2).creativesLive()).isEqualTo("12");
		assertThat(tables.get(2).rows())
				.containsExactly(new CreativeRow("Hero 2", "1,200,002", "0.52%", "82.9%", "$4,800"));
		assertThat(tables.get(3).creativesLive()).isEqualTo("13");
		assertThat(tables.get(3).rows())
				.containsExactly(new CreativeRow("Hero 3", "1,200,003", "0.53%", "82.9%", "$4,800"));
	}

	@Test
	void creativeTables_readsOnlyTheCreativeSectionsColumnsNotItsNeighboursTest() {
		// Given: a full block — Top Publishers (A–D), Creative analysis (E–I), Geo (J–L) — each carrying its
		// own data, with a "Creative"-named row in the publisher table and a geo row alongside
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		List<String> header = new ArrayList<>(Collections.nCopies(13, ""));
		header.set(0, "Top Publishers 1");
		header.set(4, "Creative analysis 1");
		header.set(9, "Geo analysis 1");
		grid.add(header);
		grid.add(row(13, Map.of(0, "CREATIVES LIVE", 1, "999", 4, "CREATIVES LIVE", 5, "12", 9, "MARKETS", 10, "14")));
		grid.add(row(13, Map.of(
				1, "Publisher", 2, "Impressions", 3, "Share of voice",
				4, "Creative", 5, "Impressions", 6, "CTR", 7, "VCR", 8, "Spend",
				9, "Geo", 10, "IMPS")));
		grid.add(row(13, Map.of(
				1, "YouTube", 2, "9,000,000", 3, "99%",
				4, "Hero 15s", 5, "1,200,000", 6, "0.58%", 7, "82.9%", 8, "$4,800",
				9, "New York", 10, "500,000")));
		while (grid.size() < 18) {
			grid.add(new ArrayList<>(Collections.nCopies(13, "")));
		}

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(1));

		// Then: only the creative section's own columns are read — the publisher row, the publisher block's
		// identically-labelled stat tile, and the geo columns are all outside the block and never picked up
		CreativeTable table = tables.get(1);
		assertThat(table.creativesLive()).isEqualTo("12");
		assertThat(table.rows())
				.containsExactly(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"));
	}

	@Test
	void creativeTables_survivesTheNeighbouringSectionBeingClearedTest() {
		// Given: tactic 1 enabled Creative analysis but not Geo, so the Geo anchor is gone and the creative
		// block's column span now runs to the next surviving anchor (Audience, col 12) instead of col 9
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		List<String> header = new ArrayList<>(Collections.nCopies(17, ""));
		header.set(4, "Creative analysis 1");
		header.set(12, "Audience analysis 1");
		grid.add(header);
		grid.add(row(17, Map.of(4, "CREATIVES LIVE", 5, "12", 12, "AGE DISTRIBUTION", 13, "25-34")));
		grid.add(row(17, Map.of(4, "Creative", 5, "Impressions", 6, "CTR", 7, "VCR", 8, "Spend")));
		grid.add(row(17, Map.of(4, "Hero 15s", 5, "1,200,000", 6, "0.58%", 7, "82.9%", 8, "$4,800")));
		while (grid.size() < 18) {
			grid.add(new ArrayList<>(Collections.nCopies(17, "")));
		}

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(1));

		// Then: the wider span reads only blank geo columns, so the block still comes back intact and the
		// audience section's value is never mistaken for a creative stat
		CreativeTable table = tables.get(1);
		assertThat(table.creativesLive()).isEqualTo("12");
		assertThat(table.rows())
				.containsExactly(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"));
	}

	@Test
	void creativeTables_doesNotBleedIntoTheNextTacticWhenAWholeBlockIsClearedTest() {
		// Given: tactic 2 turned every breakdown off, so its whole 18-row block is blank and carries no
		// anchor at all — the block height can then only be inferred from the 36-row gap between tactic 1's
		// and tactic 3's anchors, which is twice the real block height
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		for (int block = 1; block <= 3; block++) {
			for (int row = 0; row < 18; row++) {
				List<String> cells = new ArrayList<>(Collections.nCopies(9, ""));
				if (block != 2) {
					if (row == 0) {
						cells.set(4, "Creative analysis " + block);
					}
					if (row == 1) {
						cells.set(4, "CREATIVES LIVE");
						cells.set(5, "1" + block);
					}
					if (row == 5) {
						cells.set(4, "Creative");
						cells.set(5, "Impressions");
						cells.set(6, "CTR");
						cells.set(7, "VCR");
						cells.set(8, "Spend");
					}
					if (row == 6) {
						cells.set(4, "Hero " + block);
						cells.set(5, "1,200,00" + block);
						cells.set(6, "0.5" + block + "%");
						cells.set(7, "82.9%");
						cells.set(8, "$4,800");
					}
				}
				grid.add(cells);
			}
		}

		// When:
		Map<Integer, CreativeTable> tables = provider.creativeTables(grid, Set.of(1, 3));

		// Then: tactic 1's over-wide window only ever reaches the cleared block's blank rows — tactic 3's
		// own block starts exactly where the window ends, so its creative is never pulled into tactic 1
		assertThat(tables.get(1).rows())
				.containsExactly(new CreativeRow("Hero 1", "1,200,001", "0.51%", "82.9%", "$4,800"));
		assertThat(tables.get(3).rows())
				.containsExactly(new CreativeRow("Hero 3", "1,200,003", "0.53%", "82.9%", "$4,800"));
	}

	/**
	 * Builds one grid row of the given width with the given cells populated.
	 *
	 * @param width        the row's column count
	 * @param cellsByIndex zero-based column → value
	 * @return the row as trimmed cell strings
	 */
	private List<String> row(int width, Map<Integer, String> cellsByIndex) {
		List<String> cells = new ArrayList<>(Collections.nCopies(width, ""));
		cellsByIndex.forEach(cells::set);
		return cells;
	}

	/**
	 * Builds a "Breakdowns" tab carrying an 18-row Creative analysis block per given tactic: the anchor
	 * row, the four stat-tile rows, a header row, then the tactic's filled rows.
	 *
	 * @param rowsByTactic tactic number → its creative rows as {@code [name, imps, ctr, vcr, spend]}
	 * @return the tab as trimmed cell strings
	 */
	private List<List<String>> creativeGrid(Map<Integer, List<List<String>>> rowsByTactic) {
		List<List<String>> grid = new ArrayList<>();
		for (Map.Entry<Integer, List<List<String>>> entry : new java.util.TreeMap<>(rowsByTactic).entrySet()) {
			List<List<String>> block = new ArrayList<>();
			block.add(pad(List.of("Creative analysis " + entry.getKey(), "{{tactic " + entry.getKey() + "}}")));
			block.add(pad(List.of("CREATIVES LIVE", "12")));
			block.add(pad(List.of("BEST CTR / VCR", "0.58")));
			block.add(pad(List.of("AVG. CTR / VCR", "0.42")));
			block.add(pad(List.of("TOP CREATIVE", "Hero 15s")));
			block.add(pad(List.of("Creative", "Impressions", "CTR", "VCR", "Spend")));
			for (List<String> row : entry.getValue()) {
				block.add(pad(row));
			}
			while (block.size() < 18) {
				block.add(new ArrayList<>(Collections.nCopies(5, "")));
			}
			grid.addAll(block);
		}
		return grid;
	}

	/**
	 * Pads a row out to the creative block's five columns so every grid row is the same width.
	 *
	 * @param cells the row's populated leading cells
	 * @return a five-column row
	 */
	private List<String> pad(List<String> cells) {
		List<String> row = new ArrayList<>(cells);
		while (row.size() < 5) {
			row.add("");
		}
		return row;
	}

	/**
	 * Builds a "Breakdowns" tab carrying an 18-row Geo analysis block per given tactic: the anchor row, the
	 * three stat-tile rows (the last matched by its "MOST EFFICIENT" prefix), a blank row, a header row
	 * whose KPI column header is the resolved KPI type, then the tactic's filled rows.
	 *
	 * @param rowsByTactic tactic number → its geo rows as {@code [name, imps, kpi]}
	 * @return the tab as trimmed cell strings
	 */
	private List<List<String>> geoGrid(Map<Integer, List<List<String>>> rowsByTactic) {
		List<List<String>> grid = new ArrayList<>();
		for (Map.Entry<Integer, List<List<String>>> entry : new java.util.TreeMap<>(rowsByTactic).entrySet()) {
			List<List<String>> block = new ArrayList<>();
			block.add(padGeo(List.of("Geo analysis " + entry.getKey(), "{{tactic " + entry.getKey() + "}}")));
			block.add(padGeo(List.of("MARKETS ACTIVATED", "42")));
			block.add(padGeo(List.of("TOP GEO", "Miami")));
			block.add(padGeo(List.of("MOST EFFICIENT CTR", "0.48%")));
			block.add(padGeo(List.of("AUDIENCE FOOTPRINT", "{{tactic n}}")));
			block.add(padGeo(List.of("")));
			block.add(padGeo(List.of("Geo", "IMPS", "CTR")));
			for (List<String> row : entry.getValue()) {
				block.add(padGeo(row));
			}
			while (block.size() < 18) {
				block.add(new ArrayList<>(Collections.nCopies(3, "")));
			}
			grid.addAll(block);
		}
		return grid;
	}

	/**
	 * Pads a row out to the geo block's three columns so every grid row is the same width.
	 *
	 * @param cells the row's populated leading cells
	 * @return a three-column row
	 */
	private List<String> padGeo(List<String> cells) {
		List<String> row = new ArrayList<>(cells);
		while (row.size() < 3) {
			row.add("");
		}
		return row;
	}

	/**
	 * Builds a "Breakdowns" tab carrying an 18-row Audience analysis block per given tactic: the anchor
	 * row, the two stat tiles, the shared header row, then the age-distribution rows (cols 0-1) and the
	 * top-audience-segments rows (cols 3-4) laid out side by side.
	 *
	 * @param tablesByTactic tactic number → the audience block to render on the tab
	 * @return the tab as trimmed cell strings, five columns wide
	 */
	private List<List<String>> audienceGrid(Map<Integer, AudienceTable> tablesByTactic) {
		List<List<String>> grid = new ArrayList<>();
		for (Map.Entry<Integer, AudienceTable> entry : new java.util.TreeMap<>(tablesByTactic).entrySet()) {
			AudienceTable table = entry.getValue();
			List<List<String>> block = new ArrayList<>();
			block.add(padAudience(List.of("Audience analysis " + entry.getKey(), "{{tactic " + entry.getKey() + "}}")));
			block.add(padAudience(List.of("AGE DISTRIBUTION", table.ageDistribution())));
			block.add(padAudience(List.of("GENDER DEMOGRAPHICS", table.genderDemographics())));
			block.add(padAudience(List.of("TOP SEGMENT", "{{aud_n_1}}")));
			block.add(padAudience(List.of("age", "impressions", "", "Segment", "Affinity index")));
			int dataRows = Math.max(table.ageRows().size(), table.segmentRows().size());
			for (int i = 0; i < dataRows; i++) {
				List<String> row = new ArrayList<>(List.of("", "", "", "", ""));
				if (i < table.ageRows().size()) {
					row.set(0, table.ageRows().get(i).ageGroup());
					row.set(1, table.ageRows().get(i).impressions());
				}
				if (i < table.segmentRows().size()) {
					row.set(3, table.segmentRows().get(i).segment());
					row.set(4, table.segmentRows().get(i).affinityIndex());
				}
				block.add(row);
			}
			while (block.size() < 18) {
				block.add(new ArrayList<>(Collections.nCopies(5, "")));
			}
			grid.addAll(block);
		}
		return grid;
	}

	/**
	 * Pads a row out to the audience block's five columns so every grid row is the same width.
	 *
	 * @param cells the row's populated leading cells
	 * @return a five-column row
	 */
	private List<String> padAudience(List<String> cells) {
		List<String> row = new ArrayList<>(cells);
		while (row.size() < 5) {
			row.add("");
		}
		return row;
	}

	/**
	 * Builds a "Breakdowns" tab carrying an 18-row Device breakdown block per given tactic: the anchor
	 * row, the five stat-tile rows (label in col 0, value in col 1), a spacer, the header row, then the
	 * tactic's device rows (name in col 0, metrics in cols 1-4).
	 *
	 * @param tablesByTactic tactic number → the device block to render on the tab
	 * @return the tab as trimmed cell strings, five columns wide
	 */
	private List<List<String>> deviceGrid(Map<Integer, DeviceTable> tablesByTactic) {
		List<List<String>> grid = new ArrayList<>();
		for (Map.Entry<Integer, DeviceTable> entry : new java.util.TreeMap<>(tablesByTactic).entrySet()) {
			DeviceTable table = entry.getValue();
			List<List<String>> block = new ArrayList<>();
			block.add(padAudience(List.of("Device breakdown " + entry.getKey(), "{{tactic " + entry.getKey() + "}}")));
			block.add(padAudience(List.of("HIGHEST CTR", table.highestCtr())));
			block.add(padAudience(List.of("BEST COMPLETION", table.bestCompletion())));
			block.add(padAudience(List.of("DEVICES TRACKED", table.devicesTracked())));
			block.add(padAudience(List.of("TOP DEVICE", table.topDevice())));
			block.add(padAudience(List.of("TOP DEVICE - % OF IMPRESSIONS", table.topDeviceImpressionsPct())));
			block.add(padAudience(List.of("")));
			block.add(padAudience(List.of("Device", "Impressions", "CTR", "VCR", "Spend")));
			for (DeviceRow row : table.rows()) {
				block.add(padAudience(List.of(row.device(), row.impressions(), row.ctr(), row.vcr(), row.spend())));
			}
			while (block.size() < 18) {
				block.add(new ArrayList<>(Collections.nCopies(5, "")));
			}
			grid.addAll(block);
		}
		return grid;
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
