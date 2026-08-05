package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.config.SavingsProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminSavings;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminSavingsCalculatorTest {

	/** The day every fixture sits on, and the reference date for "this month". */
	private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

	/**
	 * Builds a rollup row of measured slide-deck reports.
	 *
	 * @param day               the calendar day
	 * @param jobs              jobs that day
	 * @param slides            slides those jobs' decks shipped
	 * @param jobsWithSlides    how many of those jobs produced a measured deck
	 * @param generationSeconds wall-clock seconds those runs took
	 * @return the rollup row
	 */
	UsageDailyBucket day(LocalDate day, long jobs, long slides, long jobsWithSlides, long generationSeconds) {
		return new UsageDailyBucket(
				day, "EOC", "SLIDES", "claude-sonnet-4-6",
				jobs, jobs, 0L, jobs, 0L, 0L, 0L, 0L, slides, jobsWithSlides, generationSeconds);
	}

	/**
	 * Builds the calculator at the configured baseline: 15 minutes a slide, $14 an hour.
	 *
	 * @return the calculator under test
	 */
	AdminSavingsCalculator calculator() {
		SavingsProperties props = new SavingsProperties();
		props.setDefaultSlidesByType(Map.of("EOC", 25, "EOM", 16));
		RollupUsageMath math = new RollupUsageMath(
				new ClaudeCostCalculator(new ClaudePricingProperties()), new ReportCountPolicy());
		return new AdminSavingsCalculator(props, math);
	}

	@Test
	void shouldValueMeasuredSlidesAtTheConfiguredBaselineTest() {
		// Given: one report that shipped 20 measured slides and took 6 minutes to generate.
		List<UsageDailyBucket> days = List.of(day(TODAY, 1, 20, 1, 360));

		// When:
		AdminSavings savings = calculator().calculate(days);

		// Then: 20 slides × 15 min = 5 hours by hand, less the 0.1 h the run actually took.
		assertThat(savings.manualHours()).isEqualTo(5d);
		assertThat(savings.automationHours()).isEqualTo(0.1d);
		assertThat(savings.savedHours()).isEqualTo(4.9d);
		assertThat(savings.savedUsd()).isCloseTo(4.9d * 14d, within(0.001d));
		assertThat(savings.slidesMeasured()).isEqualTo(20);
	}

	@Test
	void shouldModelUnmeasuredReportsAtThePerTypeDefaultTest() {
		// Given: two EOC reports, only one of which had its deck measured (at 20 slides).
		List<UsageDailyBucket> days = List.of(day(TODAY, 2, 20, 1, 0));

		// When:
		AdminSavings savings = calculator().calculate(days);

		// Then: the unmeasured one is modelled at the EOC default of 25 rather than counted as zero —
		// a report that predates slide counting saved time, and recording it as nothing would
		// understate the figure instead of leaving it uncertain.
		assertThat(savings.slidesTotal()).isEqualTo(45);
		assertThat(savings.slidesMeasured()).isEqualTo(20);
		assertThat(savings.avgSlidesPerReport()).isEqualTo(22.5d);
	}

	@Test
	void shouldNeverReportANegativeSavingTest() {
		// Given: a run that somehow took longer than the manual baseline it replaces.
		List<UsageDailyBucket> days = List.of(day(TODAY, 1, 1, 1, 36_000));

		// When:
		AdminSavings savings = calculator().calculate(days);

		// Then: the saving floors at zero rather than going negative — the automation costing more
		// than it saved is worth showing as "no saving", not as a debt.
		assertThat(savings.savedHours()).isZero();
		assertThat(savings.savedUsd()).isZero();
	}

	@Test
	void shouldCoverEveryDayTheCallerHandedItTest() {
		// Given: two days of reports, each with 20 measured slides. Restricting to a window is the
		// caller's job — by the time rows reach here they are already the window.
		List<UsageDailyBucket> days = List.of(
				day(LocalDate.of(2026, 2, 14), 1, 20, 1, 0),
				day(TODAY, 1, 20, 1, 0));

		// When:
		AdminSavings savings = calculator().calculate(days);

		// Then: both days count.
		assertThat(savings.savedHours()).isEqualTo(10d);
		assertThat(savings.reportsCounted()).isEqualTo(2);
	}

	@Test
	void shouldReportTheBaselineBackWithAnEmptyWindowTest() {
		// When: the window contains nothing at all.
		AdminSavings savings = calculator().calculate(List.of());

		// Then: the figures are zero, but the assumptions still come back — the panel prints them, and
		// a reader must be able to see the basis even when there is nothing to apply it to.
		assertThat(savings.reportsCounted()).isZero();
		assertThat(savings.savedUsd()).isZero();
		assertThat(savings.minutesPerSlide()).isEqualTo(15);
		assertThat(savings.hourlyRateUsd()).isEqualTo(14d);
	}

	@Test
	void shouldIgnoreAnIntermediateSheetStepTest() {
		// Given: a slide-deck flow's sheet step, which produces no deck.
		List<UsageDailyBucket> days = List.of(
				new UsageDailyBucket(TODAY, "EOC", "SHEET", "claude-sonnet-4-6",
						1L, 1L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 600L));

		// When:
		AdminSavings savings = calculator().calculate(days);

		// Then: it contributes nothing — its slides are counted once, on the deck it feeds, and
		// modelling it at the per-type default would invent a second deck that never existed.
		assertThat(savings.reportsCounted()).isZero();
		assertThat(savings.slidesTotal()).isZero();
		assertThat(savings.savedHours()).isZero();
	}
}
