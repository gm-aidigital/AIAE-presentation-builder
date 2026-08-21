package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EomDashboardResolverTest {

	private final EomDashboardResolver resolver = new EomDashboardResolver(new ReportNumberParserImpl(), new Fmt(), new RatePlanCalculator());

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

	/**
	 * Builds the KPI values a two-tactic workbook carries — one click-led tactic, one video tactic — in the
	 * display formatting the sheet reader hands over.
	 *
	 * @return the placeholder map the performance dashboard is derived from
	 */
	private Map<String, String> twoTacticKpis() {
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "CTR");
		flat.put("{{tactic 1 KPI}}", "0.35%");
		flat.put("{{tactic 1 ctr plan}}", "0.25%");
		flat.put("{{tactic 1 vcr plan}}", "60.0%");
		flat.put("{{tactic 2 KPI type}}", "VCR");
		flat.put("{{tactic 2 KPI}}", "80.0%");
		flat.put("{{tactic 2 vcr plan}}", "60.0%");
		return flat;
	}

	@Test
	void fill_shouldTakeTheKpiGoalFromTheRateTheTacticIsJudgedOnTest() {
		// Given: a click-led tactic and a video tactic, both carrying a planned completion rate
		Map<String, String> flat = twoTacticKpis();

		// When:
		resolver.fill(flat, 2);

		// Then: the goal column follows the KPI type, not whichever plan the row happens to carry
		assertThat(flat.get("{{tactic 1 KPI goal}}")).isEqualTo("0.25%");
		assertThat(flat.get("{{tactic 2 KPI goal}}")).isEqualTo("60.0%");
	}

	@Test
	void fill_shouldStateTheDistanceFromTheGoalInPercentagePointsTest() {
		// Given: both tactics running above their goal rate
		Map<String, String> flat = twoTacticKpis();

		// When:
		resolver.fill(flat, 2);

		// Then: the gap is a signed percentage-point delta, not a ratio, and drops trailing zeros
		assertThat(flat.get("{{tactic 1 vs goal}}")).isEqualTo("+0.1pp");
		assertThat(flat.get("{{tactic 2 vs goal}}")).isEqualTo("+20pp");
	}

	@Test
	void fill_shouldSignTheGapDownwardsAndFlattenAGapThatRoundsAwayTest() {
		// Given: one tactic below its goal and one sitting exactly on it
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "CTR");
		flat.put("{{tactic 1 KPI}}", "0.18%");
		flat.put("{{tactic 1 ctr plan}}", "0.25%");
		flat.put("{{tactic 2 KPI type}}", "ACR");
		flat.put("{{tactic 2 KPI}}", "72.5%");
		flat.put("{{tactic 2 vcr plan}}", "72.5%");

		// When:
		resolver.fill(flat, 2);

		// Then: under-delivery carries its own minus sign, and no gap prints as an unsigned zero
		assertThat(flat.get("{{tactic 1 vs goal}}")).isEqualTo("-0.07pp");
		assertThat(flat.get("{{tactic 2 vs goal}}")).isEqualTo("0pp");
	}

	@Test
	void fill_shouldReadTheAudioCompletionRateAsACompletionRateGoalTest() {
		// Given: an audio tactic, whose KPI the deck spells ACR rather than VCR
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 KPI type}}", "ACR");
		flat.put("{{tactic 1 KPI}}", "91.0%");
		flat.put("{{tactic 1 vcr plan}}", "88.0%");

		// When:
		resolver.fill(flat, 1);

		// Then: the goal comes from the planned completion rate all the same
		assertThat(flat.get("{{tactic 1 KPI goal}}")).isEqualTo("88.0%");
		assertThat(flat.get("{{tactic 1 vs goal}}")).isEqualTo("+3pp");
	}

	@Test
	void fill_shouldDashTheKpiColumnsWhenTheTacticHasNoKpiTypeTest() {
		// Given: a tactic whose KPI type the workbook never carried
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1 ctr plan}}", "0.25%");
		flat.put("{{tactic 1 KPI}}", "0.35%");

		// When:
		resolver.fill(flat, 1);

		// Then: nothing says which rate the row is about, so both columns dash rather than guess
		assertThat(flat.get("{{tactic 1 KPI goal}}")).isEqualTo("—");
		assertThat(flat.get("{{tactic 1 vs goal}}")).isEqualTo("—");
	}

	@Test
	void fill_shouldLeaveTheSlotsAboveTheTacticCountAloneTest() {
		// Given: a workbook carrying a third tactic the campaign no longer has
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{tactic 3 spend plan}}", "$1,000");
		flat.put("{{tactic 3 KPI type}}", "CTR");

		// When: only two tactics are real
		resolver.fill(flat, 2);

		// Then: the surplus row is never filled — its dashboard row is deleted during the deck trim
		assertThat(flat).doesNotContainKey("{{tactic 3 planned budget}}");
		assertThat(flat).doesNotContainKey("{{tactic 3 KPI goal}}");
	}

	@Test
	void fill_shouldDashTheDividerChipsTheCampaignHasNoTacticForTest() {
		// Given: a two-tactic campaign, whose divider slide still carries three channel chips
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{tactic 1}}", "CTV");
		flat.put("{{tactic 2}}", "Display");

		// When:
		resolver.fill(flat, 2);

		// Then: the third chip dashes instead of shipping as a raw token, and the real names stay put
		assertThat(flat.get("{{tactic 1}}")).isEqualTo("CTV");
		assertThat(flat.get("{{tactic 2}}")).isEqualTo("Display");
		assertThat(flat.get("{{tactic 3}}")).isEqualTo("—");
	}

	@Test
	void fill_shouldLeaveEveryDividerChipAloneWhenTheCampaignFillsThemTest() {
		// Given: a three-tactic campaign, which needs every chip the divider prints
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{tactic 1}}", "CTV");
		flat.put("{{tactic 2}}", "Display");
		flat.put("{{tactic 3}}", "Online Video");

		// When:
		resolver.fill(flat, 3);

		// Then: no chip is dashed
		assertThat(flat.get("{{tactic 3}}")).isEqualTo("Online Video");
	}

	@Test
	void fill_shouldStepTheFocusSlidesHeadingOffTheMonthTheCoverPrintsTest() {
		// Given: a reviewed workbook whose cover names August 2026, the second month of the flight
		Map<String, String> flat = twoTacticSheet();
		flat.put("{{reporting month}}", "August 2026");
		flat.put("{{mon no}}", "2");

		// When:
		resolver.fill(flat, 2);

		// Then: the "focus next month" slide names the month after the cover's, and the position after the
		// cover's — derived from the reviewed values so the two slides can never name different months
		assertThat(flat.get("{{reporting month +1}}")).isEqualTo("September 2026");
		assertThat(flat.get("{{mon no +1}}")).isEqualTo("3");
	}

	@Test
	void fill_shouldStepOffTheLastMonthOfATwoMonthWindowTest() {
		// Given: a reporting window spanning two calendar months, and one crossing the year
		Map<String, String> summer = twoTacticSheet();
		summer.put("{{reporting month}}", "July - August 2026");
		Map<String, String> winter = twoTacticSheet();
		winter.put("{{reporting month}}", "December 2025 - January 2026");

		// When:
		resolver.fill(summer, 2);
		resolver.fill(winter, 2);

		// Then: the next month follows the window's END, and the year rolls with it
		assertThat(summer.get("{{reporting month +1}}")).isEqualTo("September 2026");
		assertThat(winter.get("{{reporting month +1}}")).isEqualTo("February 2026");
	}

	@Test
	void fill_shouldDashTheFocusHeadingRatherThanGuessItTest() {
		// Given: a workbook carrying neither the reporting month nor its position in the flight
		Map<String, String> flat = twoTacticSheet();

		// When:
		resolver.fill(flat, 2);

		// Then: both dash — an unreadable month number would otherwise print "1", the one position that is
		// never right on a report about a month already delivered
		assertThat(flat.get("{{reporting month +1}}")).isEqualTo("—");
		assertThat(flat.get("{{mon no +1}}")).isEqualTo("—");
	}
}
