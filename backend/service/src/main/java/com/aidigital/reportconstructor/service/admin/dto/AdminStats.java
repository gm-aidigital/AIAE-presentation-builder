package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated admin dashboard payload: totals, per-user and per-type breakdowns, and
 * a 7-day volume series computed from the {@code report_jobs} table.
 *
 * @param updatedAt when the aggregation ran
 * @param totals    headline counters
 * @param byUser    per-user activity, most reports first
 * @param byType    counts per report type, most reports first
 * @param weekly    the last 7 days of volume, oldest first
 */
public record AdminStats(
		LocalDateTime updatedAt,
		AdminTotals totals,
		List<AdminUserStat> byUser,
		List<AdminTypeStat> byType,
		List<AdminDayVolume> weekly) {
}
