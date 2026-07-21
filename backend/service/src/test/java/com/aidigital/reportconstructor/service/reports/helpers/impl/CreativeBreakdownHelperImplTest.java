package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
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
class CreativeBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;

	@InjectMocks
	CreativeBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetValuesVerbatimAndDashTheSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 enabled Creative analysis and the user filled only 2 of the slide's 5 rows
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		CreativeTable table = new CreativeTable("12", "0.58", "0.42", "Hero 15s", List.of(
				new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"),
				new CreativeRow("Cutdown 6s", "600,000", "0.31%", "71.2%", "$2,100")));
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readCreativeInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the stat tiles are carried across exactly as typed
		assertThat(values.get("{{cr_live_1}}")).isEqualTo("12");
		assertThat(values.get("{{cr_bKPI_1}}")).isEqualTo("0.58");
		assertThat(values.get("{{cr_aKPI_1}}")).isEqualTo("0.42");
		assertThat(values.get("{{tactic 1 top creative name}}")).isEqualTo("Hero 15s");

		// Then: the filled rows land on the slide's renumbered row tokens
		assertThat(values.get("{{tactic 1 top creative name 1.1}}")).isEqualTo("Hero 15s");
		assertThat(values.get("{{tactic 1.1 top creative imps}}")).isEqualTo("1,200,000");
		assertThat(values.get("{{tactic 1.1 top creative ctr}}")).isEqualTo("0.58%");
		assertThat(values.get("{{tactic 1.1 top creative vcr}}")).isEqualTo("82.9%");
		assertThat(values.get("{{tactic 1.1 top creative spend}}")).isEqualTo("$4,800");
		assertThat(values.get("{{tactic 1 top creative name 1.2}}")).isEqualTo("Cutdown 6s");

		// Then: every remaining slot is dashed, so none can ship as a raw token
		assertThat(values.get("{{tactic 1 top creative name 1.3}}")).isEqualTo("—");
		assertThat(values.get("{{tactic 1.5 top creative imps}}")).isEqualTo("—");
		assertThat(values.get("{{tactic 1.5 top creative spend}}")).isEqualTo("—");
	}

	@Test
	void shouldDashStatTilesStillHoldingTheTemplatesOwnHintTokenTest() {
		// Given: the user overwrote only CREATIVES LIVE, leaving the template's {{…}} hint text in the rest —
		// the creative block ships with its tokens pre-typed, unlike the publisher table's empty cells
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		CreativeTable table = new CreativeTable(
				"12", "{{cr_bKPI_n}}", "{{cr_aKPI_n}}", "{{tactic n top creative name}}", List.of());
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readCreativeInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the untouched hints are dashed rather than shipped as raw tokens
		assertThat(values.get("{{cr_live_1}}")).isEqualTo("12");
		assertThat(values.get("{{cr_bKPI_1}}")).isEqualTo("—");
		assertThat(values.get("{{cr_aKPI_1}}")).isEqualTo("—");
		assertThat(values.get("{{tactic 1 top creative name}}")).isEqualTo("—");
	}

	@Test
	void shouldNotCarryTheTemplatesHintTokensIntoTheClaudeInputAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are all still the template's hints, with only the table filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		List<CreativeRow> rows = List.of(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"));
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new CreativeTable("{{cr_live_n}}", "{{cr_bKPI_n}}", "{{cr_aKPI_n}}", "", rows)));

		// When:
		BreakdownSectionInputs<CreativeTakeawayInput> read = helper.readCreativeInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token");

		// Then: the input sees the hints as absent, so Claude cannot read "{{cr_live_n}}" as a creatives count
		CreativeTable sent = read.inputs().get(1).table();
		assertThat(sent.creativesLive()).isEmpty();
		assertThat(sent.bestKpi()).isEmpty();
		assertThat(sent.rows()).isEqualTo(rows);
	}

	@Test
	void shouldBuildNoInputWhenTheTacticsCreativeBlockIsEmptyTest() {
		// Given: tactic 1 enabled Creative analysis but never filled the block in — the template's hints are
		// all that is left, which is not data
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new CreativeTable("{{cr_live_n}}", "{{cr_bKPI_n}}", "{{cr_aKPI_n}}", "", List.of())));

		// When:
		BreakdownSectionInputs<CreativeTakeawayInput> read = helper.readCreativeInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the tactic is enabled but produces no Claude input — there is nothing to observe
		assertThat(read.tactics()).containsExactly(1);
		assertThat(read.inputs()).isEmpty();

		// Then: the slide still gets its data tokens, dashed rather than raw
		assertThat(read.dataValues().get("{{cr_live_1}}")).isEqualTo("—");
	}

	@Test
	void shouldBuildAnInputOnlyForTacticsWithDataAndCarryTheirNameAndKpiTypeTest() {
		// Given: tactic 1 has a filled block, tactic 2 enabled the toggle but left its block empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("ca")), new BreakdownSelection(2, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.CREATIVE),
				2, EnumSet.of(BreakdownType.CREATIVE)));
		CreativeTable filled = new CreativeTable("12", "0.58", "0.42", "Hero 15s",
				List.of(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800")));
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, CreativeTable.EMPTY));

		// When:
		BreakdownSectionInputs<CreativeTakeawayInput> read = helper.readCreativeInputs(
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
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoTakeawaysTest() {
		// Given: tactic 1 was sent and answered nothing, tactic 2 left its block empty and was never sent —
		// only the first is a failure worth telling the user about
		Map<String, String> values = new LinkedHashMap<>();

		// When:
		List<String> warnings = helper.writeCreativeTakeaways(
				values, Set.of(1, 2), Set.of(1), Map.of(),
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"));

		// Then: exactly one warning, naming the tactic that lost its bullets
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("Creative analysis", "CTV", "KEY TAKEAWAYS");
		assertThat(warnings.getFirst()).doesNotContain("Display");

		// Then: both tactics' takeaways render blank rather than as raw tokens
		assertThat(values.get("{{cr_takeaway_tactic 1_1}}")).isEmpty();
		assertThat(values.get("{{cr_takeaway_tactic 2_1}}")).isEmpty();
	}

	@Test
	void shouldWriteTakeawaysForAnsweredTacticsInSlideOrderTest() {
		// Given: tactic 1 was sent and answered
		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, List<String>> takeaways = Map.of(1, List.of("one", "two", "three", "four"));

		// When:
		List<String> warnings = helper.writeCreativeTakeaways(
				values, Set.of(1), Set.of(1), takeaways, Map.of("{{tactic 1}}", "CTV"));

		// Then: the bullets are written in slide order with no warning
		assertThat(warnings).isEmpty();
		assertThat(values.get("{{cr_takeaway_tactic 1_1}}")).isEqualTo("one");
		assertThat(values.get("{{cr_takeaway_tactic 1_4}}")).isEqualTo("four");
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledCreativeAnalysisTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));

		// When:
		BreakdownSectionInputs<CreativeTakeawayInput> read = helper.readCreativeInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the sheet is never read and no values are produced
		assertThat(read.tactics()).isEmpty();
		assertThat(read.dataValues()).isEmpty();
		verify(sheetHelper, never()).readCreativeTables("sheet-url", Set.of(1), "token");
	}

	@Test
	void shouldCarryTheDecksResolvedHeadingAndKpiTypeOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and KPI type
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		when(sheetHelper.readCreativeTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, CreativeTable.EMPTY));

		// When:
		Map<String, String> values = helper.readCreativeInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 KPI type}}", "VCR"), "token").dataValues();

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 KPI type}}")).isEqualTo("VCR");
	}
}
