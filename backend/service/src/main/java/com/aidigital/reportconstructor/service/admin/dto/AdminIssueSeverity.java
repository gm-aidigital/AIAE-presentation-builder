package com.aidigital.reportconstructor.service.admin.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Severity of a report-job issue surfaced in the dashboard's failures list.
 *
 * <p>An {@link #ERROR} is a hard failure — the run threw and produced no report. A {@link #WARNING}
 * is a report that completed but shipped degraded, e.g. a breakdown slide whose Claude copy came
 * back empty. Both are worth an admin's attention, so both appear in the list; the severity code
 * lets the dashboard tell them apart without inflating the hard-failure counter.
 */
@Getter
@RequiredArgsConstructor
public enum AdminIssueSeverity {

	/** A run that threw and produced no report. */
	ERROR("error"),

	/** A report that completed but shipped with generation warnings. */
	WARNING("warning");

	/** Wire code sent to the dashboard. */
	private final String code;
}
