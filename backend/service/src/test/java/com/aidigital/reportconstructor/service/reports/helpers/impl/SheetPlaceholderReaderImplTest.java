package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetPlaceholderReaderImplTest {

	private final SheetPlaceholderReaderImpl reader = new SheetPlaceholderReaderImpl(new SheetRowHelperImpl());

	@Test
	void shouldReadTopInfoBlockByLabelAnchorsTest() {
		// Given: the info block with each label's value to its right and the RFP brief beneath its header
		List<List<String>> grid = List.of(
				List.of("Info", "", "", "RFP Input"),
				List.of("Client name:", "Acme", "", "Brief text here"),
				List.of("Campaign name:", "Spring Launch"),
				List.of("Flight dates:", "Jun 1 – Jun 30, 2026"),
				List.of("Tactics list:", "CTV, Display"),
				List.of("KPI:", "Reach & CTR"),
				List.of("Geo:", "Texas"),
				List.of("Funnel:", "Awareness"),
				List.of("Audience age:", "25-54"),
				List.of("Segments:", "Auto intenders"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: every info token resolves, including the below-label RFP value
		assertThat(out)
				.containsEntry("{{client_name}}", "Acme")
				.containsEntry("{{Campaign_name}}", "Spring Launch")
				.containsEntry("{{flight_dates}}", "Jun 1 – Jun 30, 2026")
				.containsEntry("{{tactics_list}}", "CTV, Display")
				.containsEntry("{{primary_kpis}}", "Reach & CTR")
				.containsEntry("{{geo_locations}}", "Texas")
				.containsEntry("{{funnel_stages}}", "Awareness")
				.containsEntry("{{audience_age}}", "25-54")
				.containsEntry("{{audience_segments}}", "Auto intenders")
				.containsEntry("{{RFP info}}", "Brief text here");
	}

	@Test
	void shouldReadSummaryTableTacticsAndTotalsTest() {
		// Given: a summary header row, two tactic rows, then a totals row
		List<List<String>> grid = List.of(
				List.of("Tactic name", "Benchmark", "KPI type", "KPI", "Impressions Fact", "Clicks Fact",
						"Spend Fact", "Reach", "Frequency", "Market Volume"),
				List.of("CTV", "0.5%", "VCR", "95%", "1,000,000", "2,000", "$10,000", "800,000", "1.3", "2,000,000"),
				List.of("Display", "0.1%", "CTR", "0.3%", "500,000", "1,000", "$5,000", "400,000", "1.1", "2,000,000"),
				List.of("Total", "", "", "", "1,500,000", "3,000", "$15,000", "1,200,000", "1.4", "2,000,000"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: per-tactic columns map by header, and the totals row feeds the campaign-level tokens
		assertThat(out)
				.containsEntry("{{tactic 1}}", "CTV")
				.containsEntry("{{tactic 1 – bench}}", "0.5%")
				.containsEntry("{{tactic 1 KPI type}}", "VCR")
				.containsEntry("{{tactic 1 imps}}", "1,000,000")
				.containsEntry("{{tactic 1 spend}}", "$10,000")
				.containsEntry("{{tactic 1 reach}}", "800,000")
				.containsEntry("{{tactic 1 f}}", "1.3")
				.containsEntry("{{tactic 1 volume}}", "2,000,000")
				.containsEntry("{{tactic 2}}", "Display")
				.containsEntry("{{tactic 2 clicks}}", "1,000")
				.containsEntry("{{total imps}}", "1,500,000")
				.containsEntry("{{total clicks}}", "3,000")
				.containsEntry("{{total_investment}}", "$15,000")
				.containsEntry("{{reach}}", "1,200,000")
				.containsEntry("{{reach_f}}", "1.4")
				.containsEntry("{{market volume}}", "2,000,000");
		// And: the totals row is not misread as a third tactic
		assertThat(out).doesNotContainKey("{{tactic 3}}");
	}

	@Test
	void shouldReadClicksAndCompletionsPlanColumnsWhenPresentTest() {
		// Given: a summary table whose header also carries the CPC/CPV plan columns; a tactic with no
		// plan for a given unit carries the em-dash a resolver writes for an unresolved figure
		List<List<String>> grid = List.of(
				List.of("Tactic name", "Clicks Plan", "Clicks Fact", "Completions Plan", "Completions Fact"),
				List.of("Paid Search", "5,000", "4,800", "—", "—"),
				List.of("YouTube", "—", "—", "80,000", "76,500"),
				List.of("Total", "5,000", "4,800", "80,000", "76,500"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: each tactic's own plan/fact column is captured, independent of the other tactic's dash
		assertThat(out)
				.containsEntry("{{tactic 1 clicks plan}}", "5,000")
				.containsEntry("{{tactic 1 clicks}}", "4,800")
				.containsEntry("{{tactic 1 completions plan}}", "—")
				.containsEntry("{{tactic 2 completions plan}}", "80,000")
				.containsEntry("{{tactic 2 complitions}}", "76,500")
				.containsEntry("{{tactic 2 clicks plan}}", "—");
	}

	@Test
	void shouldReadEomUnitRateAndRateTypeColumnsUnderTheirOwnTokenNamesTest() {
		// Given: the EOM summary table, whose "Unit rate"/"Rate type" columns sit between the KPI and
		// fact columns
		List<List<String>> grid = List.of(
				List.of("Tactic name", "KPI type", "Unit rate", "Rate type", "Impressions Fact"),
				List.of("Programmatic Display", "CTR", "6.00", "CPM", "251,633"),
				List.of("Programmatic Video", "VCR", "12.00", "CPM", "124,900"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: both are emitted under their own token names, and the columns around them keep theirs
		assertThat(out)
				.containsEntry("{{unit 1 rate}}", "6.00")
				.containsEntry("{{unit 2 rate}}", "12.00")
				.containsEntry("{{rate type 1}}", "CPM")
				.containsEntry("{{rate type 2}}", "CPM")
				.containsEntry("{{tactic 1 KPI type}}", "CTR")
				.containsEntry("{{tactic 2 imps}}", "124,900");
	}

	@Test
	void shouldTreatDashFilledSummaryRowsAsUnusedTacticsTest() {
		// Given: two real tactics followed by the template's dash-filled unused rows, then totals
		List<List<String>> grid = List.of(
				List.of("Tactic name", "Benchmark", "Impressions Fact", "Spend Fact"),
				List.of("Display", "0.17%", "251,633", "$1,500"),
				List.of("Video", "60%", "125,219", "$1,500"),
				List.of("—", "—", "—", "—"),
				List.of("—", "—", "—", "—"),
				List.of("-", "-", "-", "-"),
				List.of("Total", "", "376,852", "$3,000"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: only the two real tactics are emitted; the dash rows are not counted as tactics
		assertThat(out)
				.containsEntry("{{tactic 1}}", "Display")
				.containsEntry("{{tactic 2}}", "Video")
				.doesNotContainKey("{{tactic 3}}")
				.doesNotContainKey("{{tactic 4}}")
				.doesNotContainKey("{{tactic 5}}");
		// And: the totals row is still read through, past the dash rows
		assertThat(out).containsEntry("{{total imps}}", "376,852");
	}

	@Test
	void shouldReadMainSlideBlocksByExplicitNumberAsTacticsTest() {
		// Given: two "Main slide N" blocks stacked vertically, each anchored by its numbered cell
		List<List<String>> grid = List.of(
				List.of("Main slide 1"),
				List.of("Tactic Goal", "Drive reach"),
				List.of("Weekdays", "60%"),
				List.of("Weekends", "40%"),
				List.of("Male", "48%"),
				List.of("Female", "52%"),
				List.of("Creative Name:", "Hero15"),
				List.of("Impressions:", "600,000"),
				List.of("Clicks:", "1,200"),
				List.of("Main slide 2"),
				List.of("Tactic Goal", "Drive clicks"),
				List.of("Weekends", "45%"),
				List.of("Creative Name:", "Banner A"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: each block maps to the tactic number in its anchor cell
		assertThat(out)
				.containsEntry("{{tactic 1 goal}}", "Drive reach")
				.containsEntry("{{tactic 1 weekdays}}", "60%")
				.containsEntry("{{tactic 1 male}}", "48%")
				.containsEntry("{{tactic 1 female}}", "52%")
				.containsEntry("{{tactic 1 top creative name}}", "Hero15")
				.containsEntry("{{tactic 1 top creative imps}}", "600,000")
				.containsEntry("{{tactic 1 top creative clicks}}", "1,200")
				.containsEntry("{{tactic 2 goal}}", "Drive clicks")
				.containsEntry("{{tactic 2 weekends}}", "45%")
				.containsEntry("{{tactic 2 top creative name}}", "Banner A");
	}

	@Test
	void shouldSurviveRowsAndColumnsInsertedByUserTest() {
		// Given: the same content shifted down two rows and right one column (as if the user inserted them)
		List<List<String>> base = List.of(
				List.of("Client name:", "Acme"),
				List.of("Tactic name", "Benchmark", "Spend Fact", "Reach"),
				List.of("CTV", "0.5%", "$10,000", "800,000"),
				List.of("Total", "", "$15,000", "1,200,000"),
				List.of("Main slide 1"),
				List.of("Tactic Goal", "Drive reach"));
		List<List<String>> shifted = new ArrayList<>();
		shifted.add(List.of());
		shifted.add(List.of());
		for (List<String> row : base) {
			List<String> shiftedRow = new ArrayList<>();
			shiftedRow.add("");
			shiftedRow.addAll(row);
			shifted.add(shiftedRow);
		}

		// When: the shifted grid is read
		Map<String, String> out = reader.readPlaceholders(shifted);

		// Then: label/header anchoring resolves values regardless of the inserted rows and column
		assertThat(out)
				.containsEntry("{{client_name}}", "Acme")
				.containsEntry("{{tactic 1}}", "CTV")
				.containsEntry("{{tactic 1 spend}}", "$10,000")
				.containsEntry("{{total_investment}}", "$15,000")
				.containsEntry("{{reach}}", "1,200,000")
				.containsEntry("{{tactic 1 goal}}", "Drive reach");
	}

	@Test
	void shouldSkipUnreplacedTemplateTokensTest() {
		// Given: an unfilled tactic slot whose cells still hold their literal template tokens
		List<List<String>> grid = List.of(
				List.of("Tactic name", "Spend Fact"),
				List.of("CTV", "$10,000"),
				List.of("{{tactic 2}}", "{{tactic 2 spend}}"),
				List.of("Total", "$10,000"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: the real tactic is read but the unreplaced-token row contributes nothing
		assertThat(out)
				.containsEntry("{{tactic 1}}", "CTV")
				.containsEntry("{{tactic 1 spend}}", "$10,000")
				.doesNotContainKey("{{tactic 2 spend}}");
	}
}
