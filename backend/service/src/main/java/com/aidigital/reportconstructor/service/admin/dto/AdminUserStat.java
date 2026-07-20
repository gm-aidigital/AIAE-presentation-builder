package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;

/**
 * Per-user report activity for the admin dashboard's "By user" table and the token tab's
 * per-user spend breakdown.
 *
 * @param userId       internal owner id
 * @param email        owner email (may be {@code null} for legacy rows)
 * @param name         display name derived from the email local part
 * @param total        total reports the user created
 * @param thisMonth    reports the user created this calendar month
 * @param lastActivity local date-time of the user's most recent report, or {@code null}
 * @param inputTokens  plain input tokens across the user's reports
 * @param outputTokens output tokens across the user's reports
 * @param cacheTokens  cache write + read tokens across the user's reports
 * @param totalTokens  every token above, summed
 * @param costUsd      estimated cost of the user's reports at configured list prices
 */
public record AdminUserStat(
		String userId,
		String email,
		String name,
		int total,
		int thisMonth,
		LocalDateTime lastActivity,
		long inputTokens,
		long outputTokens,
		long cacheTokens,
		long totalTokens,
		double costUsd) {
}
