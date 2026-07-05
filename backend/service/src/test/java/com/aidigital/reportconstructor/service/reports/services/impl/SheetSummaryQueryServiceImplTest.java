package com.aidigital.reportconstructor.service.reports.services.impl;

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
		// Given: the workbook grid parses to two tactics' plan/fact tokens (tactic 3 absent)
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
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1");

		// Then: one row per contiguous tactic, in order, carrying the sheet's plan/fact strings
		assertThat(rows)
				.extracting(SheetSummaryRow::tactic, SheetSummaryRow::impressionsPlan,
						SheetSummaryRow::impressionsFact, SheetSummaryRow::spendPlan, SheetSummaryRow::spendFact)
				.containsExactly(
						tuple("CTV", "250,000", "248,113", "$1,500", "$1,489"),
						tuple("Display", "125,000", "130,402", "$1,500", "$1,502"));
	}

	@Test
	void shouldReturnEmptyWhenNoTacticsPresentTest() {
		// Given: the workbook carries no summary table (no tactic-name tokens)
		when(userGoogleTokens.getIfAvailable()).thenReturn(null);
		when(sheetHelper.readSheetGrid(anyString(), any())).thenReturn(List.of());
		when(placeholderReader.readPlaceholders(any())).thenReturn(Map.of());

		// When: the summary is read back
		List<SheetSummaryRow> rows = service.readSummary("https://docs.google.com/spreadsheets/d/abc/edit", "user_1");

		// Then: no rows are produced
		assertThat(rows).isEmpty();
	}
}
