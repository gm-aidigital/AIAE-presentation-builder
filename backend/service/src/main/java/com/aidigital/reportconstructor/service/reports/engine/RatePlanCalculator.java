package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.PlanUnitTargets;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Pure math behind EOM plan values: converting a tactic's rate/budget into Plan Units, prorating a
 * full-flight plan down to the elapsed months (campaign-to-date), extrapolating the to-date actual
 * back up to the full flight, and the calendar labels ({@code eom_month_number},
 * {@code eom_report_month}, ...) shown alongside them. Holds no campaign data itself — every method
 * takes its inputs explicitly, so it stays trivial to unit-test.
 */
@Component
public class RatePlanCalculator {

	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
	private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMMM", Locale.US);
	private static final double RATE_ON_PLAN_THRESHOLD_PP = 0.05;
	private static final double COUNT_ON_PLAN_THRESHOLD_PCT = 1.0;

	/**
	 * Converts a spend amount into Plan Units for the given rate type: planned impressions for CPM
	 * ({@code spend / price × 1000}), planned clicks for CPC, planned views for CPV (both
	 * {@code spend / price}).
	 *
	 * @param spendAmount the spend figure to convert (e.g. a full-flight or to-date budget)
	 * @param unitPrice   the final unit price entered at matching time
	 * @param rateType    how the tactic's cost is bought
	 * @return the Plan Units figure, or {@code null} when any input is missing, {@code rateType} is
	 * {@code null}, or {@code unitPrice} is not positive
	 */
	Double planUnits(Double spendAmount, Double unitPrice, RateType rateType) {
		if (spendAmount == null || unitPrice == null || unitPrice <= 0 || rateType == null) {
			return null;
		}
		return switch (rateType) {
			case CPM -> spendAmount / unitPrice * 1000;
			case CPC, CPV -> spendAmount / unitPrice;
		};
	}

	/**
	 * Derives a tactic's full set of planned targets — impressions, clicks and completions — from its
	 * spend, unit price, rate type and the planned CTR/VCR benchmarks. The bought unit comes from
	 * {@link #planUnits}; the other two are derived through the planned rates so every column of the
	 * summary row describes the same plan:
	 * <ul>
	 *     <li>CPM: impressions are bought; clicks = impressions × CTR plan; completions = impressions × VCR plan</li>
	 *     <li>CPC: clicks are bought; impressions = clicks ÷ CTR plan; completions = impressions × VCR plan</li>
	 *     <li>CPV: completions are bought; impressions = completions ÷ VCR plan; clicks = impressions × CTR plan</li>
	 * </ul>
	 * Each derived figure needs its rate, so a tactic with no planned CTR simply has no planned clicks
	 * rather than a made-up one.
	 *
	 * @param spendAmount the spend figure to convert (the tactic's budget for the reporting month)
	 * @param unitPrice   the final unit price entered at matching time
	 * @param rateType    how the tactic's cost is bought
	 * @param ctrPlanPct  the planned click-through rate as a percentage (e.g. {@code 0.20} for 0.20%),
	 *                    or {@code null} when the tactic has no CTR benchmark
	 * @param vcrPlanPct  the planned completion rate as a percentage (e.g. {@code 75} for 75%), or
	 *                    {@code null} when the tactic has no VCR benchmark
	 * @return the three planned targets, each {@code null} when it cannot be derived
	 */
	PlanUnitTargets planTargets(Double spendAmount, Double unitPrice, RateType rateType,
	                            Double ctrPlanPct, Double vcrPlanPct) {
		Double units = planUnits(spendAmount, unitPrice, rateType);
		if (units == null) {
			return new PlanUnitTargets(null, null, null);
		}
		return switch (rateType) {
			case CPM -> new PlanUnitTargets(units, applyRate(units, ctrPlanPct), applyRate(units, vcrPlanPct));
			case CPC -> {
				Double imps = divideByRate(units, ctrPlanPct);
				yield new PlanUnitTargets(imps, units, applyRate(imps, vcrPlanPct));
			}
			case CPV -> {
				Double imps = divideByRate(units, vcrPlanPct);
				yield new PlanUnitTargets(imps, applyRate(imps, ctrPlanPct), units);
			}
		};
	}

	/**
	 * Applies a planned rate to an impressions figure: {@code impressions × ratePct / 100}.
	 *
	 * @param impressions the planned impressions the rate applies to ({@code null} when not derivable)
	 * @param ratePct     the planned rate as a percentage ({@code null} when the tactic has no such benchmark)
	 * @return the resulting count, or {@code null} when either input is missing or the rate is not positive
	 */
	Double applyRate(Double impressions, Double ratePct) {
		if (impressions == null || ratePct == null || ratePct <= 0) {
			return null;
		}
		return impressions * ratePct / 100;
	}

