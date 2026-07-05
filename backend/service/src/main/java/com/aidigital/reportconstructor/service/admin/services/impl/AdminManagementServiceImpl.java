package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;
import com.aidigital.reportconstructor.service.admin.services.AdminManagementService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link AdminManagementService}. Config (root) admins from the allow-list are
 * listed as non-removable; managed admins live in the {@code admin_users} table.
 */
@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

	private static final String SOURCE_CONFIG = "config";
	private static final String SOURCE_MANAGED = "managed";

	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminUserService adminUserService;

	@Override
	public List<AdminEntry> listAdmins(String callerEmail) {
		requireAdmin(callerEmail);
		return currentAdmins();
	}

	@Override
	public List<AdminEntry> addAdmin(String callerEmail, String email) {
		requireAdmin(callerEmail);
		String normalized = normalizeValidEmail(email);
		adminUserService.add(normalized, callerEmail);
		return currentAdmins();
	}

	@Override
	public List<AdminEntry> removeAdmin(String callerEmail, String email) {
		requireAdmin(callerEmail);
		if (email == null || email.isBlank()) {
			throw new AppException(ErrorReason.C002, "Email is required");
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (adminAccessPolicy.isConfigAdmin(normalized)) {
			throw new AppException(ErrorReason.C002, "Configured (root) admins cannot be removed here");
		}
		if (!adminUserService.removeByEmail(normalized)) {
			throw new AppException(ErrorReason.C001, "No managed admin for " + normalized);
		}
		return currentAdmins();
	}

	/**
	 * Throws {@code C004} when the caller is not an admin.
	 *
	 * @param callerEmail email of the requesting user
	 */
	void requireAdmin(String callerEmail) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
	}

	/**
	 * Builds the merged admin list — config (root) entries first, then managed entries,
	 * skipping managed duplicates of a config email.
	 *
	 * @return the current admins
	 */
	List<AdminEntry> currentAdmins() {
		List<String> configEmails = adminAccessPolicy.configAdminEmails();
		List<AdminEntry> entries = new ArrayList<>();
		for (String email : configEmails) {
			entries.add(new AdminEntry(email, SOURCE_CONFIG, null, null, false));
		}
		for (AdminUserEntity managed : adminUserService.listAll()) {
			if (configEmails.contains(managed.getEmail().toLowerCase(Locale.ROOT))) {
				continue;
			}
			entries.add(new AdminEntry(
					managed.getEmail(),
					SOURCE_MANAGED,
					managed.getAddedByEmail(),
					managed.getCreatedAt() == null ? null : managed.getCreatedAt().toLocalDateTime(),
					true));
		}
		return entries;
	}

	/**
	 * Normalizes and minimally validates an email to grant.
	 *
	 * @param email raw email input
	 * @return trimmed, lowercased email
	 * @throws AppException {@code C002} when the email is blank or clearly malformed
	 */
	String normalizeValidEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new AppException(ErrorReason.C002, "Email is required");
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		int at = normalized.indexOf('@');
		if (at <= 0 || at == normalized.length() - 1 || !normalized.substring(at).contains(".")) {
			throw new AppException(ErrorReason.C002, "Enter a valid email address");
		}
		return normalized;
	}
}
