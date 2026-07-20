package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDateTime;

/**
 * One failed report job, with enough context to tell what broke without opening the server logs:
 * whose report it was, which pipeline step it died on, and the recorded failure message.
 *
 * <p>{@code step}/{@code stepLabel} are the last progress values the job reached before the failure
 * — the pipeline stamps them as it advances and does not clear them when it marks the job failed —
 * so they name the stage that threw.
 *
 * @param jobId        surrogate report-job id
 * @param type         report type code, or {@code null} when unknown
 * @param title        human-friendly report name
 * @param ownerEmail   report owner's email, or {@code null} for legacy rows
 * @param ownerName    report owner's display name, or {@code null}
 * @param createdAt    when the job was created
 * @param failedAt     when the job was last updated, i.e. when it failed
 * @param step         1-based pipeline step the job had reached
 * @param total        number of steps in the pipeline
 * @param stepLabel    human-readable name of that step, e.g. {@code "Claude — executive batch (C)"}
 * @param errorMessage recorded failure message, or {@code null} when none was captured
 * @param totalTokens  tokens the failed run had already consumed
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
		String errorMessage,
		long totalTokens,
		double costUsd) {
}
