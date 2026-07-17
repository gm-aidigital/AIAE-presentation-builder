package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
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
class AudienceBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;
	@Mock
	ClaudeClient claude;

	@InjectMocks
	AudienceBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetValuesVerbatimAndDashTheSegmentSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 enabled Audience analysis and the user filled the stat tiles and only 2 of 5 segments
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable table = new AudienceTable("25-34", "58% F / 42% M",
				List.of(new AudienceAgeRow("25-34", "1,200,000")),
				List.of(new AudienceSegmentRow("Auto Intenders", "142"),
						new AudienceSegmentRow("Sports Fans", "128")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchAudienceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("takeaway", "worked", "flag", "reco")));

		// When:
		Map<String, String> values = helper.buildAudienceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

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
		// Given: a filled block Claude answers with the takeaway, what-worked, watch-out and recommendation
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new AudienceTable("25-34", "58% F / 42% M", List.of(),
						List.of(new AudienceSegmentRow("Auto Intenders", "142")))));
		when(claude.batchAudienceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("the takeaway", "what worked", "the watch-out", "do this next")));

		// When:
		Map<String, String> values = helper.buildAudienceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

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
		when(claude.batchAudienceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		Map<String, String> values = helper.buildAudienceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the untouched hint is dashed rather than shipped as a raw token
		assertThat(values.get("{{age_1_gr}}")).isEqualTo("25-34");
		assertThat(values.get("{{gender_1}}")).isEqualTo("—");
	}

	@Test
	void shouldNotSendTheTemplatesHintTokensToClaudeAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are still the template's hints, with only the tables filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		List<AudienceSegmentRow> segments = List.of(new AudienceSegmentRow("Auto Intenders", "142"));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new AudienceTable("{{age_n_gr}}", "{{gender_n}}", List.of(), segments)));
		when(claude.batchAudienceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		helper.buildAudienceValues("sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token");

		// Then: Claude sees the hints as absent, so it cannot read "{{age_n_gr}}" as a dominant age group
		ArgumentCaptor<List<AudienceInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchAudienceInsights(captor.capture(), eq("brief"));
		AudienceTable sent = captor.getValue().getFirst().table();
		assertThat(sent.ageDistribution()).isEmpty();
		assertThat(sent.genderDemographics()).isEmpty();
		assertThat(sent.segmentRows()).isEqualTo(segments);
	}

	@Test
	void shouldNotAskClaudeWhenTheTacticsAudienceBlockIsEmptyTest() {
		// Given: tactic 1 enabled Audience analysis but never filled the block in — only hints remain
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new AudienceTable("{{age_n_gr}}", "{{gender_n}}", List.of(), List.of())));

		// When:
		Map<String, String> values =
				helper.buildAudienceValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: Claude is never asked — there is nothing to observe and any copy would be invented
		verifyNoInteractions(claude);

		// Then: the slide still gets its tokens, with blank fields and dashed tiles rather than raw tokens
		assertThat(values.get("{{aud_1_takeaway}}")).isEmpty();
		assertThat(values.get("{{aud_1_reco}}")).isEmpty();
		assertThat(values.get("{{age_1_gr}}")).isEqualTo("—");
		assertThat(values.get("{{aud_1_1}}")).isEqualTo("—");
	}

	@Test
	void shouldSendOnlyTacticsWithDataToClaudeAndCarryTheirNameTest() {
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
				2, AudienceTable.empty()));
		when(claude.batchAudienceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		Map<String, String> values = helper.buildAudienceValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"),
				"brief", "token").values();

		// Then: only tactic 1 is sent, carrying its deck name
		ArgumentCaptor<List<AudienceInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchAudienceInsights(captor.capture(), eq("brief"));
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst().tacticNum()).isEqualTo(1);
		assertThat(captor.getValue().getFirst().tacticName()).isEqualTo("CTV");

		// Then: tactic 1 gets its fields and tactic 2's are blanked
		assertThat(values.get("{{aud_1_takeaway}}")).isEqualTo("t");
		assertThat(values.get("{{aud_2_takeaway}}")).isEmpty();
		assertThat(values.get("{{aud_2_reco}}")).isEmpty();
	}

	@Test
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoFieldsTest() {
		// Given: tactic 1 has a filled block Claude answered nothing for, tactic 2 left its block empty and
		// was never sent — only the first is a failure worth telling the user about
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("aud")), new BreakdownSelection(2, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.AUDIENCE),
				2, EnumSet.of(BreakdownType.AUDIENCE)));
		AudienceTable filled = new AudienceTable("25-34", "58% F / 42% M", List.of(),
				List.of(new AudienceSegmentRow("Auto Intenders", "142")));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, AudienceTable.empty()));
		when(claude.batchAudienceInsights(any(), eq("brief"))).thenReturn(Map.of());

		// When:
		BreakdownValues result = helper.buildAudienceValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"),
				"brief", "token");

		// Then: exactly one warning, naming the tactic that lost its copy
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Audience analysis", "CTV");
		assertThat(result.warnings().getFirst()).doesNotContain("Display");
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledAudienceAnalysisTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));

		// When:
		Map<String, String> values =
				helper.buildAudienceValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: the sheet is never read and no values are produced
		assertThat(values).isEmpty();
		verify(sheetHelper, never()).readAudienceTables("sheet-url", Set.of(1), "token");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldCarryTheDecksHeadingAndGenderSplitOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and gender-split bars
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.AUDIENCE)));
		when(sheetHelper.readAudienceTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, AudienceTable.empty()));

		// When:
		Map<String, String> values = helper.buildAudienceValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 male}}", "42%", "{{tactic 1 female}}", "58%"),
				"brief", "token").values();

		// Then: all three are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 male}}")).isEqualTo("42%");
		assertThat(values.get("{{tactic 1 female}}")).isEqualTo("58%");
	}
}
