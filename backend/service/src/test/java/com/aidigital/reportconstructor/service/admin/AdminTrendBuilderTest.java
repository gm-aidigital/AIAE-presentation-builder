package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenPeriod;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTrendBuilderTest {

	/**
	 * Builds a rollup row for one day of ordinary slide-deck reports.
	 *
	 * @param day    the calendar day
	 * @param jobs   jobs that day
	 * @param input  plain input tokens
	 * @param output output tokens
	 * @return the rollup row
	 */
	UsageDailyBucket day(LocalDate day, long jobs, long input, long output) {
		return new UsageDailyBucket(
				day, "EOC", "SLIDES", "claude-sonnet-4-6",
				jobs, jobs, 0L, jobs, input, output, 0L, 0L, 0L, 0L, 0L);
	}

	/**
	 * Builds the trend builder with pricing that makes cost read back as tokens.
	 *
	 * @return the builder under test
	 */
	AdminTrendBuilder builder() {
		ClaudeModelPrice price = new ClaudeModelPrice();
		price.setInputPerMtok(1d);
		price.setOutputPerMtok(1d);
		price.setCacheWritePerMtok(1d);
		price.setCacheReadPerMtok(1d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(price);
		RollupUsageMath math = new RollupUsageMath(new ClaudeCostCalculator(pricing), new ReportCountPolicy());
		return new AdminTrendBuilder(new AdminPeriodBucketer(), math);
	}

	@Test
	void shouldFoldDaysIntoMonthsAndCompareWithThePreviousMonthTest() {
		// Given: two days in January and one in February.
		List<UsageDailyBucket> days = List.of(
				day(LocalDate.of(2026, 1, 5), 2, 1_000, 100),
				day(LocalDate.of(2026, 1, 20), 3, 3_000, 300),
				day(LocalDate.of(2026, 2, 3), 1, 2_000, 200));

		// When:
		List<AdminTokenPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: one bucket per month, oldest first, with January's days summed.
		assertThat(months).hasSize(2);
		assertThat(months.getFirst().key()).isEqualTo("2026-01");
		assertThat(months.getFirst().totalTokens()).isEqualTo(4_400);
		assertThat(months.getFirst().reports()).isEqualTo(5);

		// And: February compares against January — 2,200 against 4,400 is a halving.
		AdminTokenPeriod february = months.get(1);
		assertThat(february.key()).isEqualTo("2026-02");
		assertThat(february.prevTotalTokens()).isEqualTo(4_400);
		assertThat(february.tokensDeltaPct()).isEqualTo(-50d);
	}

	@Test
	void shouldLeaveTheFirstBucketWithoutAComparisonTest() {
		// Given: a single month of activity.
		List<UsageDailyBucket> days = List.of(day(LocalDate.of(2026, 1, 5), 1, 1_000, 0));

		// When:
		List<AdminTokenPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: no previous month exists, so the delta is absent rather than zero — reporting 0%
		// would claim nothing changed, which is a different statement from "nothing to compare".
		assertThat(months.getFirst().prevTotalTokens()).isNull();
		assertThat(months.getFirst().tokensDeltaPct()).isNull();
	}

	@Test
	void shouldNotCompareAcrossAMonthWithNoActivityTest() {
		// Given: activity in January and March, with February silent.
		List<UsageDailyBucket> days = List.of(
				day(LocalDate.of(2026, 1, 5), 1, 1_000, 0),
				day(LocalDate.of(2026, 3, 5), 1, 9_000, 0));

		// When:
		List<AdminTokenPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: March's predecessor is February, which has no row — so March reports no comparison
		// rather than a ninefold rise against January two months earlier.
		AdminTokenPeriod march = months.get(1);
		assertThat(march.key()).isEqualTo("2026-03");
		assertThat(march.prevTotalTokens()).isNull();
		assertThat(march.tokensDeltaPct()).isNull();
	}

	@Test
	void shouldKeepAWeekTogetherAcrossAMonthBoundaryTest() {
		// Given: two days of the same ISO week that fall either side of 1 February 2026 (the week
		// running Mon 26 Jan – Sun 1 Feb).
		List<UsageDailyBucket> days = List.of(
				day(LocalDate.of(2026, 1, 30), 1, 1_000, 0),
				day(LocalDate.of(2026, 2, 1), 1, 1_000, 0));

		// When:
		List<AdminTokenPeriod> weeks = builder().build(days, AdminPeriodUnit.WEEK);

		// Then: one week, not two — the ISO week-year is what keeps the bucket whole.
		assertThat(weeks).hasSize(1);
		assertThat(weeks.getFirst().start()).isEqualTo(LocalDate.of(2026, 1, 26));
		assertThat(weeks.getFirst().totalTokens()).isEqualTo(2_000);
	}

	@Test
	void shouldNotCountAnIntermediateSheetStepAsItsOwnReportTest() {
		// Given: a slide-deck run's sheet step and the deck it feeds, on the same day.
		List<UsageDailyBucket> days = List.of(
				new UsageDailyBucket(LocalDate.of(2026, 1, 5), "EOC", "SHEET", "claude-sonnet-4-6",
						1L, 1L, 0L, 1L, 500L, 50L, 0L, 0L, 0L, 0L, 0L),
				day(LocalDate.of(2026, 1, 5), 1, 1_000, 100));

		// When:
		List<AdminTokenPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: one report, but both steps' tokens — the sheet step cost real money and is not a
		// report in its own right.
		assertThat(months.getFirst().reports()).isEqualTo(1);
		assertThat(months.getFirst().totalTokens()).isEqualTo(1_650);
	}
}
