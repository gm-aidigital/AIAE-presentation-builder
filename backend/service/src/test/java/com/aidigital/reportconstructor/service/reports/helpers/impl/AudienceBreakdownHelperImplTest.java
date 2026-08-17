package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
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
class AudienceBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;

	@InjectMocks
	AudienceBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetValuesVerbatimAndDashTheSegmentSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 enabled Audience analysis and the user filled the stat tiles and only 2 of 5 segments
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "58% F / 42% M",
				List.of(),
				List.of(new AudienceSegmentRow("Auto Intenders", "142"),
						new AudienceSegmentRow("Sports Fans", "128")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the stat tiles are carried across exactly as typed
		assertThat(values.get("{{age_1_gr}}")).isEqualTo("25-34");
		assertThat(values.get("{{gender_1}}")).isEqualTo("58% F / 42% M");

		// Then: the filled segment rows land on the slide's renumbered segment/affinity tokens
		assertThat(values.get("{{aud_1_1}}")).isEqualTo("Auto Intenders");
		assertThat(values.get("{{aud_in_1_1}}")).isEqualTo("142");
		assertThat(values.get("{{aud_1_2}}")).isEqualTo("Sports Fans");
		assertThat(values.get("{{aud_in_1_2}}")).isEqualTo("128");

		// Then: every remaining segment slot is dashed, so none can ship as a raw token
		assertThat(values.get("{{aud_1_3}}")).isEqualTo("—");
		assertThat(values.get("{{aud_in_1_5}}")).isEqualTo("—");
	}

	@Test
	void shouldWriteTheFourFieldsFromClaudesStringsInSlideOrderTest() {
		// Given: a tactic Claude answered with the takeaway, what-worked, watch-out and recommendation
		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, List<String>> insights =
				Map.of(1, List.of("the takeaway", "what worked", "the watch-out", "do this next"));

		// When:
		helper.writeAudienceInsights(values, Set.of(1), Set.of(1), insights, Map.of("{{tactic 1}}", "CTV"));

		// Then: each string maps to its own slide token, in order
		assertThat(values.get("{{aud_1_takeaway}}")).isEqualTo("the takeaway");
		assertThat(values.get("{{aud_1_worked}}")).isEqualTo("what worked");
		assertThat(values.get("{{aud_1_flag}}")).isEqualTo("the watch-out");
		assertThat(values.get("{{aud_1_reco}}")).isEqualTo("do this next");
	}

	@Test
	void shouldDashStatTilesStillHoldingTheTemplatesOwnHintTokenTest() {
		// Given: the user overwrote only AGE DISTRIBUTION, leaving the template's {{…}} hint in GENDER
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "{{gender_n}}",
				List.of(), List.of(new AudienceSegmentRow("Auto Intenders", "142")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the untouched hint is dashed rather than shipped as a raw token
		assertThat(values.get("{{age_1_gr}}")).isEqualTo("25-34");
		assertThat(values.get("{{gender_1}}")).isEqualTo("—");
	}

	@Test
	void shouldNotCarryTheTemplatesHintTokensIntoTheClaudeInputAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are still the template's hints, with only the tables filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		List<AudienceSegmentRow> segments = List.of(new AudienceSegmentRow("Auto Intenders", "142"));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new AudienceTable("{{age_n_gr}}", "{{gender_n}}", List.of(), segments)));

		// When:
		BreakdownSectionInputs<AudienceInsightInput> read = helper.readAudienceInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token");

		// Then: the input sees the hints as absent, so Claude cannot read "{{age_n_gr}}" as a dominant age group
		AudienceTable sent = read.inputs().get(1).table();
		assertThat(sent.ageDistribution()).isEmpty();
		assertThat(sent.genderDemographics()).isEmpty();
		assertThat(sent.segmentRows()).isEqualTo(segments);
	}

	@Test
	void shouldBuildNoInputWhenTheTacticsAudienceBlockIsEmptyTest() {
		// Given: tactic 1 enabled Audience analysis but never filled the block in — only hints remain
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new AudienceTable("{{age_n_gr}}", "{{gender_n}}", List.of(), List.of())));

		// When:
		BreakdownSectionInputs<AudienceInsightInput> read = helper.readAudienceInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the tactic is enabled but produces no Claude input — there is nothing to observe
		assertThat(read.tactics()).containsExactly(1);
		assertThat(read.inputs()).isEmpty();

		// Then: the slide still gets its data tokens, dashed rather than raw
		assertThat(read.dataValues().get("{{age_1_gr}}")).isEqualTo("—");
		assertThat(read.dataValues().get("{{aud_1_1}}")).isEqualTo("—");
	}

	@Test
	void shouldBuildAnInputOnlyForTacticsWithDataAndCarryTheirNameTest() {
		// Given: tactic 1 has a filled block, tactic 2 enabled the toggle but left its block empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("aud")), new BreakdownSelection(2, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.AUDIENCE),
				2, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable filled = new AudienceTable("25-34", "58% F / 42% M", List.of(),
				List.of(new AudienceSegmentRow("Auto Intenders", "142")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, AudienceTable.EMPTY));

		// When:
		BreakdownSectionInputs<AudienceInsightInput> read = helper.readAudienceInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"),
				"token");

		// Then: only tactic 1 becomes a Claude input, carrying its deck name
		assertThat(read.inputs().keySet()).containsExactly(1);
		assertThat(read.inputs().get(1).tacticNum()).isEqualTo(1);
		assertThat(read.inputs().get(1).tacticName()).isEqualTo("CTV");
	}

	@Test
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoFieldsTest() {
		// Given: tactic 1 was sent and answered nothing, tactic 2 left its block empty and was never sent —
		// only the first is a failure worth telling the user about
		Map<String, String> values = new LinkedHashMap<>();

		// When:
		List<String> warnings = helper.writeAudienceInsights(
				values, Set.of(1, 2), Set.of(1), Map.of(),
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"));

		// Then: exactly one warning, naming the tactic that lost its copy
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("Audience analysis", "CTV");
		assertThat(warnings.getFirst()).doesNotContain("Display");

		// Then: both tactics' fields render blank rather than as raw tokens
		assertThat(values.get("{{aud_1_takeaway}}")).isEmpty();
		assertThat(values.get("{{aud_2_reco}}")).isEmpty();
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledAudienceAnalysisTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));

		// When:
		BreakdownSectionInputs<AudienceInsightInput> read = helper.readAudienceInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the sheet is never read and no values are produced
		assertThat(read.tactics()).isEmpty();
		assertThat(read.dataValues()).isEmpty();
		verify(sheetHelper, never()).readAudienceTables("sheet-url", Set.of(1), "token");
	}

	@Test
	void shouldCarryTheDecksHeadingAndGenderSplitOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and gender-split bars
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, AudienceTable.EMPTY));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 male}}", "42%", "{{tactic 1 female}}", "58%"),
				"token").dataValues();

		// Then: all three are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 male}}")).isEqualTo("42%");
		assertThat(values.get("{{tactic 1 female}}")).isEqualTo("58%");
	}

	@Test
	void shouldNotEmitTheTilesTheAudienceSlideDoesNotCarryTest() {
		// Given: a filled audience block and a deck that already resolved this tactic's reach/frequency/KPI
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "58% F / 42% M",
				List.of(),
				List.of(new AudienceSegmentRow("Auto Intenders", "142"),
						new AudienceSegmentRow("Sports Fans", "128")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 reach}}", "9,028", "{{tactic 1 f}}", "11",
						"{{tactic 1 KPI}}", "0.29%"),
				"token").dataValues();

		// Then: the slide has no top-segment tile and no reach/frequency/engagement tiles, so none is
		// written — the first segment row still ships as {{aud_1_1}} and the figures stay on the tactic's
		// own slide
		assertThat(values).doesNotContainKeys(
				"{{aud_1_top_segment}}", "{{aud_1_top_segment_index}}",
				"{{aud_1_reach}}", "{{aud_1_freq}}", "{{aud_1_engaged}}");
		assertThat(values.get("{{aud_1_1}}")).isEqualTo("Auto Intenders");
		assertThat(values.get("{{aud_in_1_1}}")).isEqualTo("142");
	}

	@Test
	void shouldComputeAgeBucketSharesAgainstTheTacticImpressionsTotalTest() {
		// Given: three filled age rows summing to 1,000 against a tactic that ran 2,000 impressions
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "",
				List.of(new AudienceAgeRow("18-24", "200"),
						new AudienceAgeRow("25–34", "500"),
						new AudienceAgeRow("65+", "300")),
				List.of());
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 imps}}", "2,000"), "token").dataValues();

		// Then: each bucket is a share of the tactic's 2,000, so the three filled buckets add to 50%
		// rather than to 100%, and the en-dash-typed "25–34" row still lands on its bucket
		assertThat(values.get("{{age_1_18}}")).isEqualTo("10%");
		assertThat(values.get("{{age_1_25}}")).isEqualTo("25%");
		assertThat(values.get("{{age_1_35}}")).isEqualTo("—");
		assertThat(values.get("{{age_1_65}}")).isEqualTo("15%");
	}

	@Test
	void shouldFallBackToTheAgeRowsOwnTotalWhenTheTacticImpressionsAreMissingTest() {
		// Given: the same age rows, but a placeholder map carrying no {{tactic 1 imps}}
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "",
				List.of(new AudienceAgeRow("18-24", "200"),
						new AudienceAgeRow("25–34", "500"),
						new AudienceAgeRow("65+", "300")),
				List.of());
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));

		// When:
		Map<String, String> values = helper.readAudienceInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token").dataValues();

		// Then: the shares are computed over the rows' own 1,000 total instead of dashing
		assertThat(values.get("{{age_1_18}}")).isEqualTo("20%");
		assertThat(values.get("{{age_1_25}}")).isEqualTo("50%");
		assertThat(values.get("{{age_1_65}}")).isEqualTo("30%");
	}
}