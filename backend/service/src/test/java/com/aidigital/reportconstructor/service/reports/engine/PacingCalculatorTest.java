package com.aidigital.reportconstructor.service.reports.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PacingCalculatorTest {

	private final PacingCalculator pacing = new PacingCalculator();

	@Test
	void inclusiveDaysShouldCountBothEndpointsTest() {
		// Given: a 31-day October
		LocalDate start = LocalDate.of(2025, 10, 1);
		LocalDate end = LocalDate.of(2025, 10, 31);

		// When:
		long days = pacing.inclusiveDays(start, end);

		// Then:
		assertThat(days).isEqualTo(31);
	}

	@Test
	void inclusiveDaysShouldClampInvertedRangeToOneTest() {
		// Given: an inverted range
		LocalDate start = LocalDate.of(2025, 10, 31);
		LocalDate end = LocalDate.of(2025, 10, 1);

		// When:
		long days = pacing.inclusiveDays(start, end);

		// Then:
		assertThat(days).isEqualTo(1);
	}

	@Test
	void planCtdShouldProrateByPeriodShareOfFlightTest() {
		// Given: a 90-day flight with a 300,000 impression goal, reporting on its first 30 days
		// When:
		double planCtd = pacing.planCtd(300_000, 30, 90);

		// Then: a third of the flight, a third of the plan
		assertThat(planCtd).isEqualTo(100_000);
	}

	@Test
	void planCtdShouldReturnFullPlanWhenPeriodSpansWholeFlightTest() {
		// Given/When:
		double planCtd = pacing.planCtd(300_000, 90, 90);

		// Then:
		assertThat(planCtd).isEqualTo(300_000);
	}

	@Test
	void projectionShouldExtrapolatePeriodRunRateAcrossFlightTest() {
		// Given: 100,000 impressions delivered in the first 30 days of a 90-day flight
		// When:
		double projected = pacing.projection(100_000, 30, 90);

		// Then: holding the same daily rate for the whole flight lands at 300,000
		assertThat(projected).isEqualTo(300_000);
	}

	@Test
	void paceVarianceShouldReportOnPlanWithinRateTolerancePpTest() {
		// Given: CTR actual and goal within 0.05pp of each other
		// When:
		String variance = pacing.paceVariance(2.52, 2.50, true);

		// Then:
		assertThat(variance).isEqualTo("on plan");
	}

	@Test
	void paceVarianceShouldSignAndSuffixRateDeltaInPercentagePointsTest() {
		// Given: CTR running 0.3pp above goal
		// When:
		String variance = pacing.paceVariance(2.8, 2.5, true);

		// Then:
		assertThat(variance).isEqualTo("+0.3pp");
	}

	@Test
	void paceVarianceShouldSignAndSuffixCountDeltaAsRelativePercentTest() {
		// Given: impressions running 11% ahead of the prorated goal
		// When:
		String variance = pacing.paceVariance(111_000, 100_000, false);

		// Then:
		assertThat(variance).isEqualTo("+11%");
	}

	@Test
	void paceVarianceShouldReportOnPlanWithinCountTolerancePercentTest() {
		// Given/When:
		String variance = pacing.paceVariance(100_500, 100_000, false);

		// Then:
		assertThat(variance).isEqualTo("on plan");
	}

	@Test
	void paceVarianceShouldReturnNullWhenCountGoalIsZeroOrLessTest() {
		// Given/When:
		String variance = pacing.paceVariance(1_000, 0, false);

		// Then:
		assertThat(variance).isNull();
	}

	@Test
	void monthNumberShouldCountCalendarMonthsFromFlightStartTest() {
		// Given: a flight starting September 15, reporting through the end of November
		LocalDate flightStart = LocalDate.of(2025, 9, 15);
		LocalDate periodEnd = LocalDate.of(2025, 11, 30);

		// When:
		int monthNumber = pacing.monthNumber(flightStart, periodEnd);

		// Then: September=1, October=2, November=3
		assertThat(monthNumber).isEqualTo(3);
	}

	@Test
	void totalMonthsShouldCountCalendarMonthsAcrossTheFullFlightTest() {
		// Given:
		LocalDate flightStart = LocalDate.of(2025, 9, 15);
		LocalDate flightEnd = LocalDate.of(2025, 11, 30);

		// When:
		int totalMonths = pacing.totalMonths(flightStart, flightEnd);

		// Then:
		assertThat(totalMonths).isEqualTo(3);
	}

	@Test
	void monthLabelShouldFormatFullMonthNameAndYearTest() {
		// Given/When:
		String label = pacing.monthLabel(LocalDate.of(2025, 10, 15));

		// Then:
		assertThat(label).isEqualTo("October 2025");
	}
}