	/**
	 * Backs impressions out of a bought unit count and its planned rate: {@code count ÷ (ratePct / 100)}.
	 *
	 * @param count   the planned clicks or completions the tactic was bought in
	 * @param ratePct the planned rate that count is a share of, as a percentage ({@code null} when absent)
	 * @return the implied impressions, or {@code null} when either input is missing or the rate is not positive
	 */
	Double divideByRate(Double count, Double ratePct) {
		if (count == null || ratePct == null || ratePct <= 0) {
			return null;
		}
		return count / (ratePct / 100);
	}

	/**
	 * Counts the calendar months a date range spans (e.g. Feb 9 – Mar 15 spans 2: February and March),
	 * regardless of exact day-of-month alignment. Used for {@code eom_month_number} (months elapsed
	 * so far, from the currently selected Flight dates window).
	 *
	 * @param start first day of the range (inclusive)
	 * @param end   last day of the range (inclusive)
	 * @return the number of distinct calendar months the range touches, at least 1
	 */
	public int monthsSpanned(LocalDate start, LocalDate end) {
		return (int) ChronoUnit.MONTHS.between(start.withDayOfMonth(1), end.withDayOfMonth(1)) + 1;
	}

	/**
	 * Prorates a full-flight plan figure down to the months elapsed so far: {@code fullPlan *
	 * elapsedMonths / totalMonths}. Passing {@code elapsedMonths == totalMonths} returns
	 * {@code fullPlan} unchanged (the last reporting month).
	 *
	 * @param fullPlan      the whole-flight planned target
	 * @param elapsedMonths months elapsed so far (see {@link #monthsSpanned})
	 * @param totalMonths   total months the flight spans
	 * @return the prorated to-date goal, or {@code null} when {@code fullPlan} is {@code null} or
	 * {@code totalMonths} is not positive
	 */
	Double planCtd(Double fullPlan, int elapsedMonths, int totalMonths) {
		if (fullPlan == null || totalMonths <= 0) {
			return null;
		}
		return fullPlan * elapsedMonths / (double) totalMonths;
	}

	/**
	 * Extrapolates the to-date actual's run-rate across the whole flight: {@code actual /
	 * elapsedMonths * totalMonths}. This is the "where will we land if the current pace holds" figure.
	 *
	 * @param actual        the actual metric value observed so far (campaign-to-date)
	 * @param elapsedMonths months elapsed so far (see {@link #monthsSpanned})
	 * @param totalMonths   total months the flight spans
	 * @return the pace-based projection across the full flight, or {@code null} when {@code actual} is
	 * {@code null} or {@code elapsedMonths} is not positive
	 */
	Double projection(Double actual, int elapsedMonths, int totalMonths) {
		if (actual == null || elapsedMonths <= 0) {
			return null;
		}
		return actual / elapsedMonths * totalMonths;
	}

	/**
	 * Formats the variance of the to-date actual against its prorated goal: a signed percentage-point
	 * delta for rate metrics (CTR/VCR, already expressed as percentages) or a signed relative
	 * percentage for count/currency metrics (impressions, spend, ...), collapsing to {@code "on plan"}
	 * within a small tolerance band.
	 *
	 * @param actual  the to-date actual metric value
	 * @param planCtd the to-date prorated goal for the same metric
	 * @param isRate  {@code true} for a rate metric (CTR/VCR: variance in percentage points),
	 *                {@code false} for a count/currency metric (variance as a relative percentage)
	 * @return the formatted variance (e.g. {@code "+11%"}, {@code "+0.3pp"}, {@code "on plan"}), or
	 * {@code null} when a relative percentage can't be computed ({@code planCtd <= 0})
	 */
	String paceVariance(double actual, double planCtd, boolean isRate) {
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
	 * Formats a date's calendar month and year, e.g. {@code "October 2025"}.
	 *
	 * @param date the date whose month/year to format
	 * @return the {@code "MMMM yyyy"} label
	 */
	String monthLabel(LocalDate date) {
		return date.format(MONTH_YEAR);
	}

	/**
	 * Reads a month label back into the first day of the month it names, e.g. {@code "October 2025"} into
	 * 2025-10-01.
	 *
	 * <p>The inverse of {@link #monthLabel}, so a month the deck has already printed can be stepped forward
	 * without re-deriving it from the plan. A label the formatter cannot read — a hand-typed cell, a range,
	 * an empty string — comes back {@code null} rather than throwing, and the caller dashes its token.
	 *
	 * @param label the {@code "MMMM yyyy"} label to read (may be null or blank)
	 * @return the first day of the month named, or {@code null} when the label is not one
	 */
	LocalDate monthFromLabel(String label) {
		if (label == null || label.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(label.trim(), MONTH_YEAR).atDay(1);
		} catch (DateTimeParseException ex) {
			return null;
		}
	}

	/**
	 * Formats a date's calendar month name only (no year), e.g. {@code "November"} — used for the
	 * "next month" focus slide, which names the upcoming month without restating the year.
	 *
	 * @param date the date whose month to format
	 * @return the {@code "MMMM"} label
	 */
	String monthNameOnly(LocalDate date) {
		return date.format(MONTH_ONLY);
	}
}
