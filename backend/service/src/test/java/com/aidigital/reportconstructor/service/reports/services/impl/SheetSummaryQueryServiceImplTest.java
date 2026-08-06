package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.SheetSummary;
import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SheetSummaryQueryServiceImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	SheetPlaceholderReader placeholderReader;
	@Mock
	ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;

	@InjectMocks
	SheetSummaryQueryServiceImpl service;

	@Test
	void shouldProjectContiguousTacticsWithPlanAndFactFiguresTest() {
		// Given: the workbook grid parses to two CPM tactics' plan/fact tokens (tactic 3 absent)
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "CTV"),
				Map.entry("{{tactic 1 imps plan}}", "250,000"),
				Map.entry("{{tactic 1 imps}}", "248,113"),
				Map.entry("{{tactic 1 spend plan}}", "$1,500"),
				Map.entry("{{tactic 1 spend}}", "$1,489"),
				Map.entry("{{tactic 2}}", "Display"),
				Map.entry("{{tactic 2 imps plan}}", "125,000"),
				Map.entry("{{tactic 2 imps}}", "130,402"),
				Map.entry("{{tactic 2 spend plan}}", "$1,500"),
				Map.entry("{{tactic 2 spend}}", "$1,502")));

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: one row per contiguous tactic, in order, carrying the sheet's plan/fact strings —
		// with no clicks/completions plan token present, the CPM impressions figures are the unit
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::unitPlan,
						SheetSummaryRow::unitFact, SheetSummaryRow::spendPlan, SheetSummaryRow::spendFact)
				.containsExactly(
						tuple("CTV", "250,000", "248,113", "$1,500", "$1,489"),
						tuple("Display", "125,000", "130,402", "$1,500", "$1,502"));
	}

	@Test
	void shouldPreferClicksOverImpressionsForACpcTacticTest() {
		// Given: a CPC tactic carries both a clicks plan/fact and the always-literal impressions plan/fact
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "Paid Search"),
				Map.entry("{{tactic 1 clicks plan}}", "5,000"),
				Map.entry("{{tactic 1 clicks}}", "4,800"),
				Map.entry("{{tactic 1 imps plan}}", "1,000,000"),
				Map.entry("{{tactic 1 imps}}", "960,000"),
				Map.entry("{{tactic 1 spend plan}}", "$2,000"),
				Map.entry("{{tactic 1 spend}}", "$1,950")));

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: the main-unit columns carry clicks, not impressions
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::unitPlan, SheetSummaryRow::unitFact)
				.containsExactly(tuple("Paid Search", "5,000", "4,800"));
	}

	@Test
	void shouldPreferCompletionsOverImpressionsForACpvTacticTest() {
		// Given: a CPV tactic carries both a completions plan/fact and the always-literal impressions plan/fact
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "YouTube"),
				Map.entry("{{tactic 1 completions plan}}", "80,000"),
				Map.entry("{{tactic 1 complitions}}", "76,500"),
				Map.entry("{{tactic 1 imps plan}}", "500,000"),
				Map.entry("{{tactic 1 imps}}", "478,000"),
				Map.entry("{{tactic 1 spend plan}}", "$3,000"),
				Map.entry("{{tactic 1 spend}}", "$2,900")));

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: the main-unit columns carry completions, not impressions
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::unitPlan, SheetSummaryRow::unitFact)
				.containsExactly(tuple("YouTube", "80,000", "76,500"));
	}

	@Test
	void shouldFallBackToImpressionsWhenClicksAndCompletionsPlanAreUnresolvedDashesTest() {
		// Given: a CPM tactic whose sheet carries the em-dash a resolver writes into a Clicks/Completions
		// Plan cell that does not apply to it (see PlaceholderValueFlattenerImpl), alongside real impressions
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "CTV"),
				Map.entry("{{tactic 1 clicks plan}}", "—"),
				Map.entry("{{tactic 1 clicks}}", "—"),
				Map.entry("{{tactic 1 completions plan}}", "—"),
				Map.entry("{{tactic 1 complitions}}", "—"),
				Map.entry("{{tactic 1 imps plan}}", "250,000"),
				Map.entry("{{tactic 1 imps}}", "248,113"),
				Map.entry("{{tactic 1 spend plan}}", "$1,500"),
				Map.entry("{{tactic 1 spend}}", "$1,489")));

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: the dash is not mistaken for a resolved clicks/completions plan — impressions win
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::unitPlan, SheetSummaryRow::unitFact)
				.containsExactly(tuple("CTV", "250,000", "248,113"));
	}

	@Test
	void shouldPickTheBoughtUnitByRateTypeWhenEveryPlanColumnIsFilledTest() {
		// Given: an EOM workbook where all three plan columns carry a figure for every tactic (the
		// non-bought ones derived through the CTR/VCR benchmarks) — only the "Rate type" cell says which
		// unit each tactic was actually bought in
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "CTV"),
				Map.entry("{{rate type 1}}", "CPM"),
				Map.entry("{{tactic 1 imps plan}}", "250,000"),
				Map.entry("{{tactic 1 imps}}", "248,113"),
				Map.entry("{{tactic 1 clicks plan}}", "500"),
				Map.entry("{{tactic 1 clicks}}", "480"),
				Map.entry("{{tactic 2}}", "Paid Search"),
				Map.entry("{{rate type 2}}", "CPC"),
				Map.entry("{{tactic 2 imps plan}}", "1,000,000"),
				Map.entry("{{tactic 2 imps}}", "960,000"),
				Map.entry("{{tactic 2 clicks plan}}", "5,000"),
				Map.entry("{{tactic 2 clicks}}", "4,800"),
				Map.entry("{{tactic 3}}", "YouTube"),
				Map.entry("{{rate type 3}}", "CPV"),
				Map.entry("{{tactic 3 imps plan}}", "500,000"),
				Map.entry("{{tactic 3 imps}}", "478,000"),
				Map.entry("{{tactic 3 clicks plan}}", "1,000"),
				Map.entry("{{tactic 3 completions plan}}", "80,000"),
				Map.entry("{{tactic 3 complitions}}", "76,500")));

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: each row shows the unit its rate type was bought in, not whichever column is filled first
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::unitPlan, SheetSummaryRow::unitFact)
				.containsExactly(
						tuple("CTV", "250,000", "248,113"),
						tuple("Paid Search", "5,000", "4,800"),
						tuple("YouTube", "80,000", "76,500"));
	}

	@Test
	void shouldCarryTheWorkbooksCampaignContextCellsTest() {
		// Given: a workbook whose RFP info and change-log cells were filled in the sheet after it was built
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.ofEntries(
				Map.entry("{{tactic 1}}", "CTV"),
				Map.entry("{{RFP info}}", "Client is Lennox, a residential HVAC brand."),
				Map.entry("{{change log}}", "Budget raised mid-flight.")));

		// When: the summary is read back
		SheetSummary summary = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1");

		// Then: the review step sees the sheet's live context, not whatever the draft remembered
		assertThat(summary.rfpInfo()).isEqualTo("Client is Lennox, a residential HVAC brand.");
		assertThat(summary.changeLog()).isEqualTo("Budget raised mid-flight.");
	}

	@Test
	void shouldReportNoCampaignContextWhenTheCellsAreEmptyTest() {
		// Given: a workbook that carries a tactic but neither context cell
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.of("{{tactic 1}}", "CTV"));

		// When: the summary is read back
		SheetSummary summary = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1");

		// Then: both context fields come back null, so the review step asks for a brief
		assertThat(summary.rfpInfo()).isNull();
		assertThat(summary.changeLog()).isNull();
	}

	@Test
	void shouldReturnEmptyWhenNoTacticsPresentTest() {
		// Given: the workbook carries no summary table (no tactic-name tokens)
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of());
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.of());

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1").rows();

		// Then: no rows are produced
		assertThat(rows).isEmpty();
	}
}
