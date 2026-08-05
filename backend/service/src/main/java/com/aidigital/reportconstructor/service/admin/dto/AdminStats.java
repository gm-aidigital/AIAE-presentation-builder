package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated admin dashboard payload.
 *
 * <p>Everything except the live operational counters and the failures list is derived from the
 * {@code usage_daily} rollup rather than from a pass over {@code report_jobs}, which is what lets the
 * dashboard keep answering as the jobs table grows.
 *
 * @param updatedAt         when the aggregation ran
 * @param rollupUpdatedAt   when the rollup the figures come from was last rebuilt, or {@code null}
 *                          when it has never been built — so a stale dashboard can say so instead of
 *                          presenting old numbers as current
 * @param totals            headline counters
 * @param savings           modelled time and money the generated reports saved
 * @param byUser            per-user activity and token spend, most reports first
 * @param byType            counts per report type, most reports first
 * @param weekly            the last 7 days of volume, oldest first
 * @param tokens            team-wide Claude token consumption and its estimated cost
 * @param tokenWeekly       the last 7 days of token spend, oldest first
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
		AdminTotals totals,
		AdminSavings savings,
		List<AdminUserStat> byUser,
		List<AdminTypeStat> byType,
		List<AdminDayVolume> weekly,
		AdminTokenTotals tokens,
		List<AdminTokenDay> tokenWeekly,
		List<AdminTokenPeriod> tokensByWeek,
		List<AdminTokenPeriod> tokensByMonth,
		List<AdminActiveUsersPeriod> activeUsersWeeks,
		List<AdminActiveUsersPeriod> activeUsersMonths,
		List<AdminTokenLabel> byLabel,
		List<AdminFailedJob> failures) {
}
