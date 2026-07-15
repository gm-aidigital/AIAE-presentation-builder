package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublisherBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;
	@Mock
	ClaudeClient claude;

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
		when(claude.batchPublisherObservations(
				List.of(new PublisherObservationInput(1, "CTV", rows)), "brief"))
				.thenReturn(Map.of(1, List.of("a", "b", "c", "d")));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token");

		// Then: the filled rows are carried across exactly as typed
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
	void shouldNotAskClaudeWhenTheTacticsPublisherTableIsEmptyTest() {
		// Given: tactic 1 enabled Top Publishers but never filled the table in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, List.of()));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of(), "brief", "token");

		// Then: Claude is never asked — there is nothing to observe and any copy would be invented
		verifyNoInteractions(claude);

		// Then: the slide still gets its tokens, with blank observations rather than raw tokens
		assertThat(values.get("{{publishers_observation_1_1}}")).isEmpty();
		assertThat(values.get("{{publishers_observation_1_4}}")).isEmpty();
		assertThat(values.get("{{publisher_1.1}}")).isEqualTo("—");
	}

	@Test
	void shouldSendOnlyTacticsWithRowsToClaudeAndCarryTheirNamesTest() {
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
		when(claude.batchPublisherObservations(
				List.of(new PublisherObservationInput(1, "CTV", rows)), "brief"))
				.thenReturn(Map.of(1, List.of("one", "two", "three", "four")));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"), "brief", "token");

		// Then: only tactic 1 is sent, carrying its deck name
		ArgumentCaptor<List<PublisherObservationInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchPublisherObservations(captor.capture(), eq("brief"));
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst().tacticNum()).isEqualTo(1);
		assertThat(captor.getValue().getFirst().tacticName()).isEqualTo("CTV");

		// Then: tactic 1 gets its bullets and tactic 2's are blanked
		assertThat(values.get("{{publishers_observation_1_1}}")).isEqualTo("one");
		assertThat(values.get("{{publishers_observation_2_1}}")).isEmpty();
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledTopPublishersTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of(), "brief", "token");

		// Then: the sheet is never read and no values are produced
		assertThat(values).isEmpty();
		verify(sheetHelper, never()).readPublisherTables("sheet-url", Set.of(1), "token");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldCarryTheDecksResolvedHeadingAndTotalOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading and total impressions
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, List.of()));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 imps}}", "4,600,000"), "brief", "token");

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 imps}}")).isEqualTo("4,600,000");
	}
}
