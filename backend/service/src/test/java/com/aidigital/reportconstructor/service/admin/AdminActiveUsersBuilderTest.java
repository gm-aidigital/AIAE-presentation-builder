package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminActiveUsersBuilderTest {

	/**
	 * Builds the active-users builder.
	 *
	 * @return the builder under test
	 */
	AdminActiveUsersBuilder builder() {
		RollupUsageMath math = new RollupUsageMath(
				new ClaudeCostCalculator(new ClaudePricingProperties()), new ReportCountPolicy());
		return new AdminActiveUsersBuilder(new AdminPeriodBucketer(), math);
	}

	@Test
	void shouldCountAUserOnceHoweverManyDaysTheyWereActiveTest() {
		// Given: one user active on three days of the same month, and a second on one.
		List<UsageActiveDay> days = List.of(
				new UsageActiveDay(LocalDate.of(2026, 1, 5), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 1, 6), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 1, 20), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 1, 21), "u2"));

		// When:
		List<AdminActiveUsersPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: two active users, not four days of activity.
		assertThat(months).hasSize(1);
		assertThat(months.getFirst().activeUsers()).isEqualTo(2);
	}

	@Test
	void shouldCountAReturningUserAsActiveButNotNewTest() {
		// Given: u1 in January and again in February; u2 arrives in February.
		List<UsageActiveDay> days = List.of(
				new UsageActiveDay(LocalDate.of(2026, 1, 5), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 2, 5), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 2, 6), "u2"));

		// When:
		List<AdminActiveUsersPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: February has two active users but only one new one — the distinction the raw count
		// hides, and the one that says whether the growth is arrivals or the same people returning.
		AdminActiveUsersPeriod february = months.get(1);
		assertThat(february.activeUsers()).isEqualTo(2);
		assertThat(february.newUsers()).isEqualTo(1);
		assertThat(february.prevActive()).isEqualTo(1);
		assertThat(february.deltaPct()).isEqualTo(100d);
	}

	@Test
	void shouldNotCompareAcrossAMonthWithNoActivityTest() {
		// Given: activity in January and March, with February silent.
		List<UsageActiveDay> days = List.of(
				new UsageActiveDay(LocalDate.of(2026, 1, 5), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 3, 5), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 3, 6), "u2"));

		// When:
		List<AdminActiveUsersPeriod> months = builder().build(days, AdminPeriodUnit.MONTH);

		// Then: March's predecessor is February, which has no row, so no comparison is claimed.
		AdminActiveUsersPeriod march = months.get(1);
		assertThat(march.key()).isEqualTo("2026-03");
		assertThat(march.prevActive()).isNull();
		assertThat(march.deltaPct()).isNull();
	}

	@Test
	void shouldCountTheSameUserInEveryWeekTheyAppearInTest() {
		// Given: one user active in two consecutive weeks.
		List<UsageActiveDay> days = List.of(
				new UsageActiveDay(LocalDate.of(2026, 1, 6), "u1"),
				new UsageActiveDay(LocalDate.of(2026, 1, 13), "u1"));

		// When:
		List<AdminActiveUsersPeriod> weeks = builder().build(days, AdminPeriodUnit.WEEK);

		// Then: one active user in each week — active-user counts are per bucket and deliberately do
		// not add up across buckets.
		assertThat(weeks).hasSize(2);
		assertThat(weeks).allSatisfy(week -> assertThat(week.activeUsers()).isEqualTo(1));
		assertThat(weeks.get(1).newUsers()).isZero();
	}
}
