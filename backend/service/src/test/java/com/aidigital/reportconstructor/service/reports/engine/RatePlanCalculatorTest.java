package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.PlanUnitTargets;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RatePlanCalculatorTest {

	private final RatePlanCalculator calculator = new RatePlanCalculator();

	@Test
	void planUnitsShouldComputeImpressionsForCpmTest() {
		// Given: $5,000 spend at a $10 CPM
		// When:
		Double result = calculator.planUnits(5000.0, 10.0, RateType.CPM);

		// Then: 5000 / 10 * 1000 = 500,000 impressions
		assertThat(result).isEqualTo(500_000.0);
	}

	@Test
	void planUnitsShouldComputeClicksForCpcTest() {
		// Given: $500 spend at a $2 CPC
		// When:
		Double result = calculator.planUnits(500.0, 2.0, RateType.CPC);

		// Then: 500 / 2 = 250 clicks
		assertThat(result).isEqualTo(250.0);
	}

	@Test
	void planUnitsShouldComputeViewsForCpvTest() {
		// Given: $300 spend at a $0.05 CPV
		// When:
		Double result = calculator.planUnits(300.0, 0.05, RateType.CPV);

		// Then: 300 / 0.05 = 6,000 views
		assertThat(result).isEqualTo(6_000.0);
	}

	@Test
	void planTargetsShouldDeriveClicksAndCompletionsFromImpressionsForCpmTest() {
		// Given: $5,000 at a $10 CPM, with a 0.20% CTR and a 70% VCR benchmark
		// When:
		PlanUnitTargets targets = calculator.planTargets(5000.0, 10.0, RateType.CPM, 0.20, 70.0);

		// Then: impressions are bought (500,000) and the other two follow from the rates
		assertThat(targets.impressions()).isEqualTo(500_000.0);
		assertThat(targets.clicks()).isEqualTo(1_000.0);
		assertThat(targets.completions()).isEqualTo(350_000.0);
	}

	@Test
	void planTargetsShouldBackImpressionsOutOfClicksForCpcTest() {
		// Given: $500 at a $2 CPC, with a 0.25% CTR and a 50% VCR benchmark
		// When:
		PlanUnitTargets targets = calculator.planTargets(500.0, 2.0, RateType.CPC, 0.25, 50.0);

		// Then: clicks are bought (250), impressions come from the CTR and completions from those
		assertThat(targets.clicks()).isEqualTo(250.0);
		assertThat(targets.impressions()).isEqualTo(100_000.0);
		assertThat(targets.completions()).isEqualTo(50_000.0);
	}

	@Test
	void planTargetsShouldBackImpressionsOutOfCompletionsForCpvTest() {
		// Given: $300 at a $0.05 CPV, with a 0.10% CTR and a 60% VCR benchmark
		// When:
		PlanUnitTargets targets = calculator.planTargets(300.0, 0.05, RateType.CPV, 0.10, 60.0);

		// Then: completions are bought (6,000), impressions come from the VCR and clicks from those
		assertThat(targets.completions()).isEqualTo(6_000.0);
		assertThat(targets.impressions()).isEqualTo(10_000.0);
		assertThat(targets.clicks()).isEqualTo(10.0);
	}

	@Test
	void planTargetsShouldLeaveDerivedFiguresNullWhenTheirRateIsMissingTest() {
		// Given: a CPC tactic with a CTR benchmark but no VCR one, and a tactic with no unit price at all
		// When:
		PlanUnitTargets noVcr = calculator.planTargets(500.0, 2.0, RateType.CPC, 0.25, null);
		PlanUnitTargets noPrice = calculator.planTargets(500.0, null, RateType.CPC, 0.25, 50.0);

		// Then: only the underivable figures are null — nothing is invented
		assertThat(noVcr.clicks()).isEqualTo(250.0);
		assertThat(noVcr.impressions()).isEqualTo(100_000.0);
		assertThat(noVcr.completions()).isNull();
		assertThat(noPrice.impressions()).isNull();
		assertThat(noPrice.clicks()).isNull();
		assertThat(noPrice.completions()).isNull();
	}

	@Test
	void planUnitsShouldReturnNullWhenUnitPriceIsZeroOrNegativeTest() {
		// Given/When:
		Double zero = calculator.planUnits(5000.0, 0.0, RateType.CPM);
		Double negative = calculator.planUnits(5000.0, -1.0, RateType.CPM);

		// Then:
		assertThat(zero).isNull();
		assertThat(negative).isNull();
	}

	@Test
	void planUnitsShouldReturnNullWhenRateTypeOrSpendIsMissingTest() {
		// Given/When:
		Double noRateType = calculator.planUnits(5000.0, 10.0, null);
		Double noSpend = calculator.planUnits(null, 10.0, RateType.CPM);

		// Then:
		assertThat(noRateType).isNull();
		assertThat(noSpend).isNull();
	}

	@Test
	void monthsSpannedShouldCountOneForASingleCalendarMonthTest() {
		// Given: a window entirely within January 2026
		LocalDate start = LocalDate.of(2026, 1, 1);
		LocalDate end = LocalDate.of(2026, 1, 31);

		// When:
		int result = calculator.monthsSpanned(start, end);

		// Then:
		assertThat(result).isEqualTo(1);
	}

	@Test
	void monthsSpannedShouldCountEachCalendarMonthTouchedTest() {
		// Given: a window spanning the last day of January through the first day of March
		LocalDate start = LocalDate.of(2026, 1, 31);
		LocalDate end = LocalDate.of(2026, 3, 1);

		// When:
		int result = calculator.monthsSpanned(start, end);

		// Then: January, February, March
		assertThat(result).isEqualTo(3);
	}

	@Test
	void planCtdShouldScaleFullPlanByElapsedOverTotalMonthsTest() {
		// Given: a full-flight plan of 600,000 with 2 of 6 months elapsed
		// When:
		Double result = calculator.planCtd(600_000.0, 2, 6);

		// Then: 600,000 * 2/6 = 200,000
		assertThat(result).isEqualTo(200_000.0);
	}

	@Test
	void planCtdShouldReturnNullWhenFullPlanOrTotalMonthsIsMissingTest() {
		// Given/When:
		Double nullPlan = calculator.planCtd(null, 2, 6);
		Double zeroTotal = calculator.planCtd(600_000.0, 2, 0);

		// Then:
		assertThat(nullPlan).isNull();
		assertThat(zeroTotal).isNull();
	}

	@Test
	void projectionShouldExtrapolateToDateRunRateAcrossTotalMonthsTest() {
		// Given: 100,000 delivered after 2 of 6 months
		// When:
		Double result = calculator.projection(100_000.0, 2, 6);

		// Then: 100,000 / 2 * 6 = 300,000
		assertThat(result).isEqualTo(300_000.0);
	}

	@Test
	void projectionShouldReturnNullWhenActualOrElapsedMonthsIsMissingTest() {
		// Given/When:
		Double nullActual = calculator.projection(null, 2, 6);
		Double zeroElapsed = calculator.projection(100_000.0, 0, 6);

		// Then:
		assertThat(nullActual).isNull();
		assertThat(zeroElapsed).isNull();
	}

	@Test
	void paceVarianceShouldReturnOnPlanWhenCountIsWithinOnePercentTest() {
		// Given: actual is 0.5% above the to-date goal
		// When:
		String result = calculator.paceVariance(1005.0, 1000.0, false);

		// Then:
		assertThat(result).isEqualTo("on plan");
	}

	@Test
	void paceVarianceShouldReturnSignedPercentForACountMetricTest() {
		// Given: actual is 10% above the to-date goal
		// When:
		String result = calculator.paceVariance(1100.0, 1000.0, false);

		// Then:
		assertThat(result).isEqualTo("+10%");
	}

	@Test
	void paceVarianceShouldReturnSignedPercentagePointsForARateMetricTest() {
		// Given: actual CTR is 0.3pp above the to-date goal
		// When:
		String result = calculator.paceVariance(2.3, 2.0, true);

		// Then:
		assertThat(result).isEqualTo("+0.3pp");
	}

	@Test
	void paceVarianceShouldReturnOnPlanForARateMetricWithinThresholdTest() {
		// Given: actual CTR is 0.02pp above the to-date goal
		// When:
		String result = calculator.paceVariance(2.02, 2.0, true);

		// Then:
		assertThat(result).isEqualTo("on plan");
	}

	@Test
	void paceVarianceShouldReturnNullWhenCountGoalIsNonPositiveTest() {
		// Given/When:
		String result = calculator.paceVariance(100.0, 0.0, false);

		// Then:
		assertThat(result).isNull();
	}

	@Test
	void monthLabelShouldFormatMonthAndYearTest() {
		// Given/When:
		String result = calculator.monthLabel(LocalDate.of(2026, 3, 15));

		// Then:
		assertThat(result).isEqualTo("March 2026");
	}

	@Test
	void monthNameOnlyShouldFormatMonthWithoutYearTest() {
		// Given/When:
		String result = calculator.monthNameOnly(LocalDate.of(2026, 3, 15));

		// Then:
		assertThat(result).isEqualTo("March");
	}
}
