package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EomPacingResolverTest {

	private final EomPacingResolver resolver = new EomPacingResolver(new ReportNumberParserImpl(), new Fmt());

	/**
	 * Builds the summary-table values a two-tactic workbook carries, in the exact display formatting the
	 * sheet reader hands over.
	 *
	 * @return the placeholder map the dashboard is derived from
	 */
	private Map<String, String> twoTacticSheet() {
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 spend plan}}", "$11,812.50");
		flat.put("{{tactic 1 spend}}", "$12,167.88");
		flat.put("{{tactic 1 imps plan}}", "1,575,000");
		flat.put("{{tactic 1 imps}}", "1,602,341");
		flat.put("{{tactic 2 spend plan}}", "$8,000");
		flat.put("{{tactic 2 spend}}", "$7,200");
		flat.put("{{tactic 2 imps plan}}", "1,000,000");
		flat.put("{{tactic 2 imps}}", "890,000");
		return flat;
	}

	@Test
	void fill_shouldCopyTheSummaryFiguresOntoTheDashboardTokensTest() {
		// Given: a reviewed workbook's summary table
		Map<String, String> flat = twoTacticSheet();

		// When:
		resolver.fill(flat, 2);

		// Then: each dashboard column prints the sheet's own value, character for character
		assertThat(flat.get("{{tactic 1 planned budget}}")).isEqualTo("$11,812.50");
		assertThat(flat.get("{{tactic 1 fact budget}}")).isEqualTo("$12,167.88");
		assertThat(flat.get("{{tactic 1 planned imps}}")).isEqualTo("1,575,000");
		assertThat(flat.get("{{tactic 1 fact imps}}")).isEqualTo("1,602,341");
		assertThat(flat.get("{{tactic 2 planned budget}}")).isEqualTo("$8,000");
	}

	@Test
	void fill_shouldComputePacingAsActualOverPlannedSpendTest() {
		// Given: one tactic over its planned spend and one under it
		Map<String, String> flat = twoTacticSheet();

		// When:
		resolver.fill(flat, 2);

		// Then: the pacing column is a whole percentage, over-delivery included
		assertThat(flat.get("{{tactic 1 pacing}}")).isEqualTo("103%");
		assertThat(flat.get("{{tactic 2 pacing}}")).isEqualTo("90%");
	}

	@Test
	void fill_shouldPreferTheWorkbookTotalsRowOverTheSumOfTheTacticRowsTest() {
		// Given: a workbook whose totals row is carried explicitly
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{total_investment_plan}}", "$19,812.50");
		flat.put("{{total_investment}}", "$19,367.88");
		flat.put("{{total imps plan}}", "2,575,000");
		flat.put("{{total imps}}", "2,492,341");

		// When:
		resolver.fill(flat, 2);

		// Then: the totals row is the sheet's, and its pacing is computed from it
		assertThat(flat.get("{{total planned budget}}")).isEqualTo("$19,812.50");
		assertThat(flat.get("{{total fact budget}}")).isEqualTo("$19,367.88");
		assertThat(flat.get("{{total planned imps}}")).isEqualTo("2,575,000");
		assertThat(flat.get("{{total fact imps}}")).isEqualTo("2,492,341");
		assertThat(flat.get("{{total pacing}}")).isEqualTo("98%");
	}

	@Test
	void fill_shouldSumTheTacticRowsWhenTheWorkbookCarriesNoTotalsRowTest() {
		// Given: a workbook with no totals tokens at all
		Map<String, String> flat = twoTacticSheet();

		// When:
		resolver.fill(flat, 2);

		// Then: the totals row is summed from the tactic rows and formatted like every other figure
		assertThat(flat.get("{{total planned budget}}")).isEqualTo("$19,812.50");
		assertThat(flat.get("{{total fact budget}}")).isEqualTo("$19,367.88");
		assertThat(flat.get("{{total planned imps}}")).isEqualTo("2,575,000");
		assertThat(flat.get("{{total fact imps}}")).isEqualTo("2,492,341");
	}

	@Test
	void fill_shouldDashAFigureTheSheetNeverCarriedAndItsPacingTest() {
		// Given: a tactic row whose planned spend was left empty and one that was dashed out
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 spend}}", "$500");
		flat.put("{{tactic 1 imps plan}}", "—");

		// When:
		resolver.fill(flat, 1);

		// Then: the empty slots print a dash, and pacing with no plan to divide by does too
		assertThat(flat.get("{{tactic 1 planned budget}}")).isEqualTo("—");
		assertThat(flat.get("{{tactic 1 planned imps}}")).isEqualTo("—");
		assertThat(flat.get("{{tactic 1 fact budget}}")).isEqualTo("$500");
		assertThat(flat.get("{{tactic 1 pacing}}")).isEqualTo("—");
	}

	@Test
	void fill_shouldLeaveTheSlotsAboveTheTacticCountAloneTest() {
		// Given: a workbook carrying a third tactic the campaign no longer has
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{tactic 3 spend plan}}", "$1,000");

		// When: only two tactics are real
		resolver.fill(flat, 2);

		// Then: the surplus row is never filled — its dashboard row is deleted during the deck trim
		assertThat(flat).doesNotContainKey("{{tactic 3 planned budget}}");
	}
}
