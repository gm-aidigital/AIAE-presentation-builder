package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;

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

		// When:
		Map<String, String> values = helper.readGeoInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

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
	void shouldWriteThreeInsightsAndTheRecommendationFromClaudesFourStringsTest() {
		// Given: a tactic Claude answered with three insights then a forward-looking recommendation
		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, List<String>> insights =
				Map.of(1, List.of("insight one", "insight two", "insight three", "do this"));

		// When:
		helper.writeGeoInsights(values, Set.of(1), Set.of(1), insights, Map.of("{{tactic 1}}", "CTV"));

		// Then: the three insights map to the "what the map tells us" tokens, the fourth to the
		// recommendation, and no fourth insight is written — the slide carries no slot for one
		assertThat(values.get("{{geo_insight_1.1}}")).isEqualTo("insight one");
		assertThat(values.get("{{geo_insight_1.3}}")).isEqualTo("insight three");
		assertThat(values).doesNotContainKey("{{geo_insight_1.4}}");
		assertThat(values.get("{{geo_1_reco}}")).isEqualTo("do this");
	}

	@Test
	void shouldDashStatTilesStillHoldingTheTemplatesOwnHintTokenTest() {
		// Given: the user overwrote only MARKETS ACTIVATED, leaving the template's {{…}} hints in the rest
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		GeoTable table = new GeoTable("42", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", List.of());
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readGeoInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the untouched hints are dashed rather than shipped as raw tokens
		assertThat(values.get("{{geo_1_amount}}")).isEqualTo("42");
		assertThat(values.get("{{geo_1_topgeo}}")).isEqualTo("—");
		assertThat(values.get("{{geo_1_topkpi}}")).isEqualTo("—");
	}

	@Test
	void shouldNotCarryTheTemplatesHintTokensIntoTheClaudeInputAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are all still the template's hints, with only the table filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		List<GeoRow> rows = List.of(new GeoRow("Miami", "1,200,000", "0.48%"));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new GeoTable("{{geo_n_amount}}", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", rows)));

		// When:
		BreakdownSectionInputs<GeoInsightInput> read = helper.readGeoInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token");

		// Then: the input sees the hints as absent, so Claude cannot read "{{geo_n_amount}}" as a markets count
		GeoTable sent = read.inputs().get(1).table();
		assertThat(sent.marketsActivated()).isEmpty();
		assertThat(sent.topGeo()).isEmpty();
		assertThat(sent.rows()).isEqualTo(rows);
	}

	@Test
	void shouldBuildNoInputWhenTheTacticsGeoBlockIsEmptyTest() {
		// Given: tactic 1 enabled Geo analysis but never filled the block in — only the template's hints remain
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new GeoTable("{{geo_n_amount}}", "{{geo_n_topgeo}}", "{{geo_n_topkpi}}", List.of())));

		// When:
		BreakdownSectionInputs<GeoInsightInput> read = helper.readGeoInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the tactic is enabled but produces no Claude input — there is nothing to observe
		assertThat(read.tactics()).containsExactly(1);
		assertThat(read.inputs()).isEmpty();

		// Then: the slide still gets its data tokens, dashed rather than raw
		assertThat(read.dataValues().get("{{geo_1_amount}}")).isEqualTo("—");
	}

	@Test
	void shouldBuildAnInputOnlyForTacticsWithDataAndCarryTheirNameAndKpiTypeTest() {
		// Given: tactic 1 has a filled block, tactic 2 enabled the toggle but left its block empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("geo")), new BreakdownSelection(2, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.GEO),
				2, EnumSet.of(BreakdownType.GEO)));
		GeoTable filled = new GeoTable("42", "Miami", "0.48%", List.of(new GeoRow("Miami", "1,200,000", "0.48%")));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, GeoTable.EMPTY));

		// When:
		BreakdownSectionInputs<GeoInsightInput> read = helper.readGeoInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR", "{{tactic 2}}", "Display"),
				"token");

		// Then: only tactic 1 becomes a Claude input, carrying its deck name and KPI type
		assertThat(read.inputs().keySet()).containsExactly(1);
		assertThat(read.inputs().get(1).tacticNum()).isEqualTo(1);
		assertThat(read.inputs().get(1).tacticName()).isEqualTo("CTV");
		assertThat(read.inputs().get(1).kpiType()).isEqualTo("VCR");
	}

	@Test
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoInsightsTest() {
		// Given: tactic 1 was sent and answered nothing, tactic 2 left its block empty and was never sent —
		// only the first is a failure worth telling the user about
		Map<String, String> values = new LinkedHashMap<>();

		// When:
		List<String> warnings = helper.writeGeoInsights(
				values, Set.of(1, 2), Set.of(1), Map.of(),
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"));

		// Then: exactly one warning, naming the tactic that lost its bullets
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("Geo analysis", "CTV");
		assertThat(warnings.getFirst()).doesNotContain("Display");

		// Then: both tactics' insights render blank rather than as raw tokens
		assertThat(values.get("{{geo_insight_1.1}}")).isEmpty();
		assertThat(values.get("{{geo_1_reco}}")).isEmpty();
		assertThat(values.get("{{geo_insight_2.1}}")).isEmpty();
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledGeoAnalysisTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));

		// When:
		BreakdownSectionInputs<GeoInsightInput> read = helper.readGeoInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the sheet is never read and no values are produced
		assertThat(read.tactics()).isEmpty();
		assertThat(read.dataValues()).isEmpty();
		verify(sheetHelper, never()).readGeoTables("sheet-url", Set.of(1), "token");
	}

	@Test
	void shouldCarryTheDecksResolvedHeadingAndKpiTypeOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and KPI type
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));
		when(sheetHelper.readGeoTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, GeoTable.EMPTY));

		// When:
		Map<String, String> values = helper.readGeoInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR"), "token").dataValues();

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 KPI type}}")).isEqualTo("VCR");
	}
}
