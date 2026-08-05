package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * Maps a calendar day to the week or month bucket it belongs to.
 *
 * <p>All three answers a bucket needs — its id, its first day and its label — come from here, so a
 * series built by one class and a comparison built by another can never disagree about where a week
 * starts. Weeks are ISO weeks (Monday to Sunday) and are keyed by ISO week-year rather than calendar
 * year, which is what keeps the days either side of New Year in a single bucket instead of splitting
 * one week across two labels.
 */
@Component
public class AdminPeriodBucketer {

	/**
	 * First day of the bucket a date belongs to.
	 *
	 * @param day  the date
	 * @param unit the bucket granularity
	 * @return the bucket's first day
	 */
	public LocalDate startOf(LocalDate day, AdminPeriodUnit unit) {
		return unit == AdminPeriodUnit.MONTH
				? day.withDayOfMonth(1)
				: day.minusDays(day.getDayOfWeek().getValue() - 1L);
	}

	/**
	 * First day of the bucket immediately before the one a date belongs to.
	 *
	 * @param day  the date
	 * @param unit the bucket granularity
	 * @return the previous bucket's first day
	 */
	public LocalDate previousStart(LocalDate day, AdminPeriodUnit unit) {
		LocalDate start = startOf(day, unit);
		return unit == AdminPeriodUnit.MONTH ? start.minusMonths(1) : start.minusWeeks(1);
	}

	/**
	 * Stable id of the bucket a date belongs to, used to join a bucket to its predecessor.
	 *
	 * @param day  the date
	 * @param unit the bucket granularity
	 * @return {@code 2026-W32} for a week, {@code 2026-08} for a month
	 */
	public String keyOf(LocalDate day, AdminPeriodUnit unit) {
		if (unit == AdminPeriodUnit.MONTH) {
			return String.format(Locale.ROOT, "%04d-%02d", day.getYear(), day.getMonthValue());
		}
		return String.format(Locale.ROOT, "%04d-W%02d",
				day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}

	/**
	 * Short human label for the bucket a date belongs to.
	 *
	 * @param day  the date
	 * @param unit the bucket granularity
	 * @return {@code Aug 3} for a week (its Monday), {@code Aug 2026} for a month
	 */
	public String labelOf(LocalDate day, AdminPeriodUnit unit) {
		LocalDate start = startOf(day, unit);
		String month = start.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
		return unit == AdminPeriodUnit.MONTH
				? month + " " + start.getYear()
				: month + " " + start.getDayOfMonth();
	}
}
