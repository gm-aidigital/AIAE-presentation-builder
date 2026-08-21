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
	void shouldReadTheNorthStarChannelListsBackFromTheInfoBlockTest() {
		// Given: the EOM Info block, where the three channel lists sit in the second label column and the
		// template writes them without a trailing colon
		List<List<String>> grid = List.of(
				List.of("Client name:", "Acme", "Reporting dates", "Jun 1 – Jun 30, 2026"),
				List.of("KPI:", "Reach & CTR", "Awareness channels", "CTV, Online Video"),
				List.of("Geo:", "Texas", "Consideration channels", "Display"),
				List.of("Funnel:", "Awareness", "Conversions channels", "—"));

		// When:
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: each list comes back under its deck token, so an edit in the sheet reaches the slide
		assertThat(out)
				.containsEntry("{{awareness channels}}", "CTV, Online Video")
				.containsEntry("{{consideration channels}}", "Display")
				.containsEntry("{{conversions channels}}", "—");
	}

	@Test
	void shouldReadChangeLogFromEitherLayoutTest() {
		// Given: the real EOC/EOM info-block shape — "Change log" is a merged H1:J1 header with the text in the
		// merged block beneath it, so the API returns the header only in H1 and the covered cells come back
		// empty (this is the same mechanic the "RFP Input" block already relies on). The second sheet is the
		// label-beside-value spelling, tolerated because a miss here is silent: the slides step would simply
		// run without the change log.
		List<List<String>> below = List.of(
				List.of("Info", "", "", "RFP Input", "", "", "", "Change log", "", ""),
				List.of("Client name:", "Acme", "", "Brief text.", "", "", "", "Shifted budget to CTV on Jul 3."));
		List<List<String>> beside = List.of(
				List.of("Change Log:", "Paused Native on Aug 1."));

		// When:
		Map<String, String> fromBelow = reader.readPlaceholders(below);
		Map<String, String> fromBeside = reader.readPlaceholders(beside);

		// Then: the slides step reads back the same change-log text the sheet step wrote, and the neighbouring
		// RFP block is unaffected
		assertThat(fromBelow)
				.containsEntry("{{change log}}", "Shifted budget to CTV on Jul 3.")
				.containsEntry("{{RFP info}}", "Brief text.");
		assertThat(fromBeside).containsEntry("{{change log}}", "Paused Native on Aug 1.");
	}

	@Test
	void shouldNotEmitChangeLogWhenTheSheetCarriesNoneTest() {
		// Given: a sheet with an empty change-log block — the user left the field blank
		List<List<String>> grid = List.of(
				List.of("Change log", ""),
				List.of("", ""));

		// When:
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: no entry at all, so the generation step falls back to the payload rather than to an empty string
		assertThat(out).doesNotContainKey("{{change log}}");
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

	@Test
	void shouldReadTheEomCoverCadenceFromTheInfoBlockTest() {
		// Given: the Info block's second label/value pair, written without trailing colons
		List<List<String>> grid = List.of(
				List.of("Client name:", "Acme", "Reporting dates", "Aug 1 - Aug 31, 2026"),
				List.of("Campaign name:", "Q3 Push", "Reporting month", "August 2026"),
				List.of("Flight dates:", "Oct 1, 2025 - Dec 31, 2025", "Campaign duration (months)", "3"),
				List.of("Tactics list:", "CTV, Display", "Reporting month no.", "2"));

		// When:
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: both columns of the block are read, and the flight and the reporting window stay distinct
		assertThat(out).containsEntry("{{reporting filter}}", "Aug 1 - Aug 31, 2026");
		assertThat(out).containsEntry("{{reporting month}}", "August 2026");
		assertThat(out).containsEntry("{{total mon no}}", "3");
		assertThat(out).containsEntry("{{mon no}}", "2");
		assertThat(out).containsEntry("{{flight_dates}}", "Oct 1, 2025 - Dec 31, 2025");
	}

	@Test
	void shouldReadTheMetricTableUnderTheChannelSlideTokensTest() {
		// Given: one tactic's workbook band — the "Main slide" detail block that numbers it, and beneath it
		// the metric table that faces the deck's channel slide, with the reporting month in two headers
		List<List<String>> grid = List.of(
				List.of("CTV", "", "", "", "", ""),
				List.of("Main slide 3", "", "", "", "", ""),
				List.of("Tactic Goal", "Awareness", "", "", "", ""),
				List.of("", "", "", "", "", ""),
				List.of("CTV", "", "", "", "", ""),
				List.of("METRIC", "MONTH 2  GOAL", "MONTH 2 ACTUAL", "VS GOAL", "EOC GOAL", "EOC PROJ."),
				List.of("Impressions", "500,000", "512,300", "+2.5%", "1,500,000", "1,536,900"),
				List.of("CTR", "0.12%", "0.15%", "+25%", "0.12%", "0.14%"),
				List.of("Clicks", "600", "769", "+28%", "1,800", "2,300"),
				List.of("Reach", "120,000", "131,000", "+9%", "360,000", "393,000"),
				List.of("CPM", "$12.00", "$11.70", "-2.5%", "$12.00", "$11.80"),
				List.of("Spend", "$6,000", "$5,994", "-0.1%", "$18,000", "$18,135"));

		// When: the placeholders are read
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: every cell comes back under the token the channel slide prints, for the block's own tactic
		assertThat(out)
				.containsEntry("{{tactic 3 planned imps}}", "500,000")
				.containsEntry("{{tactic 3 fact imps}}", "512,300")
				.containsEntry("{{tactic 3 imps pacing}}", "+2.5%")
				.containsEntry("{{tactic 3 eoc planned imps}}", "1,500,000")
				.containsEntry("{{tactic 3 proj imps}}", "1,536,900")
				.containsEntry("{{tactic 3 ctr plan}}", "0.12%")
				.containsEntry("{{tactic 3 ctr}}", "0.15%")
				.containsEntry("{{tactic 3 ctr pacing}}", "+25%")
				.containsEntry("{{tactic 3 ctr proj}}", "0.14%")
				.containsEntry("{{tactic 3 clicks plan}}", "600")
				.containsEntry("{{tactic 3 clicks}}", "769")
				.containsEntry("{{tactic 3 clicks pacing}}", "+28%")
				.containsEntry("{{tactic 3 clicks mp}}", "1,800")
				.containsEntry("{{tactic 3 clicks proj}}", "2,300")
				.containsEntry("{{tactic 3 reach plan}}", "120,000")
				.containsEntry("{{tactic 3 reach}}", "131,000")
				.containsEntry("{{tactic 3 reach pacing}}", "+9%")
				.containsEntry("{{tactic 3 reach plan eoc}}", "360,000")
				.containsEntry("{{tactic 3 reach proj}}", "393,000")
				.containsEntry("{{tactic 3 planned cpm}}", "$12.00")
				.containsEntry("{{tactic 3 fact cpm}}", "$11.70")
				.containsEntry("{{tactic 3 cpm pacing}}", "-2.5%")
				.containsEntry("{{tactic 3 cpm proj}}", "$11.80")
				.containsEntry("{{tactic 3 planned budget}}", "$6,000")
				.containsEntry("{{tactic 3 fact budget}}", "$5,994")
				.containsEntry("{{tactic 3 budget pacing}}", "-0.1%")
				.containsEntry("{{tactic 3 spend plan eoc}}", "$18,000")
				.containsEntry("{{tactic 3 spend proj}}", "$18,135");
	}

	@Test
	void shouldKeepSummaryTableFiguresOverRepeatedMetricTableCellsTest() {
		// Given: a summary table and, below it, a metric table repeating six of the same tokens — the
		// workbook fills both from the same resolver, so they differ only when a user edits one of them
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("Tactic name", "CTR Fact", "CTR Plan", "Clicks Fact", "Reach", "Spend Plan"));
		grid.add(List.of("CTV", "0.15%", "0.12%", "769", "131,000", "$18,000"));
		grid.add(List.of("Total", "", "", "", "", ""));
		grid.add(List.of("Main slide 1", "", "", "", "", ""));
		grid.add(List.of("METRIC", "MONTH 2  GOAL", "MONTH 2 ACTUAL", "VS GOAL", "EOC GOAL", "EOC PROJ."));
		grid.add(List.of("CTR", "0.99%", "0.99%", "+25%", "0.99%", "0.14%"));
		grid.add(List.of("Clicks", "600", "999", "+28%", "1,800", "2,300"));
		grid.add(List.of("Reach", "120,000", "999,000", "+9%", "360,000", "393,000"));
		grid.add(List.of("Spend", "$6,000", "$5,994", "-0.1%", "$99,000", "$18,135"));

		// When:
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: the shared tokens keep the summary table's figures, while the tokens only the metric table
		// carries still come through
		assertThat(out)
				.containsEntry("{{tactic 1 ctr}}", "0.15%")
				.containsEntry("{{tactic 1 ctr plan}}", "0.12%")
				.containsEntry("{{tactic 1 clicks}}", "769")
				.containsEntry("{{tactic 1 reach}}", "131,000")
				.containsEntry("{{tactic 1 spend plan}}", "$18,000")
				.containsEntry("{{tactic 1 spend plan eoc}}", "$99,000")
				.containsEntry("{{tactic 1 clicks pacing}}", "+28%")
				.containsEntry("{{tactic 1 reach plan eoc}}", "360,000");
	}

	@Test
	void shouldIgnoreAMetricTableWithNoDetailBlockAboveItTest() {
		// Given: a metric table that no "Main slide N" anchor numbers — the workbook shape a stray copy of
		// the block would have, and nothing tells which tactic it belongs to
		List<List<String>> grid = List.of(
				List.of("METRIC", "MONTH 2  GOAL", "MONTH 2 ACTUAL", "VS GOAL", "EOC GOAL", "EOC PROJ."),
				List.of("Impressions", "500,000", "512,300", "+2.5%", "1,500,000", "1,536,900"));

		// When:
		Map<String, String> out = reader.readPlaceholders(grid);

		// Then: nothing is emitted rather than guessed onto tactic 1
		assertThat(out).isEmpty();
	}
}
