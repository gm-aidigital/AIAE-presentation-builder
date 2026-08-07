package com.aidigital.reportconstructor.service.reports.model;

/**
 * Lifecycle status codes stored in the {@code report_jobs.status} column.
 * Each constant owns its lowercase database value so callers never write raw strings.
 */
public enum ReportJobStatus {

	QUEUED("queued"),
	RUNNING("running"),
	DONE("done"),
	ERROR("error");

	private final String code;

	ReportJobStatus(String code) {
		this.code = code;
	}

	/** Returns the lowercase string value written to the database. */
	public String getCode() {
		return code;
	}
}
