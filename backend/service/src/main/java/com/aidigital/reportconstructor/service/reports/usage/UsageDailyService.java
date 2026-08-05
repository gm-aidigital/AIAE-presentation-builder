package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Entity service for the {@code usage_daily} rollup: the one place the rollup repository is reached.
 *
 * <p>Everything it exposes is an aggregate. There is no "load the rows" method on purpose — the
 * rollup exists so the admin dashboard never again pulls a table into the JVM to add it up, and an
 * entity-returning method would be the first step back to that.
 */
public interface UsageDailyService {

	/**
	 * Rebuilds a window of the rollup from the report jobs it summarises.
	 *
	 * <p>Idempotent: the window is cleared and recomputed, so running it twice leaves the same rows
	 * and a job deleted since the last run stops being counted.
	 *
	 * @param from first day to rebuild, inclusive
	 * @param to   day to stop at, exclusive
	 * @return the number of rollup rows written
	 */
	int rebuild(LocalDate from, LocalDate to);

	/**
	 * Rebuilds the whole rollup, from the earliest report job to today.
	 *
	 * <p>Used to populate the rollup the first time and to repair it wholesale; the routine refresh
	 * rebuilds only a trailing window.
	 *
	 * @return the number of rollup rows written
	 */
	int rebuildAll();

	/**
	 * Sums the rollup by day, keeping the report-type, target and model dimensions the read side
	 * needs to price tokens and to decide which jobs count as reports.
	 *
	 * @param from first day to include, inclusive
	 * @return one row per (day, report type, target, model), oldest first
	 */
	List<UsageDailyBucket> byDay(LocalDate from);

	/**
	 * Sums the rollup by user, keeping the same read-side dimensions as {@link #byDay}.
	 *
	 * @param from       first day to include, inclusive
	 * @param monthStart first day of the current calendar month, for the row's "this month" slice
	 * @return one row per (user, report type, target, model)
	 */
	List<UsageDailyUserRow> byUser(LocalDate from, LocalDate monthStart);

	/**
	 * Lists the distinct (day, user) pairs in a window, from which active users are counted for any
	 * bucket granularity.
	 *
	 * @param from first day to include, inclusive
	 * @return one pair per day a user was active on, oldest first
	 */
	List<UsageActiveDay> activeDays(LocalDate from);

	/**
	 * Reports when the rollup was last rebuilt, so the dashboard can show how fresh its figures are.
	 *
	 * @return the most recent refresh timestamp, or {@code null} when the rollup is empty
	 */
	OffsetDateTime lastRefreshedAt();
}
