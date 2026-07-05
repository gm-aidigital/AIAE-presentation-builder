package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;

/**
 * Per-user report activity for the admin dashboard's "By user" table.
 *
 * @param userId       internal owner id
 * @param email        owner email (may be {@code null} for legacy rows)
 * @param name         display name derived from the email local part
 * @param total        total reports the user created
 * @param thisMonth    reports the user created this calendar month
 * @param lastActivity local date-time of the user's most recent report, or {@code null}
 */
public record AdminUserStat(
		String userId,
		String email,
		String name,
		int total,
		int thisMonth,
		LocalDateTime lastActivity) {
}
