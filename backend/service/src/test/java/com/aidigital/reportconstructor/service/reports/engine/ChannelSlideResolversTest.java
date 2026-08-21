package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSlideResolversTest {

	private ChannelSlideResolvers resolvers;

	@BeforeEach
	void setUp() {
		SheetRowHelper sheetUtils = ReportsEngineTestSupport.sheetRowHelper();
		TacticResolvers tacticResolvers = new TacticResolvers(sheetUtils, new Fmt(),
				ReportsEngineTestSupport.tacticExtractionHelper(),
				new CampaignResolvers(sheetUtils, new Fmt(), ReportsEngineTestSupport.tacticExtractionHelper(),
						new RatePlanCalculator()),
				new RatePlanCalculator());
		resolvers = new ChannelSlideResolvers(sheetUtils, new Fmt(), tacticResolvers,
				ReportsEngineTestSupport.reportNumberParser());
	}

	/**
	 * One tactic pacing ahead of its month's goal: 512,300 of 500,000 impressions on a $6,000 monthly
	 * budget, against a flight booked for 1,500,000 impressions, 1,800 clicks, 400,000 reach and $18,000.
	 */
	private CampaignData campaign(double factImps, double factSpend) {
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				factSpend, factImps, 769, 0,
				0.15, null, null, null,
				6_000.0, 500_000.0, 0.12, null, 10.0,
				null, null, null,
				600.0, null, 2.0, 400_000.0,
				18_000.0, 1_500_000.0, 1_800.0);
		return new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null, null, null, null, null, null);
	}

	@Test
	void shouldFillTheImpressionsRowFromMonthPlanAndFlightGoalTest() {
		// Given: a tactic pacing at 102% of its monthly impressions goal
		CampaignData data = campaign(512_300, 5_994);

		// When: the impressions row is resolved
		Resolved pacing = resolvers.resolveImpsPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveEocPlannedImps(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveProjImps(1, List.of(), List.of(), data);

		// Then: the month's pace is a whole percentage and the flight goal carries forward at that pace
		assertThat(pacing.value()).isEqualTo("102%");
		assertThat(flightGoal.value()).isEqualTo("1,500,000");
		assertThat(projection.value()).isEqualTo("1,536,900");
	}

	@Test
	void shouldFloorAnUnderPacingProjectionAtItsFlightGoalTest() {
		// Given: a tactic that delivered only 400,000 of its 500,000 monthly impressions
		CampaignData data = campaign(400_000, 5_994);

		// When:
		Resolved pacing = resolvers.resolveImpsPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveProjImps(1, List.of(), List.of(), data);

		// Then: the projection restates the flight goal rather than dropping below it — a deliberate floor,
		// so the end-of-campaign column never projects a shortfall
		assertThat(pacing.value()).isEqualTo("80%");
		assertThat(projection.value()).isEqualTo("1,500,000");
	}

	@Test
	void shouldFillTheCtrRowAsPointsAndAHalvedProjectionTest() {
		// Given: a tactic beating its 0.12% CTR goal with 0.15%
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveCtrPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveCtrProj(1, List.of(), List.of(), data);

		// Then: the variance is signed percentage points and the projection sits midway to the goal
		assertThat(pacing.value()).isEqualTo("+0.03pp");
		assertThat(projection.value()).isEqualTo("0.14%");
	}

	@Test
	void shouldFillTheClicksRowFromTheMediaPlansOwnClickGoalTest() {
		// Given: 769 clicks against a 600-click month and an 1,800-click flight
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveClicksPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveClicksMp(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveClicksProj(1, List.of(), List.of(), data);

		// Then: the flight goal is the media plan's Clicks column, carried forward at the clicks pace
		assertThat(pacing.value()).isEqualTo("128%");
		assertThat(flightGoal.value()).isEqualTo("1,800");
		assertThat(projection.value()).isEqualTo("2,307");
	}

	@Test
	void shouldFillTheCpmRowWithSignedVarianceAndMidpointProjectionTest() {
		// Given: a $6,000 / 500,000 plan delivering 512,300 impressions for $5,994
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved planned = resolvers.resolvePlannedCpm(1, List.of(), List.of(), data);
		Resolved actual = resolvers.resolveFactCpm(1, List.of(), List.of(), data);
		Resolved pacing = resolvers.resolveCpmPacing(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveCpmProj(1, List.of(), List.of(), data);

		// Then: the variance is planned − delivered, so cheaper than plan reads as a plus
		assertThat(planned.value()).isEqualTo("$12.00");
		assertThat(actual.value()).isEqualTo("$11.70");
		assertThat(pacing.value()).isEqualTo("+ $0.30");
		assertThat(projection.value()).isEqualTo("$11.85");
	}

	@Test
	void shouldFillTheSpendRowWithTheFlightBudgetInBothEndOfCampaignColumnsTest() {
		// Given: $5,994 spent of a $6,000 month, on an $18,000 flight
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved pacing = resolvers.resolveBudgetPacing(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveSpendPlanEoc(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveSpendProj(1, List.of(), List.of(), data);

		// Then: a flight spends what it was booked for, so the projection restates the goal
		assertThat(pacing.value()).isEqualTo("100%");
		assertThat(flightGoal.value()).isEqualTo("$18,000.00");
		assertThat(projection.value()).isEqualTo("$18,000.00");
	}

	@Test
	void shouldFillTheReachRowAgainstThePlannedWeeklyFrequencyTest() {
		// Given: 512,300 impressions over a 30-day window at a planned 2 exposures per week
		CampaignData data = campaign(512_300, 5_994);

		// When:
		Resolved plan = resolvers.resolveReachPlan(1, List.of(), List.of(), data);
		Resolved flightGoal = resolvers.resolveReachPlanEoc(1, List.of(), List.of(), data);
		Resolved projection = resolvers.resolveReachProj(1, List.of(), List.of(), data);

		// Then: the month's goal is the reach those impressions imply at the planned frequency, while both
		// end-of-campaign columns carry the media plan's own Reach figure
		assertThat(plan.value()).isEqualTo("59,768");
		assertThat(flightGoal.value()).isEqualTo("400,000");
		assertThat(projection.value()).isEqualTo("400,000");
	}

	@Test
	void shouldDashEveryCellWhenTheTacticHasNoPlanTest() {
		// Given: a campaign with no data for the tactic at all
		CampaignData empty = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null, null, null, null, null, null);

		// When / Then: every cell prints the table's dash rather than an empty or infinite value
		assertThat(resolvers.resolveImpsPacing(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveProjImps(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveCtrPacing(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveClicksMp(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveReachPlan(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveFactCpm(1, List.of(), List.of(), empty).value()).isEqualTo("—");
		assertThat(resolvers.resolveSpendProj(1, List.of(), List.of(), empty).value()).isEqualTo("—");
	}

	@Test
	void shouldPreferAManualAdjustmentOverrideTest() {
		// Given: the Adjustments tab pins the flight impressions goal
		List<List<String>> adj = List.of(List.of("Tactic 1 eoc planned imps:", "2,000,000"));

		// When:
		Resolved r = resolvers.resolveEocPlannedImps(1, List.of(), adj, campaign(512_300, 5_994));

		// Then: the override wins over the media plan's own figure
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("2,000,000");
	}
}
