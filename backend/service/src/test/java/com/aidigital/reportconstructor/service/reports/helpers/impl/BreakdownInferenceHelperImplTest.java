package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BreakdownInferenceHelperImplTest {

	private static final String SHEET = "https://docs.google.com/spreadsheets/d/abc";

	/**
	 * Stubs every section reader to return nothing for the given tactics, so a test only has to
	 * stub the one section it is about.
	 *
	 * @param sheetHelper the mocked sheet helper
	 * @param tacticNums  the tactics being inferred
	 */
	void stubAllEmpty(ReportSheetHelper sheetHelper, Set<Integer> tacticNums) {
		when(sheetHelper.readPublisherTables(eq(SHEET), eq(tacticNums), eq(null))).thenReturn(Map.of());
		when(sheetHelper.readCreativeTables(eq(SHEET), eq(tacticNums), eq(null))).thenReturn(Map.of());
		when(sheetHelper.readGeoTables(eq(SHEET), eq(tacticNums), eq(null))).thenReturn(Map.of());
		when(sheetHelper.readAudienceTables(eq(SHEET), eq(tacticNums), eq(null))).thenReturn(Map.of());
		when(sheetHelper.readDeviceTables(eq(SHEET), eq(tacticNums), eq(null))).thenReturn(Map.of());
	}

	@Test
	void shouldEnableOnlyTheSectionsThatCarryDataTest() {
		// Given: tactic 1 has publishers and geo filled in, tactic 2 has nothing
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		Set<Integer> tactics = Set.of(1, 2);
		stubAllEmpty(sheetHelper, tactics);
		when(sheetHelper.readPublisherTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, List.of(new PublisherRow("Hulu", "1,000", "12%")), 2, List.of()));
		when(sheetHelper.readGeoTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, new GeoTable("4", "New York", "0.42%", List.of()), 2, GeoTable.EMPTY));
		BreakdownInferenceHelperImpl helper = new BreakdownInferenceHelperImpl(sheetHelper);

		// When: the selections are inferred
		List<BreakdownSelection> selections = helper.infer(SHEET, 2, null);

		// Then: every tactic gets an entry, carrying exactly the sections it has data for
		assertThat(selections).hasSize(2);
		assertThat(selections.get(0).tacticNum()).isEqualTo(1);
		assertThat(selections.get(0).breakdowns()).containsExactly("tp", "geo");
		assertThat(selections.get(1).tacticNum()).isEqualTo(2);
		assertThat(selections.get(1).breakdowns()).isEmpty();
	}

	@Test
	void shouldTreatAnEmptiedSectionAsDisabledTest() {
		// Given: a workbook this app generated, where the unselected sections were blanked — the
		// readers report those tables as empty
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		Set<Integer> tactics = Set.of(1);
		stubAllEmpty(sheetHelper, tactics);
		when(sheetHelper.readCreativeTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, CreativeTable.EMPTY));
		when(sheetHelper.readAudienceTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, AudienceTable.EMPTY));
		when(sheetHelper.readDeviceTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, DeviceTable.EMPTY));
		BreakdownInferenceHelperImpl helper = new BreakdownInferenceHelperImpl(sheetHelper);

		// When
		List<BreakdownSelection> selections = helper.infer(SHEET, 1, null);

		// Then: nothing is enabled, so the deck inserts no breakdown slides for that tactic
		assertThat(selections).singleElement()
				.extracting(BreakdownSelection::breakdowns).isEqualTo(List.of());
	}

	@Test
	void shouldEnableASectionFilledOnlyInItsSummaryCellsTest() {
		// Given: a device block whose table rows are blank but whose summary figures were typed in
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		Set<Integer> tactics = Set.of(1);
		stubAllEmpty(sheetHelper, tactics);
		when(sheetHelper.readDeviceTables(eq(SHEET), eq(tactics), eq(null)))
				.thenReturn(Map.of(1, new DeviceTable("0.41%", "72%", "3", "Mobile", "61%", List.of())));
		BreakdownInferenceHelperImpl helper = new BreakdownInferenceHelperImpl(sheetHelper);

		// When
		List<BreakdownSelection> selections = helper.infer(SHEET, 1, null);

		// Then: the section counts as prepared — the user did fill it in
		assertThat(selections).singleElement()
				.extracting(BreakdownSelection::breakdowns).isEqualTo(List.of("dev"));
	}

	@Test
	void shouldReadEverySectionOnceForTheWholeWorkbookTest() {
		// Given: a workbook with three tactics
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		Set<Integer> tactics = Set.of(1, 2, 3);
		stubAllEmpty(sheetHelper, tactics);
		BreakdownInferenceHelperImpl helper = new BreakdownInferenceHelperImpl(sheetHelper);

		// When
		helper.infer(SHEET, 3, null);

		// Then: five reads for the workbook, not five per tactic
		verify(sheetHelper).readPublisherTables(eq(SHEET), eq(tactics), eq(null));
		verify(sheetHelper).readCreativeTables(eq(SHEET), eq(tactics), eq(null));
		verify(sheetHelper).readGeoTables(eq(SHEET), eq(tactics), eq(null));
		verify(sheetHelper).readAudienceTables(eq(SHEET), eq(tactics), eq(null));
		verify(sheetHelper).readDeviceTables(eq(SHEET), eq(tactics), eq(null));
	}

	@Test
	void shouldInferNothingWhenTheWorkbookReportsNoTacticsTest() {
		// Given: a sheet with no tactics at all
		ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
		BreakdownInferenceHelperImpl helper = new BreakdownInferenceHelperImpl(sheetHelper);

		// When-Then: no reads are made and nothing is inferred
		assertThat(helper.infer(SHEET, 0, null)).isEmpty();
		verify(sheetHelper, org.mockito.Mockito.never())
				.readPublisherTables(eq(SHEET), eq(Set.of()), eq(null));
	}
}
