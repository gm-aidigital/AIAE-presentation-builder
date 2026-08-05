package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.config.UsageRollupProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Turns the dates a client asked for into the window the dashboard will actually report on.
 *
 * <p>Everything the client sends is treated as a suggestion. Dates arrive from a URL, so they can be
 * absent, reversed, or a decade wide; each of those has an obvious right answer, and none of them is
 * an error worth showing a person. Reversed ends are swapped, an open end is filled from the other,
 * and the whole thing is clamped to the history the rollup actually keeps — asking for 2019 cannot
 * make the dashboard scan further back than it has data for.
 */
@Component
@RequiredArgsConstructor
public class AdminDateRangeResolver {

	/** Days covered when the client names no range at all. */
	private static final int DEFAULT_SPAN_DAYS = 30;

	/**
	 * Longest span still reported week by week.
	 *
	 * <p>Above this, weekly buckets stop being readable — a quarter is thirteen bars nobody compares —
	 * and months are the unit people actually reason in. Deliberately a little over a calendar month,
	 * so a window like 25 July – 5 August stays weekly instead of collapsing into two stub months.
	 */
	private static final int WEEKLY_MAX_SPAN_DAYS = 40;

	private final UsageRollupProperties props;

	/**
	 * Resolves the requested dates into a usable window.
	 *
	 * @param from  first day the client asked for, or {@code null}
	 * @param to    last day the client asked for, or {@code null}
	 * @param today the current date, which bounds how far forward the window may reach
	 * @return the window to report on, with its default trend granularity
	 */
	public AdminDateRange resolve(LocalDate from, LocalDate to, LocalDate today) {
		LocalDate earliest = today.minusDays(Math.max(1, props.getHistoryDays()));

		LocalDate start = from;
		LocalDate end = to;
		if (start == null && end == null) {
			end = today;
			start = today.minusDays(DEFAULT_SPAN_DAYS - 1L);
		} else if (start == null) {
			start = end.minusDays(DEFAULT_SPAN_DAYS - 1L);
		} else if (end == null) {
			end = today;
		}
		if (start.isAfter(end)) {
			LocalDate swapped = start;
			start = end;
			end = swapped;
		}
		if (start.isBefore(earliest)) {
			start = earliest;
		}
		if (end.isAfter(today)) {
			end = today;
		}
		if (start.isAfter(end)) {
			// Only reachable when the whole requested window sits in the future; report the one day
			// that is both requested and real rather than an empty window.
			start = end;
		}
		return new AdminDateRange(start, end, unitFor(start, end));
	}

	/**
	 * Picks the trend granularity a span reads best at.
	 *
	 * @param from first day, inclusive
	 * @param to   last day, inclusive
	 * @return {@link AdminPeriodUnit#WEEK} for a short span, {@link AdminPeriodUnit#MONTH} beyond it
	 */
	public AdminPeriodUnit unitFor(LocalDate from, LocalDate to) {
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		return days <= WEEKLY_MAX_SPAN_DAYS ? AdminPeriodUnit.WEEK : AdminPeriodUnit.MONTH;
	}
}
