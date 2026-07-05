package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.config.AdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Single source of truth for "is this caller an admin?". An email is an admin when it
 * is on the {@link AdminProperties} config allow-list (root admins) OR has a UI-managed
 * grant in {@link AdminUserService}. All comparisons are case-insensitive.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessPolicy {

	private final AdminProperties adminProperties;
	private final AdminUserService adminUserService;

	/**
	 * Reports whether the given email is an admin (config allow-list or managed grant).
	 *
	 * @param email caller email (any case); {@code null}/blank is never an admin
	 * @return true when the email is a root or managed admin
	 */
	public boolean isAdmin(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		return isConfigAdmin(email) || adminUserService.exists(email);
	}

	/**
	 * Reports whether the email is a root (config allow-list) admin — these cannot be
	 * revoked through the UI.
	 *
	 * @param email caller email (any case)
	 * @return true when the normalized email matches a configured admin email
	 */
	public boolean isConfigAdmin(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		return adminProperties.getEmails().stream()
				.filter(e -> e != null && !e.isBlank())
				.map(e -> e.trim().toLowerCase(Locale.ROOT))
				.anyMatch(normalized::equals);
	}

	/**
	 * Returns the normalized config (root) admin emails, for listing alongside managed grants.
	 *
	 * @return lowercased, de-blanked config admin emails
	 */
	public java.util.List<String> configAdminEmails() {
		return adminProperties.getEmails().stream()
				.filter(e -> e != null && !e.isBlank())
				.map(e -> e.trim().toLowerCase(Locale.ROOT))
				.distinct()
				.toList();
	}
}
