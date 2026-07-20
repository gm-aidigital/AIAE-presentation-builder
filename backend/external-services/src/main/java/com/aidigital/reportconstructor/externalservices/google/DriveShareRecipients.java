package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves who every generated Drive file (deck and EOC workbook alike) is auto-shared
 * with: the statically configured {@code external.google.share-with-emails} list plus
 * every current admin — both the config allow-list ({@code app.admin.emails}) and the
 * UI-managed grants. Kept as one collaborator so both providers share a single policy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriveShareRecipients {

	private final GoogleProperties googleProperties;
	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminUserService adminUserService;

	/**
	 * Returns the de-duplicated, lowercased list of emails a freshly generated file should
	 * be shared with. Failure to read the managed admin grants is logged and swallowed —
	 * the configured recipients are still returned so report generation never breaks on it.
	 *
	 * @return share recipients, in configuration order then admin order; never null
	 */
	public List<String> resolve() {
		Set<String> recipients = new LinkedHashSet<>();
		addAll(recipients, googleProperties.getShareWithEmails());
		addAll(recipients, adminAccessPolicy.configAdminEmails());
		addAll(recipients, managedAdminEmails());
		return List.copyOf(recipients);
	}

	/**
	 * Reads the UI-managed admin grants, degrading to an empty list when the lookup fails.
	 *
	 * @return managed admin emails, or empty when they could not be read
	 */
	List<String> managedAdminEmails() {
		try {
			List<String> emails = new ArrayList<>();
			for (AdminUserEntity admin : adminUserService.listAll()) {
				emails.add(admin.getEmail());
			}
			return emails;
		} catch (RuntimeException ex) {
			log.warn("[drive] could not read managed admin emails for auto-share: {}", ex.getMessage());
			return List.of();
		}
	}

	/**
	 * Adds every non-blank email of the source list to the target set, normalized to
	 * lowercase so the same address configured in two places is granted access once.
	 *
	 * @param target set collecting the normalized recipients
	 * @param source emails to add; null/blank entries are skipped
	 */
	void addAll(Set<String> target, List<String> source) {
		if (source == null) {
			return;
		}
		for (String email : source) {
			if (email != null && !email.isBlank()) {
				target.add(email.trim().toLowerCase(Locale.ROOT));
			}
		}
	}
}
