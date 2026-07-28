package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Pure EOM plan-value math: prorates a tactic's monthly budget onto the reporting period, then
 * converts the prorated spend into Plan Units (impressions/clicks/views) per its rate type.
 */
@Component
public class RatePlanCalculator {

	/**
	 * Prorates a tactic's monthly budget onto {@code [periodStart, periodEnd]}, summing each
	 * overlapping calendar month's share by day count. Collapses to {@code monthlyBudget}
	 * unchanged when the period is exactly one full calendar month (the normal "one report = one
	 * month" case); a period covering only part of a month at either edge gets only that month's
	 * day share.
	 *
	 * @param monthlyBudget the tactic's monthly budget entered at matching time
	 * @param periodStart   first day of the reporting period (inclusive)
	 * @param periodEnd     last day of the reporting period (inclusive)
	 * @return the prorated spend for the period, or {@code null} when any input is missing or the
	 * range is inverted
	 */
	Double proratedBudget(Double monthlyBudget, LocalDate periodStart, LocalDate periodEnd) {
		if (monthlyBudget == null || periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
			return null;
		}
		double total = 0;
		LocalDate cursor = periodStart;
		while (!cursor.isAfter(periodEnd)) {
			YearMonth month = YearMonth.from(cursor);
			LocalDate monthEnd = month.atEndOfMonth();
			LocalDate sliceEnd = monthEnd.isBefore(periodEnd) ? monthEnd : periodEnd;
			long daysInSlice = ChronoUnit.DAYS.between(cursor, sliceEnd) + 1;
			total += monthlyBudget * daysInSlice / month.lengthOfMonth();
			cursor = sliceEnd.plusDays(1);
		}
		return total;
	}

	/**
	 * Converts a prorated spend figure into Plan Units for the given rate type: planned
	 * impressions for CPM ({@code spend / price × 1000}), planned clicks for CPC, planned views
	 * for CPV (both {@code spend / price}).
	 *
	 * @param proratedSpend the period's prorated spend, see {@link #proratedBudget}
	 * @param unitPrice     the final unit price entered at matching time
	 * @param rateType      how the tactic's cost is bought
	 * @return the Plan Units figure, or {@code null} when any input is missing, {@code rateType} is
	 * {@code null}, or {@code unitPrice} is not positive
	 */
	Double planUnits(Double proratedSpend, Double unitPrice, RateType rateType) {
		if (proratedSpend == null || unitPrice == null || unitPrice <= 0 || rateType == null) {
			return null;
		}
		return switch (rateType) {
			case CPM -> proratedSpend / unitPrice * 1000;
			case CPC, CPV -> proratedSpend / unitPrice;
		};
	}
}
