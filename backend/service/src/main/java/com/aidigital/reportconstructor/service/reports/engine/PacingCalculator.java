package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Pure date/number math behind EOM pacing: proration of a full-campaign plan down to a
 * reporting period, a pace-based projection back up to the full flight, and the calendar
 * labels ({@code eom_month_number}, {@code eom_report_month}, ...) shown alongside them. Holds
 * no campaign data itself — every method takes its inputs explicitly, so it stays trivial to
 * unit-test.
 */
@Component
public class PacingCalculator {

	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
	private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMMM", Locale.US);
	private static final double RATE_ON_PLAN_THRESHOLD_PP = 0.05;
	private static final double COUNT_ON_PLAN_THRESHOLD_PCT = 1.0;

	/**
	 * Counts the inclusive number of days in {@code [start, end]}, clamped to a minimum of 1 so
	 * callers never divide by zero on a same-day or inverted range.
	 *
	 * @param start first day (inclusive)
	 * @param end   last day (inclusive)
	 * @return the inclusive day count, at least 1
	 */
	public long inclusiveDays(LocalDate start, LocalDate end) {
		long days = ChronoUnit.DAYS.between(start, end) + 1;
		return Math.max(days, 1);
	}

	/**
	 * Extracts the inclusive day counts needed for EOM proration from a campaign's reporting period
	 * and flight window, shared by the per-tactic and campaign-level pacing resolvers so both read the
	 * same day-count logic.
	 *
	 * @param data campaign data whose reporting period/flight window bound the proration
	 * @return {@code [periodDays, flightDays]}, or {@code null} when {@code data} carries no reporting
	 * period (EOC, or an EOM request with none selected)
	 */
	public long[] periodAndFlightDays(CampaignData data) {
		if (data == null || data.reportPeriod() == null || data.flightTs() == null) {
			return null;
		}
		long periodDays = inclusiveDays(data.reportPeriod().start(), data.reportPeriod().end());
		long flightDays = inclusiveDays(data.flightTs().start(), data.flightTs().end());
		return new long[]{periodDays, flightDays};
	}

	/**
	 * Prorates the full-campaign plan down to the reporting period: {@code fullPlan * periodDays /
	 * flightDays}. Passing a period equal to the whole flight returns {@code fullPlan} unchanged;
	 * passing the campaign's own start as the period's start yields the cumulative-to-date reading.
	 *
	 * @param fullPlan   the whole-campaign planned target (Estimates-tab figure, unaffected by any window)
	 * @param periodDays inclusive day count of the reporting period
	 * @param flightDays inclusive day count of the full flight
	 * @return the prorated to-date goal
	 */
	public double planCtd(double fullPlan, long periodDays, long flightDays) {
		return fullPlan * periodDays / (double) flightDays;
	}

	/**
	 * Extrapolates the period's observed run-rate across the whole flight: {@code periodActual /
	 * periodDays * flightDays}. This is the "where will we land if the current pace holds" figure.
	 *
	 * @param periodActual the actual metric value observed within the reporting period
	 * @param periodDays   inclusive day count of the reporting period
	 * @param flightDays   inclusive day count of the full flight
	 * @return the pace-based projection across the full flight
	 */
	public double projection(double periodActual, long periodDays, long flightDays) {
		return periodActual / periodDays * flightDays;
	}

	/**
	 * Formats the variance of the period actual against its prorated goal: a signed percentage-point
	 * delta for rate metrics (CTR/VCR, already expressed as percentages) or a signed relative
	 * percentage for count/currency metrics (impressions, spend, ...), collapsing to {@code "on plan"}
	 * within a small tolerance band.
	 *
	 * @param actual  the period's actual metric value
	 * @param planCtd the period's prorated goal for the same metric
	 * @param isRate  {@code true} for a rate metric (CTR/VCR: variance in percentage points),
	 *                {@code false} for a count/currency metric (variance as a relative percentage)
	 * @return the formatted variance (e.g. {@code "+11%"}, {@code "+0.3pp"}, {@code "on plan"}), or
	 * {@code null} when a relative percentage can't be computed ({@code planCtd <= 0})
	 */
	public String paceVariance(double actual, double planCtd, boolean isRate) {
		if (isRate) {
			double diffPp = actual - planCtd;
			if (Math.abs(diffPp) < RATE_ON_PLAN_THRESHOLD_PP) {
				return "on plan";
			}
			return (diffPp > 0 ? "+" : "") + String.format(Locale.US, "%.1f", diffPp) + "pp";
		}
		if (planCtd <= 0) {
			return null;
		}
		double pct = (actual - planCtd) / planCtd * 100;
		if (Math.abs(pct) < COUNT_ON_PLAN_THRESHOLD_PCT) {
			return "on plan";
		}
		return (pct > 0 ? "+" : "") + Math.round(pct) + "%";
	}

	/**
	 * The reporting period's 1-based index among the flight's calendar months (e.g. the period
	 * ending in the flight's second calendar month is month 2), counted by calendar month regardless
	 * of exact day-of-month alignment.
	 *
	 * @param flightStart first day of the full flight
	 * @param periodEnd   last day of the reporting period
	 * @return the 1-based month index
	 */
	public int monthNumber(LocalDate flightStart, LocalDate periodEnd) {
		return (int) ChronoUnit.MONTHS.between(flightStart.withDayOfMonth(1), periodEnd.withDayOfMonth(1)) + 1;
	}

	/**
	 * The total number of calendar months the full flight spans.
	 *
	 * @param flightStart first day of the full flight
	 * @param flightEnd   last day of the full flight
	 * @return the total calendar-month count
	 */
	public int totalMonths(LocalDate flightStart, LocalDate flightEnd) {
		return (int) ChronoUnit.MONTHS.between(flightStart.withDayOfMonth(1), flightEnd.withDayOfMonth(1)) + 1;
	}

	/**
	 * Formats a date's calendar month and year, e.g. {@code "October 2025"}.
	 *
	 * @param date the date whose month/year to format
	 * @return the {@code "MMMM yyyy"} label
	 */
	public String monthLabel(LocalDate date) {
		return date.format(MONTH_YEAR);
	}

	/**
	 * Formats a date's calendar month name only (no year), e.g. {@code "November"} — used for the
	 * "next month" focus slide, which names the upcoming month without restating the year.
	 *
	 * @param date the date whose month to format
	 * @return the {@code "MMMM"} label
	 */
	public String monthNameOnly(LocalDate date) {
		return date.format(MONTH_ONLY);
	}
}
