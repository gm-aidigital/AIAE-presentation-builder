package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated admin dashboard payload: totals, per-user and per-type breakdowns, a 7-day
 * volume series, Claude token spend, and the recent failures list — all computed from the
 * {@code report_jobs} table.
 *
 * @param updatedAt   when the aggregation ran
 * @param totals      headline counters
 * @param byUser      per-user activity and token spend, most reports first
 * @param byType      counts per report type, most reports first
 * @param weekly      the last 7 days of volume, oldest first
 * @param tokens      team-wide Claude token consumption and its estimated cost
 * @param tokenWeekly the last 7 days of token spend, oldest first
 * @param failures    the most recent failed jobs, newest first
 */
public record AdminStats(
		LocalDateTime updatedAt,
		AdminTotals totals,
		List<AdminUserStat> byUser,
		List<AdminTypeStat> byType,
		List<AdminDayVolume> weekly,
		AdminTokenTotals tokens,
		List<AdminTokenDay> tokenWeekly,
		List<AdminFailedJob> failures) {
}
