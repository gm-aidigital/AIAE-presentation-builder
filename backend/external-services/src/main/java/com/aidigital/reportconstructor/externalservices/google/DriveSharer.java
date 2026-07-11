package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Grants standing access to generated Drive files (decks) by creating a
 * {@code writer} permission per configured email. Kept separate from the deck
 * providers so the sharing policy is one testable collaborator rather than
 * inline copy-paste in each provider.
 */
@Slf4j
@Component
public class DriveSharer {

	private static final String ROLE_WRITER = "writer";
	private static final String TYPE_USER = "user";

	/**
	 * Best-effort shares the given Drive file with each supplied email as a
	 * writer. A failure to share one recipient is logged and swallowed so it
	 * never fails the surrounding report generation — the deck already exists and
	 * is returned regardless of who it could be shared with.
	 *
	 * @param drive  authenticated Drive client used to issue the permission calls
	 * @param fileId Drive file id of the deck to share
	 * @param emails email addresses to grant writer access to; null/empty is a no-op
	 */
	public void shareWith(Drive drive, String fileId, List<String> emails) {
		if (emails == null || emails.isEmpty()) {
			return;
		}
		for (String email : emails) {
			if (email == null || email.isBlank()) {
				continue;
			}
			String recipient = email.trim();
			try {
				Permission permission = new Permission()
						.setType(TYPE_USER)
						.setRole(ROLE_WRITER)
						.setEmailAddress(recipient);
				drive.permissions().create(fileId, permission)
						.setSendNotificationEmail(false)
						.setSupportsAllDrives(true)
						.execute();
			} catch (IOException ex) {
				log.warn("[drive] failed to share file {} with {}: {}", fileId, recipient, ex.getMessage());
			}
		}
	}
}
