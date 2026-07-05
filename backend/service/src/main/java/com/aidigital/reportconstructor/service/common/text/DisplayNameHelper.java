package com.aidigital.reportconstructor.service.common.text;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Derives human-friendly display names from emails, e.g. {@code alex.turner@x.com}
 * → {@code Alex Turner}. Shared so admin stats and report history render names the
 * same way.
 */
@Component
public class DisplayNameHelper {

	/**
	 * Builds a display name from an email's local part.
	 *
	 * @param email owner email, possibly {@code null}/blank
	 * @return a human-friendly name, or {@code "Unknown"} when no email is present
	 */
	public String fromEmail(String email) {
		if (email == null || email.isBlank()) {
			return "Unknown";
		}
		String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
		String[] parts = local.split("[._-]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.length() == 0 ? email : out.toString();
	}
}
