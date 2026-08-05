package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated admin dashboard payload for one window of dates.
 *
 * <p>Everything except the live operational counters and the failures list is derived from the
 * {@code usage_daily} rollup rather than from a pass over {@code report_jobs}, which is what lets the
 * dashboard keep answering as the jobs table grows.
 *
 * <p>{@code range} is echoed back rather than assumed: what a client asked for and what it is looking
 * at can differ — dates get swapped, clamped to the history that exists, or filled in entirely — and
 * a screen that shows figures for one window while labelling it another is worse than one that
 * refuses. The trend series are returned at both granularities so the client can switch between them
 * without a round trip.
 *
 * @param updatedAt         when the aggregation ran
 * @param rollupUpdatedAt   when the rollup these figures come from was last rebuilt, or {@code null}
 *                          when it has never been built — so a stale dashboard can say so instead of
 *                          presenting old numbers as current
 * @param range             the window actually reported on, after resolution and clamping
 * @param totals            headline counters; the operational ones ignore the window
 * @param savings           modelled time and money the window's reports saved
 * @param byUser            per-user activity and token spend in the window, most reports first
 * @param byType            counts per report type in the window, most reports first
 * @param series            report volume and token spend bucketed for charting, oldest first
 * @param seriesUnit        wire code of the bucket size {@code series} uses: day, week or month
 * @param tokens            team-wide Claude token consumption in the window
 * @param tokensByWeek      token spend per ISO week with week-over-week deltas, oldest first
 * @param tokensByMonth     token spend per calendar month with month-over-month deltas, oldest first
 * @param activeUsersWeeks  active users per ISO week with week-over-week deltas, oldest first
 * @param activeUsersMonths active users per calendar month with month-over-month deltas, oldest first
 * @param byLabel           measured token spend per pipeline stage, most expensive first
 * @param failures          the most recent job issues — hard failures and degraded reports — newest first
 */
public record AdminStats(
		LocalDateTime updatedAt,
		LocalDateTime rollupUpdatedAt,
		AdminRangeView range,
		AdminTotals totals,
		AdminSavings savings,
		List<AdminUserStat> byUser,
		List<AdminTypeStat> byType,
		List<AdminTokenPeriod> series,
		String seriesUnit,
		AdminTokenTotals tokens,
		List<AdminTokenPeriod> tokensByWeek,
		List<AdminTokenPeriod> tokensByMonth,
		List<AdminActiveUsersPeriod> activeUsersWeeks,
		List<AdminActiveUsersPeriod> activeUsersMonths,
		List<AdminTokenLabel> byLabel,
		List<AdminFailedJob> failures) {
}
