package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;

/**
 * One admin grant for the "Manage admins" list.
 *
 * @param email        the admin's email
 * @param source       {@code "config"} (root, from the allow-list) or {@code "managed"} (UI-added)
 * @param addedByEmail who granted a managed admin, else {@code null}
 * @param createdAt    when a managed grant was created, else {@code null}
 * @param removable    whether this grant can be revoked via the UI (managed only)
 */
public record AdminEntry(
		String email,
		String source,
		String addedByEmail,
		LocalDateTime createdAt,
		boolean removable) {
}
