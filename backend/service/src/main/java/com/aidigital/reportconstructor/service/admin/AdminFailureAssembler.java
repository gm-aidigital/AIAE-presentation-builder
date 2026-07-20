package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminFailedJob;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns failed report jobs into the dashboard's failures list, pairing each one with the pipeline
 * step it died on and the message it recorded — the two things needed to tell a Google timeout
 * apart from a Claude parse failure without opening the server logs.
 */
@Component
@RequiredArgsConstructor
public class AdminFailureAssembler {

	/** Status wire code the pipeline writes when a run throws. */
	private static final String STATUS_ERROR = "error";

	/** Shown when a job failed without any message being captured. */
	private static final String NO_MESSAGE = "No error message was recorded.";

	private final DisplayNameHelper displayNameHelper;
	private final ReportSummaryAssembler summaryAssembler;
	private final JobTokenUsage tokenUsage;

	/**
	 * Collects the most recent failures, newest first.
	 *
	 * @param all   all report jobs, already ordered newest first
	 * @param limit maximum rows to return
	 * @return the failed jobs, at most {@code limit} of them
	 */
	public List<AdminFailedJob> recentFailures(List<ReportJobEntity> all, int limit) {
		List<AdminFailedJob> failures = new ArrayList<>();
		for (ReportJobEntity job : all) {
			if (failures.size() >= limit) {
				break;
			}
			if (STATUS_ERROR.equals(job.getStatus())) {
				failures.add(toFailure(job));
			}
		}
		return failures;
	}

	/**
	 * Maps one failed job to its dashboard row.
	 *
	 * @param job the failed report job
	 * @return the failure row
	 */
	AdminFailedJob toFailure(ReportJobEntity job) {
		String ownerEmail = job.getOwnerEmail();
		String message = job.getErrorMessage();
		return new AdminFailedJob(
				job.getId(),
				job.getReportTypeCode(),
				summaryAssembler.toSummary(job).title(),
				ownerEmail,
				ownerEmail == null || ownerEmail.isBlank() ? null : displayNameHelper.fromEmail(ownerEmail),
				job.getCreatedAt() == null ? null : job.getCreatedAt().toLocalDateTime(),
				job.getUpdatedAt() == null ? null : job.getUpdatedAt().toLocalDateTime(),
				job.getStep() == null ? 0 : job.getStep(),
				job.getTotal() == null ? 0 : job.getTotal(),
				job.getLabel(),
				message == null || message.isBlank() ? NO_MESSAGE : message,
				tokenUsage.totalTokens(job),
				tokenUsage.costUsd(job));
	}
}
