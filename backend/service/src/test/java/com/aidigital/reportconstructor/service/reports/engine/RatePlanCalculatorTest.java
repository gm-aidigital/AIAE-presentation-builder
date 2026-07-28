package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RatePlanCalculatorTest {

	private final RatePlanCalculator calculator = new RatePlanCalculator();

	@Test
	void proratedBudgetShouldReturnFullMonthlyBudgetForAWholeCalendarMonthTest() {
		// Given: a period spanning exactly January 2026 (31 days)
		LocalDate start = LocalDate.of(2026, 1, 1);
		LocalDate end = LocalDate.of(2026, 1, 31);

		// When:
		Double result = calculator.proratedBudget(3100.0, start, end);

		// Then: the full monthly budget applies unchanged
		assertThat(result).isEqualTo(3100.0);
	}

	@Test
	void proratedBudgetShouldScaleByDayShareForAPartialMonthTest() {
		// Given: only the last 10 days of a 31-day January are in the period
		LocalDate start = LocalDate.of(2026, 1, 22);
		LocalDate end = LocalDate.of(2026, 1, 31);

		// When:
		Double result = calculator.proratedBudget(3100.0, start, end);

		// Then: 3100 * 10/31 = 1000
		assertThat(result).isEqualTo(1000.0);
	}

	@Test
	void proratedBudgetShouldSumEachOverlappingCalendarMonthsShareTest() {
		// Given: a period spanning the last day of January and all of February (2026, 28 days)
		LocalDate start = LocalDate.of(2026, 1, 31);
		LocalDate end = LocalDate.of(2026, 2, 28);

		// When:
		Double result = calculator.proratedBudget(2800.0, start, end);

		// Then: January contributes 1/31 of a month, February contributes the full month:
		// 2800 * 1/31 + 2800 = 2890.32...
		assertThat(result).isEqualTo(2800.0 * 1 / 31 + 2800.0);
	}

	@Test
	void proratedBudgetShouldReturnNullWhenMonthlyBudgetIsMissingTest() {
		// Given/When:
		Double result = calculator.proratedBudget(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

		// Then:
		assertThat(result).isNull();
	}

	@Test
	void proratedBudgetShouldReturnNullWhenThePeriodIsInvertedTest() {
		// Given/When: end before start
		Double result = calculator.proratedBudget(1000.0, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1));

		// Then:
		assertThat(result).isNull();
	}

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
}
