package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSheetHelperImplTest {

	@Mock
	SheetDeckProvider sheets;
	@Mock
	TacticExtractionHelper tacticExtraction;
	@Mock
	ReportNumberParser reportNumbers;

	@InjectMocks
	ReportSheetHelperImpl helper;

	@Test
	void shouldExtractSpreadsheetIdFromSheetUrlTest() {
		assertThat(helper.extractSpreadsheetId("https://docs.google.com/spreadsheets/d/abc-123_9/edit"))
				.isEqualTo("abc-123_9");
		assertThat(helper.extractSpreadsheetId(null)).isNull();
	}

	@Test
	void shouldSkipPacingTablesWhenRequiredInputsMissingTest() {
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null, null, null);

		List<String> warnings = helper.writePacingTables(
				"https://docs.google.com/spreadsheets/d/abc/edit", payload, emptyCampaignData(), Map.of(), "token");

		assertThat(warnings).isEmpty();
		verify(sheets, never()).writePacingTables(any(), any());
	}

	@Test
	void shouldReturnSkipWarningWhenSpreadsheetIdMissingTest() {
		GeneratePayload payload = payloadWithPacingInputs();

		List<String> warnings = helper.writePacingTables(
				"https://example.com/no-id", payload, emptyCampaignData(), placeholderMap(), "token");

		assertThat(warnings).containsExactly(
				"Pacing tables skipped — could not determine spreadsheet id from https://example.com/no-id");
	}

	@Test
	void shouldBuildPacingTablesRequestFromPayloadAndPlaceholdersTest() {
		GeneratePayload payload = payloadWithPacingInputs();
		when(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows())).thenReturn(1);
		when(tacticExtraction.getTacticKpiType("Display")).thenReturn("ctr");
		when(reportNumbers.parseReportNumber("500")).thenReturn(500.0);
		when(reportNumbers.parseReportNumber("1,234")).thenReturn(1234.0);
		when(sheets.writePacingTables(eq("sheet-id"), any())).thenReturn(List.of());

		List<String> warnings = helper.writePacingTables(
				"https://docs.google.com/spreadsheets/d/sheet-id/edit", payload, emptyCampaignData(),
				placeholderMap(), "token");

		assertThat(warnings).isEmpty();
		ArgumentCaptor<PacingTablesRequest> captor = ArgumentCaptor.forClass(PacingTablesRequest.class);
		verify(sheets).writePacingTables(eq("sheet-id"), captor.capture());
		PacingTablesRequest req = captor.getValue();
		assertThat(req.tacticCount()).isEqualTo(1);
		assertThat(req.distTacticNames()).containsEntry(1, "Display");
		assertThat(req.distTacticImps()).containsEntry(1, 500.0);
		assertThat(req.distTotalImps()).isEqualTo(1234.0);
		assertThat(req.tacticKpiTypes()).containsEntry(1, "ctr");
		assertThat(req.userGoogleAccessToken()).isEqualTo("token");
	}

	@Test
	void shouldReadSheetGridByExtractedSpreadsheetIdTest() {
		// Given: the provider returns a grid for the id parsed from the sheet URL
		List<List<String>> grid = List.of(List.of("Client name:", "Acme"));
		when(sheets.readSheetGrid("sheet-id", "token")).thenReturn(grid);

		// When: the helper reads the grid from a full sheet URL
		List<List<String>> result = helper.readSheetGrid(
				"https://docs.google.com/spreadsheets/d/sheet-id/edit", "token");

		// Then: the id is extracted and the provider's grid is returned
		assertThat(result).isEqualTo(grid);
		verify(sheets).readSheetGrid("sheet-id", "token");
	}

	@Test
	void shouldReturnEmptyGridWhenSpreadsheetIdMissingTest() {
		// Given: a URL that carries no /d/<id> segment

		// When: the helper is asked to read its grid
		List<List<String>> result = helper.readSheetGrid("https://example.com/no-id", "token");

		// Then: an empty grid is returned and the provider is never called
		assertThat(result).isEmpty();
		verify(sheets, never()).readSheetGrid(any(), any());
	}

	private static GeneratePayload payloadWithPacingInputs() {
		return new GeneratePayload(
				"brief",
				"standard",
				"",
				List.of(List.of("Media"), List.of("Display")),
				List.of(List.of("Label", "Value")),
				List.of(),
				List.of(),
				List.of(),
				List.of(new LineItemMapping("Display", "99", 1)),
				"sheet-id",
				null,
				null,
				null
		);
	}

	private static CampaignData emptyCampaignData() {
		return new CampaignData(
				"", "", "", "", "",
				new FlightDates(null, null),
				"", "", "", "", "",
				new Totals(0, 0, 0, 0, null, null),
				Map.of(),
				""
		);
	}

	private static Map<String, String> placeholderMap() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("{{total imps}}", "1,234");
		map.put("{{tactic 1}}", "Display");
		map.put("{{tactic 1 imps}}", "500");
		return map;
	}
}
