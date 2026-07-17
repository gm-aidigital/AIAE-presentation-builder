package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;
	@Mock
	ClaudeClient claude;

	@InjectMocks
	GeoBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetValuesVerbatimAndDashTheSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 enabled Geo analysis and the user filled only 2 of the slide's 8 rows
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		GeoTable table = new GeoTable("42", "Miami", "0.48%", List.of(
				new GeoRow("Miami", "1,200,000", "0.48%"),
				new GeoRow("Atlanta", "900,000", "0.46%")));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchGeoInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("i1", "i2", "i3", "i4", "reco")));

		// When:
		Map<String, String> values = helper.buildGeoValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the stat tiles are carried across exactly as typed
		assertThat(values.get("{{geo_1_amount}}")).isEqualTo("42");
		assertThat(values.get("{{geo_1_topgeo}}")).isEqualTo("Miami");
		assertThat(values.get("{{geo_1_topkpi}}")).isEqualTo("0.48%");

		// Then: the filled rows land on the slide's renumbered row tokens
		assertThat(values.get("{{geo_1.1}}")).isEqualTo("Miami");
		assertThat(values.get("{{geo_imp_1.1}}")).isEqualTo("1,200,000");
		assertThat(values.get("{{geo_kpi_1.1}}")).isEqualTo("0.48%");
		assertThat(values.get("{{geo_1.2}}")).isEqualTo("Atlanta");

		// Then: every remaining slot is dashed, so none can ship as a raw token
		assertThat(values.get("{{geo_1.3}}")).isEqualTo("—");
		assertThat(values.get("{{geo_imp_1.8}}")).isEqualTo("—");
		assertThat(values.get("{{geo_kpi_1.8}}")).isEqualTo("—");
	}

	@Test
	void shouldWriteFourInsightsAndTheRecommendationFromClaudesFiveStringsTest() {
		// Given: a filled block Claude answers with four insights then a forward-looking recommendation
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new GeoTable("42", "Miami", "0.48%", List.of(new GeoRow("Miami", "1,200,000", "0.48%")))));
		when(claude.batchGeoInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("insight one", "insight two", "insight three", "insight four", "do this")));

		// When:
		Map<String, String> values = helper.buildGeoValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the four insights map to the "what the map tells us" tokens, the fifth to the recommendation
		assertThat(values.get("{{geo_insight_1.1}}")).isEqualTo("insight one");
		assertThat(values.get("{{geo_insight_1.4}}")).isEqualTo("insight four");
		assertThat(values.get("{{geo_1_reco}}")).isEqualTo("do this");
	}

	@Test
	void shouldDashStatTilesStillHoldingTheTemplatesOwnHintTokenTest() {
		// Given: the user overwrote only MARKETS ACTIVATED, leaving the template's {{…}} hints in the rest
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		GeoTable table = new GeoTable("42", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", List.of());
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchGeoInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("i1", "i2", "i3", "i4", "reco")));

		// When:
		Map<String, String> values = helper.buildGeoValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the untouched hints are dashed rather than shipped as raw tokens
		assertThat(values.get("{{geo_1_amount}}")).isEqualTo("42");
		assertThat(values.get("{{geo_1_topgeo}}")).isEqualTo("—");
		assertThat(values.get("{{geo_1_topkpi}}")).isEqualTo("—");
	}

	@Test
	void shouldNotSendTheTemplatesHintTokensToClaudeAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are all still the template's hints, with only the table filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		List<GeoRow> rows = List.of(new GeoRow("Miami", "1,200,000", "0.48%"));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new GeoTable("{{geo_n_amount}}", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", rows)));
		when(claude.batchGeoInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("i1", "i2", "i3", "i4", "reco")));

		// When:
		helper.buildGeoValues("sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token");

		// Then: Claude sees the hints as absent, so it cannot read "{{geo_n_amount}}" as a markets count
		ArgumentCaptor<List<GeoInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchGeoInsights(captor.capture(), eq("brief"));
		GeoTable sent = captor.getValue().getFirst().table();
		assertThat(sent.marketsActivated()).isEmpty();
		assertThat(sent.topGeo()).isEmpty();
		assertThat(sent.rows()).isEqualTo(rows);
	}

	@Test
	void shouldNotAskClaudeWhenTheTacticsGeoBlockIsEmptyTest() {
		// Given: tactic 1 enabled Geo analysis but never filled the block in — only the template's hints remain
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new GeoTable("{{geo_n_amount}}", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", List.of())));

		// When:
		Map<String, String> values =
				helper.buildGeoValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: Claude is never asked — there is nothing to observe and any copy would be invented
		verifyNoInteractions(claude);

		// Then: the slide still gets its tokens, with blank insights/reco rather than raw tokens
		assertThat(values.get("{{geo_insight_1.1}}")).isEmpty();
		assertThat(values.get("{{geo_insight_1.4}}")).isEmpty();
		assertThat(values.get("{{geo_1_reco}}")).isEmpty();
		assertThat(values.get("{{geo_1_amount}}")).isEqualTo("—");
	}

	@Test
	void shouldSendOnlyTacticsWithDataToClaudeAndCarryTheirNameAndKpiTypeTest() {
		// Given: tactic 1 has a filled block, tactic 2 enabled the toggle but left its block empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("geo")), new BreakdownSelection(2, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.GEO),
				2, EnumSet.of(BreakdownType.GEO)));
		GeoTable filled = new GeoTable("42", "Miami", "0.48%", List.of(new GeoRow("Miami", "1,200,000", "0.48%")));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, GeoTable.empty()));
		when(claude.batchGeoInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("i1", "i2", "i3", "i4", "reco")));

		// When:
		Map<String, String> values = helper.buildGeoValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR", "{{tactic 2}}", "Display"),
				"brief", "token").values();

		// Then: only tactic 1 is sent, carrying its deck name and KPI type
		ArgumentCaptor<List<GeoInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchGeoInsights(captor.capture(), eq("brief"));
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst().tacticNum()).isEqualTo(1);
		assertThat(captor.getValue().getFirst().tacticName()).isEqualTo("CTV");
		assertThat(captor.getValue().getFirst().kpiType()).isEqualTo("VCR");

		// Then: tactic 1 gets its bullets and tactic 2's are blanked
		assertThat(values.get("{{geo_insight_1.1}}")).isEqualTo("i1");
		assertThat(values.get("{{geo_1_reco}}")).isEqualTo("reco");
		assertThat(values.get("{{geo_insight_2.1}}")).isEmpty();
		assertThat(values.get("{{geo_2_reco}}")).isEmpty();
	}

	@Test
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoInsightsTest() {
		// Given: tactic 1 has a filled block Claude answered nothing for, tactic 2 left its block empty and
		// was never sent — only the first is a failure worth telling the user about
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("geo")), new BreakdownSelection(2, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.GEO),
				2, EnumSet.of(BreakdownType.GEO)));
		GeoTable filled = new GeoTable("42", "Miami", "0.48%", List.of(new GeoRow("Miami", "1,200,000", "0.48%")));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, GeoTable.empty()));
		when(claude.batchGeoInsights(any(), eq("brief"))).thenReturn(Map.of());

		// When:
		BreakdownValues result = helper.buildGeoValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR", "{{tactic 2}}", "Display"),
				"brief", "token");

		// Then: exactly one warning, naming the tactic that lost its bullets
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Geo analysis", "CTV");
		assertThat(result.warnings().getFirst()).doesNotContain("Display");
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledGeoAnalysisTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));

		// When:
		Map<String, String> values =
				helper.buildGeoValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: the sheet is never read and no values are produced
		assertThat(values).isEmpty();
		verify(sheetHelper, never()).readGeoTables("sheet-url", Set.of(1), "token");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldCarryTheDecksResolvedHeadingAndKpiTypeOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and KPI type
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, GeoTable.empty()));

		// When:
		Map<String, String> values = helper.buildGeoValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR"), "brief", "token").values();

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 KPI type}}")).isEqualTo("VCR");
	}
}
