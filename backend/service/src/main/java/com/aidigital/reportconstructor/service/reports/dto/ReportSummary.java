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
 * @param slideUrl     generated artifact (deck) URL when done, else {@code null}
 * @param sheetUrl     source/associated Google Sheet URL, or {@code null} when none
 * @param fileName     human file name of the generated artifact, or {@code null} when unknown
 * @param mediaPlanUrl Media Plan source sheet the user connected, or {@code null}
 * @param elevateUrl   Elevate source sheet the user connected, or {@code null}
 * @param ownerEmail   report owner email, or {@code null} when unknown
 * @param ownerName    report owner display name derived from the email
 * @param inputTokens  Claude input tokens the run billed (plain + cache), 0 when unrecorded
 * @param outputTokens Claude output tokens the run billed, 0 when unrecorded
 * @param totalTokens  every token the run billed, 0 when unrecorded
 * @param costUsd      estimated cost of those tokens at configured list prices
 */
public record ReportSummary(
		Long jobId,
		String type,
		String status,
		String title,
		LocalDateTime createdAt,
		String slideUrl,
		String sheetUrl,
		String fileName,
		String mediaPlanUrl,
		String elevateUrl,
		String ownerEmail,
		String ownerName,
		long inputTokens,
		long outputTokens,
		long totalTokens,
		double costUsd) {
}
