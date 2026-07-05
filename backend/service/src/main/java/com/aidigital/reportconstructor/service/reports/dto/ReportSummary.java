package com.aidigital.reportconstructor.service.reports.dto;

import java.time.LocalDateTime;

/**
 * One row of report history. The owner fields are populated for the admin
 * all-reports view and ignored by the per-user "My reports" screen.
 *
 * @param jobId      surrogate report-job id
 * @param type       report type code (e.g. EOM/EOC), or {@code null} when unknown
 * @param status     lifecycle status wire code (queued/running/done/error)
 * @param title      human-friendly report name for the row
 * @param createdAt  when the job was created (local date-time, matching the API contract)
 * @param slideUrl   generated artifact URL when done, else {@code null}
 * @param ownerEmail report owner email, or {@code null} when unknown
 * @param ownerName  report owner display name derived from the email
 */
public record ReportSummary(
		Long jobId,
		String type,
		String status,
		String title,
		LocalDateTime createdAt,
		String slideUrl,
		String ownerEmail,
		String ownerName) {
}
