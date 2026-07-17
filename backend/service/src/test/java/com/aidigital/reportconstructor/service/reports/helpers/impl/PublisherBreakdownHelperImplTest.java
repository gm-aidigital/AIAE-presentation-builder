package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
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
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

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
		when(claude.batchPublisherObservations(
				List.of(new PublisherObservationInput(1, "Display", rows)), "brief"))
				.thenReturn(Map.of(1, List.of("a", "b", "c", "d")));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Display"), "brief", "token").values();

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
	void shouldSendClaudeTheFullPublisherNamesNotTheShortenedOnesTest() {
		// Given: two listings of the same app that differ only past the separator — the distinction Claude
		// needs to reason about the platform mix, and the one the slide's shortened names throw away
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(
				new PublisherRow("Chai - Chat with AI bots - iOS (1544750895)", "25,534", "10.15%"),
				new PublisherRow("Chai - Chat with AI Friends - Android (com.Beauchamp.Messenger)", "3,493", "1.39%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, rows));
		when(claude.batchPublisherObservations(
				List.of(new PublisherObservationInput(1, "Display", rows)), "brief"))
				.thenReturn(Map.of(1, List.of("a", "b", "c", "d")));

		// When:
		Map<String, String> values = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Display"), "brief", "token").values();

		// Then: Claude was handed the rows verbatim, platform suffixes intact
		ArgumentCaptor<List<PublisherObservationInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchPublisherObservations(captor.capture(), eq("brief"));
		assertThat(captor.getValue().getFirst().rows()).isEqualTo(rows);

		// Then: the slide still collapses both to the same short label
		assertThat(values.get("{{publisher_1.1}}")).isEqualTo("Chai");
		assertThat(values.get("{{publisher_1.2}}")).isEqualTo("Chai");
	}

	@Test
	void shouldWarnWhenAFilledTableCameBackWithoutObservationsTest() {
		// Given: a tactic whose table Claude was asked about, but that came back with nothing — on the slide
		// the empty KEY OBSERVATIONS box is indistinguishable from a table the user never filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		List<PublisherRow> rows = List.of(new PublisherRow("modrinth.com", "19,674", "15.71%"));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, rows));
		when(claude.batchPublisherObservations(
				List.of(new PublisherObservationInput(1, "Video", rows)), "brief"))
				.thenReturn(Map.of());

		// When:
		BreakdownValues result = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Video"), "brief", "token");

		// Then: the failure is reported to the user by tactic name rather than only to the log
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Top Publishers", "Video", "KEY OBSERVATIONS");

		// Then: the table still ships, with blank bullets rather than raw tokens
		assertThat(result.values().get("{{publisher_1.1}}")).isEqualTo("modrinth.com");
		assertThat(result.values().get("{{publishers_observation_1_1}}")).isEmpty();
	}

	@Test
	void shouldNotWarnWhenTheTableWasEmptyAndClaudeWasNeverAskedTest() {
		// Given: a tactic that enabled the breakdown but left its table blank — blank bullets are the
		// user's own doing here, so warning about them would be noise
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		when(breakdownResolver.resolve(selections))
				.thenReturn(Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS)));
		when(sheetHelper.readPublisherTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, List.of()));

		// When:
		BreakdownValues result = helper.buildPublisherValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "Video"), "brief", "token");

		// Then:
		assertThat(result.warnings()).isEmpty();
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
				"sheet-url", selections, Map.of(), "brief", "token").values();

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
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"), "brief", "token").values();

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
				"sheet-url", selections, Map.of(), "brief", "token").values();

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
				Map.of("{{tactic 1}}", "CTV", "{{tactic 1 imps}}", "4,600,000"), "brief", "token").values();

		// Then: both are re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(values.get("{{tactic 1 imps}}")).isEqualTo("4,600,000");
	}
}
