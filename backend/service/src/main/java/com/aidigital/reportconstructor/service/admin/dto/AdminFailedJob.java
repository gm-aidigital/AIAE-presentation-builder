package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One report-job issue, with enough context to tell what broke without opening the server logs:
 * whose report it was, which pipeline step it reached, and the recorded message. Covers both hard
 * failures (the run threw) and reports that completed but shipped degraded — {@code severity} tells
 * the two apart.
 *
 * <p>{@code step}/{@code stepLabel} are the last progress values the job reached — the pipeline
 * stamps them as it advances — so for a hard failure they name the stage that threw and for a
 * degraded report they name the final step it completed.
 *
 * @param jobId        surrogate report-job id
 * @param type         report type code, or {@code null} when unknown
 * @param title        human-friendly report name
 * @param ownerEmail   report owner's email, or {@code null} for legacy rows
 * @param ownerName    report owner's display name, or {@code null}
 * @param createdAt    when the job was created
 * @param failedAt     when the job was last updated, i.e. when it failed or finished
 * @param step         1-based pipeline step the job had reached
 * @param total        number of steps in the pipeline
 * @param stepLabel    human-readable name of that step, e.g. {@code "Claude — executive batch (C)"}
 * @param severity     {@code "error"} for a hard failure, {@code "warning"} for a degraded report
 * @param errorMessage recorded failure message (hard failure) or a short summary (degraded report)
 * @param warnings     per-slide generation warnings for a degraded report; empty for a hard failure
 * @param totalTokens  tokens the run consumed
 * @param costUsd      estimated cost of those tokens
 */
public record AdminFailedJob(
		Long jobId,
		String type,
		String title,
		String ownerEmail,
		String ownerName,
		LocalDateTime createdAt,
		LocalDateTime failedAt,
		int step,
		int total,
		String stepLabel,
		String severity,
		String errorMessage,
		List<String> warnings,
		long totalTokens,
		double costUsd) {
}
