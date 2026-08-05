package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;

/**
 * Per-user report activity for the admin dashboard's "By user" table and the token tab's
 * per-user spend breakdown.
 *
 * @param userId       internal owner id
 * @param email        owner email (may be {@code null} for legacy rows)
 * @param name         display name derived from the email local part
 * @param total        reports the user created in the selected window
 * @param slides       slides those reports shipped, measured where known and modelled where not
 * @param lastActivity the user's most recent report overall, not clamped to the window
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
		long slides,
		LocalDateTime lastActivity,
		long inputTokens,
		long outputTokens,
		long cacheTokens,
		long totalTokens,
		double costUsd) {
}
