package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.config.UsageRollupProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDateRangeResolverTest {

	/** Reference "today" for every fixture. */
	private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

	/**
	 * Builds the resolver with the default retention window.
	 *
	 * @return the resolver under test
	 */
	AdminDateRangeResolver resolver() {
		return new AdminDateRangeResolver(new UsageRollupProperties());
	}

	@Test
	void shouldDefaultToTheLastThirtyDaysWhenNoDatesAreGivenTest() {
		// When: a client asks for the dashboard without naming a window.
		AdminDateRange range = resolver().resolve(null, null, TODAY);

		// Then: thirty days ending today — inclusive at both ends, so the span is 30, not 31.
		assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 7));
		assertThat(range.to()).isEqualTo(TODAY);
	}

	@Test
	void shouldFillInTheMissingEndTest() {
		// Given: only a start date.
		AdminDateRange fromOnly = resolver().resolve(LocalDate.of(2026, 7, 1), null, TODAY);
		// And: only an end date.
		AdminDateRange toOnly = resolver().resolve(null, LocalDate.of(2026, 7, 31), TODAY);

		// Then: the open end is closed rather than the request being refused — half a window is a
		// perfectly clear intention.
		assertThat(fromOnly.to()).isEqualTo(TODAY);
		assertThat(toOnly.from()).isEqualTo(LocalDate.of(2026, 7, 2));
	}

	@Test
	void shouldSwapReversedEndsTest() {
		// When: the dates arrive the wrong way round.
		AdminDateRange range = resolver().resolve(
				LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), TODAY);

		// Then: read as the window it obviously means. A URL is not a form; there is nobody to correct.
		assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(range.to()).isEqualTo(LocalDate.of(2026, 7, 31));
	}

	@Test
	void shouldNotLookFurtherBackThanTheRetainedHistoryTest() {
		// When: a client asks for a decade.
		AdminDateRange range = resolver().resolve(LocalDate.of(2016, 1, 1), TODAY, TODAY);

		// Then: clamped to the history the rollup keeps — asking for 2016 cannot turn the dashboard
		// into a full-table scan.
		assertThat(range.from()).isEqualTo(TODAY.minusDays(new UsageRollupProperties().getHistoryDays()));
	}

	@Test
	void shouldNotReachIntoTheFutureTest() {
		// When: the window ends next month.
		AdminDateRange range = resolver().resolve(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), TODAY);

		// Then: it stops at today. Empty days beyond it would read as a collapse in activity.
		assertThat(range.to()).isEqualTo(TODAY);
	}

	@Test
	void shouldSurviveAWindowEntirelyInTheFutureTest() {
		// When: both ends are ahead of today.
		AdminDateRange range = resolver().resolve(
				LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), TODAY);

		// Then: a single real day rather than a start after its own end, which no query could answer.
		assertThat(range.from()).isEqualTo(TODAY);
		assertThat(range.to()).isEqualTo(TODAY);
	}

	@Test
	void shouldReadAWholeCalendarMonthWeekByWeekTest() {
		AdminDateRange july = resolver().resolve(
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), TODAY);

		assertThat(july.unit()).isEqualTo(AdminPeriodUnit.WEEK);
	}

	@Test
	void shouldKeepAShortWindowWeeklyEvenAcrossAMonthBoundaryTest() {
		// Given: 25 July – 5 August. Two calendar months, but only twelve days.
		AdminDateRange range = resolver().resolve(
				LocalDate.of(2026, 7, 25), LocalDate.of(2026, 8, 5), TODAY);

		// Then: weeks. Month-over-month here would be two stubs, not a trend.
		assertThat(range.unit()).isEqualTo(AdminPeriodUnit.WEEK);
	}

	@Test
	void shouldReadALongWindowMonthByMonthTest() {
		AdminDateRange quarter = resolver().resolve(TODAY.minusDays(89), TODAY, TODAY);

		assertThat(quarter.unit()).isEqualTo(AdminPeriodUnit.MONTH);
	}
}
