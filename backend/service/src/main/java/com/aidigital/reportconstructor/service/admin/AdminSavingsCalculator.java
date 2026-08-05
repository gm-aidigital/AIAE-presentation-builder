package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.config.SavingsProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminSavings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Works out what the generated reports would have cost to produce by hand, and what the difference
 * is worth.
 *
 * <p>The model is one line: a slide costs {@link SavingsProperties#getManualMinutesPerSlide()}
 * minutes to build by hand, so a report's manual cost is that times the slides it shipped, and the
 * saving is that cost less the wall-clock time the generated run actually took. Both assumptions are
 * configuration, and both are reported back alongside the answer, because a saved-dollars figure
 * with no visible basis is a figure nobody can check.
 *
 * <p>Two details keep it honest. Reports whose deck was never measured — runs that predate slide
 * counting, or whose deck could not be read — are modelled at the configured per-type default rather
 * than counted as zero-slide reports, and how many slides were genuinely measured is reported so the
 * proportion of modelling is visible. And the automation's own runtime is subtracted rather than
 * ignored: the pipeline is fast, not free.
 *
 * <p>Everything is scoped to the window the dashboard is showing, so the figure answers "what did we
 * save over these dates" rather than quietly mixing in months nobody asked about.
 */
@Component
@RequiredArgsConstructor
public class AdminSavingsCalculator {

	/** Minutes in an hour, as a divisor for the manual-minutes total. */
	private static final double MINUTES_PER_HOUR = 60d;

	/** Seconds in an hour, as a divisor for the measured generation time. */
	private static final double SECONDS_PER_HOUR = 3600d;

	private final SavingsProperties props;
	private final RollupUsageMath math;

	/**
	 * Computes the savings figure from the daily rollup.
	 *
	 * @param days daily rollup rows already restricted to the window
	 * @return the savings block, all-zero when no report counts
	 */
	public AdminSavings calculate(List<UsageDailyBucket> days) {
		long reports = 0;
		long slides = 0;
		long slidesMeasured = 0;
		long generationSeconds = 0;

		for (UsageDailyBucket day : days) {
			if (!math.isCountable(day)) {
				// An intermediate review sheet is real work the pipeline did, but it is not a deck and its
				// slides are counted once, on the report it feeds.
				continue;
			}
			long jobs = math.value(day.jobs());
			long daySlides = slidesFor(day);
			long daySeconds = math.value(day.generationSeconds());

			reports += jobs;
			slides += daySlides;
			slidesMeasured += math.value(day.slides());
			generationSeconds += daySeconds;
		}

		double manualHours = manualHours(slides);
		double automationHours = automationHours(generationSeconds);
		double savedHours = Math.max(0d, manualHours - automationHours);

		return new AdminSavings(
				(int) reports,
				slides,
				slidesMeasured,
				reports == 0 ? 0d : (double) slides / reports,
				manualHours,
				automationHours,
				savedHours,
				savedHours * props.getHourlyRateUsd(),
				props.getHourlyRateUsd(),
				props.getManualMinutesPerSlide());
	}

	/**
	 * Slides a rollup row's reports shipped, modelling the ones that were never measured.
	 *
	 * <p>A row can hold both kinds at once — some of its jobs produced a measured deck and some did
	 * not — so the measured slides are taken as they are and only the remaining jobs are modelled.
	 *
	 * @param day the rollup row
	 * @return measured slides plus the modelled default for the unmeasured jobs
	 */
	long slidesFor(UsageDailyBucket day) {
		long jobs = math.value(day.jobs());
		long measuredJobs = Math.min(jobs, math.value(day.jobsWithSlides()));
		long unmeasured = Math.max(0L, jobs - measuredJobs);
		return math.value(day.slides()) + unmeasured * defaultSlides(day.reportTypeCode());
	}

	/**
	 * Slides to assume for one report of a type whose deck was never measured.
	 *
	 * @param reportTypeCode the report type code as stored in the rollup
	 * @return the configured default for that type, or the global default
	 */
	int defaultSlides(String reportTypeCode) {
		Integer configured = props.getDefaultSlidesByType().get(reportTypeCode);
		return configured == null ? props.getDefaultSlides() : configured;
	}

	/**
	 * Hours a number of slides would cost to build by hand at the configured baseline.
	 *
	 * @param slides the slide count
	 * @return the modelled manual hours
	 */
	double manualHours(long slides) {
		return slides * props.getManualMinutesPerSlide() / MINUTES_PER_HOUR;
	}

	/**
	 * Hours the generated runs actually took, or zero when the model is configured to ignore them.
	 *
	 * @param generationSeconds measured wall-clock seconds
	 * @return the hours to subtract from the manual cost
	 */
	double automationHours(long generationSeconds) {
		return props.isSubtractGenerationTime() ? generationSeconds / SECONDS_PER_HOUR : 0d;
	}
}
