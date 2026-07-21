package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
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
class PublisherBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;

	@InjectMocks
	PublisherBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetRowsVerbatimAndDashTheSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 enabled Top Publishers and the user filled only 2 of the slide's 15 rows
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(
				new PublisherRow("YouTube", "1,200,000", "26%"),
				new PublisherRow("Hulu", "800,000", "17%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, rows));

		// When: the data-only read runs (no Claude call)
		BreakdownSectionInputs<PublisherObservationInput> read = helper.readPublisherInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "token");

		// Then: the filled rows are carried across exactly as typed
		Map<String, String> values = read.dataValues();
		assertThat(values.get("{{publisher_1.1}}")).isEqualTo("YouTube");
		assertThat(values.get("{{pub_imp_1.1}}")).isEqualTo("1,200,000");
		assertThat(values.get("{{pub_sov_1.1}}")).isEqualTo("26%");
		assertThat(values.get("{{publisher_1.2}}")).isEqualTo("Hulu");

		// Then: every remaining slot is dashed, so none can ship as a raw token
		assertThat(values.get("{{publisher_1.3}}")).isEqualTo("—");
		assertThat(values.get("{{pub_imp_1.15}}")).isEqualTo("—");
		assertThat(values.get("{{pub_sov_1.15}}")).isEqualTo("—");
	}

	@Test
	void shouldCutPublisherNamesAtTheFirstSeparatorForDisplayTest() {
		// Given: exported names carrying a descriptive tail and a platform/bundle-id suffix, alongside bare
		// domains that carry no separator at all
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(
				new PublisherRow("Chai - Chat with AI bots - iOS (1544750895)", "25,534", "10.15%"),
				new PublisherRow("mail.yahoo.com", "2,950", "1.17%"),
				new PublisherRow("dailymotion.com", "2,281", "0.91%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, rows));

		// When:
		Map<String, String> values = helper.readPublisherInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Display"), "token").dataValues();

		// Then: the name is cut at the FIRST separator, dropping both the tail and the bundle id
		assertThat(values.get("{{publisher_1.1}}")).isEqualTo("Chai");

		// Then: names without a separator are left exactly as typed
		assertThat(values.get("{{publisher_1.2}}")).isEqualTo("mail.yahoo.com");
		assertThat(values.get("{{publisher_1.3}}")).isEqualTo("dailymotion.com");

		// Then: impressions and share of voice are untouched by the name cut
		assertThat(values.get("{{pub_imp_1.1}}")).isEqualTo("25,534");
		assertThat(values.get("{{pub_sov_1.1}}")).isEqualTo("10.15%");
	}

	@Test
	void shouldCarryTheFullPublisherNamesIntoTheClaudeInputNotTheShortenedOnesTest() {
		// Given: two listings of the same app that differ only past the separator — the distinction the
		// combined call needs to reason about the platform mix, and the one the slide's short names throw away
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(
				new PublisherRow("Chai - Chat with AI bots - iOS (1544750895)", "25,534", "10.15%"),
				new PublisherRow("Chai - Chat with AI Friends - Android (com.Beauchamp.Messenger)", "3,493", "1.39%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, rows));

		// When:
		BreakdownSectionInputs<PublisherObservationInput> read = helper.readPublisherInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Display"), "token");

		// Then: the tactic's input carries the rows verbatim, platform suffixes intact
		assertThat(read.inputs().get(1).rows()).isEqualTo(rows);

		// Then: the slide still collapses both to the same short label
		Map<String, String> values = read.dataValues();
		assertThat(values.get("{{publisher_1.1}}")).isEqualTo("Chai");
		assertThat(values.get("{{publisher_1.2}}")).isEqualTo("Chai");
	}

	@Test
	void shouldBuildAnInputOnlyForTacticsWithRowsAndCarryTheirNamesTest() {
		// Given: tactic 1 has rows, tactic 2 enabled the toggle but left its table empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("tp")), new BreakdownSelection(2, List.of("tp")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.TOP_PUBLISHERS),
				2, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(new PublisherRow("YouTube", "1,200,000", "26%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, rows,
				2, List.of()));

		// When:
		BreakdownSectionInputs<PublisherObservationInput> read = helper.readPublisherInputs(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"), "token");

		// Then: both tactics are enabled, but only tactic 1 becomes a Claude input, carrying its deck name
		assertThat(read.tactics()).containsExactlyInAnyOrder(1, 2);
		assertThat(read.inputs().keySet()).containsExactly(1);
		assertThat(read.inputs().get(1).tacticNum()).isEqualTo(1);
		assertThat(read.inputs().get(1).tacticName()).isEqualTo("CTV");

		// Then: tactic 2 still gets its dashed data tokens even though it is never sent
		assertThat(read.dataValues().get("{{publisher_2.1}}")).isEqualTo("—");
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledTopPublishersTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));

		// When:
		BreakdownSectionInputs<PublisherObservationInput> read = helper.readPublisherInputs(
				"sheet-url", selections, Map.of(), "token");

		// Then: the sheet is never read and no inputs are produced
		assertThat(read.tactics()).isEmpty();
		assertThat(read.inputs()).isEmpty();
		assertThat(read.dataValues()).isEmpty();
		verify(sheetHelper, never()).readPublisherTables("sheet-url", Set.of(1), "token");
	}

	@Test
	void shouldCarryTheDecksResolvedHeadingAndTotalOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and total impressions
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, List.of()));

		// When:
		Map<String, String> values = helper.readPublisherInputs(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 imps}}", "4,600,000"), "token").dataValues();

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 imps}}")).isEqualTo("4,600,000");
	}

	@Test
	void shouldWriteBulletsForAnsweredTacticsAndBlankTheOthersTest() {
		// Given: tactic 1 was sent and answered, tactic 2 was sent and came back empty
		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, List<String>> observations = Map.of(1, List.of("one", "two", "three", "four"));

		// When:
		List<String> warnings = helper.writePublisherObservations(
				values, Set.of(1, 2), Set.of(1, 2), observations, Map.of("{{tactic 2}}", "Display"));

		// Then: tactic 1's bullets are written in slide order
		assertThat(values.get("{{publishers_observation_1_1}}")).isEqualTo("one");
		assertThat(values.get("{{publishers_observation_1_4}}")).isEqualTo("four");

		// Then: tactic 2's bullets are blanked rather than left as raw tokens
		assertThat(values.get("{{publishers_observation_2_1}}")).isEmpty();

		// Then: a sent tactic that came back with nothing is reported to the user by name
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("Top Publishers", "Display", "KEY OBSERVATIONS");
	}

	@Test
	void shouldNotWarnWhenTheTableWasEmptyAndTheTacticWasNeverSentTest() {
		// Given: a tactic that enabled the breakdown but was never sent (its table was blank) — blank bullets
		// are the user's own doing here, so warning about them would be noise
		Map<String, String> values = new LinkedHashMap<>();

		// When:
		List<String> warnings = helper.writePublisherObservations(
				values, Set.of(1), Set.of(), Map.of(), Map.of("{{tactic 1}}", "Video"));

		// Then: no warning, and the bullets still render blank rather than as raw tokens
		assertThat(warnings).isEmpty();
		assertThat(values.get("{{publishers_observation_1_1}}")).isEmpty();
		assertThat(values.get("{{publishers_observation_1_4}}")).isEmpty();
	}
}
