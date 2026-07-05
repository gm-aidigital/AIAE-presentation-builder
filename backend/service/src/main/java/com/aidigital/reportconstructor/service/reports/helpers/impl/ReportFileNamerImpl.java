package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring bean implementation of {@link ReportFileNamer}. The date/time stamp uses
 * {@link LocalDateTime#now()}, i.e. the server's default time zone.
 */
@Component
public class ReportFileNamerImpl implements ReportFileNamer {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
	private static final String FALLBACK_TYPE = "REPORT";
	private static final String FALLBACK_CLIENT = "report";
	private static final String FALLBACK_USER = "unknown";
	private static final String SEPARATOR = "_";
	private static final String AT = "@";

	@Override
	public String buildFileName(String reportType, String clientName, String userEmail) {
		String type = orFallback(reportType, FALLBACK_TYPE);
		String client = orFallback(clientName, FALLBACK_CLIENT);
		String user = userLocalPart(userEmail);
		String stamp = LocalDateTime.now().format(STAMP);
		return String.join(SEPARATOR, type, client, stamp, user);
	}

	/**
	 * Returns the trimmed value, or the fallback when it is null or blank.
	 *
	 * @param value    candidate value
	 * @param fallback value returned when {@code value} is null or blank
	 * @return the cleaned value or the fallback
	 */
	String orFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	/**
	 * Extracts the local part (before {@code @}) of an email address, falling back
	 * to {@code unknown} when the email is null or blank.
	 *
	 * @param userEmail email address of the creating user
	 * @return the email local part, the full trimmed value when it carries no {@code @},
	 *         or the {@code unknown} fallback
	 */
	String userLocalPart(String userEmail) {
		if (userEmail == null || userEmail.isBlank()) {
			return FALLBACK_USER;
		}
		String trimmed = userEmail.trim();
		int at = trimmed.indexOf(AT);
		return at > 0 ? trimmed.substring(0, at) : trimmed;
	}
}
