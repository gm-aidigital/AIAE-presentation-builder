package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminFailedJob;
import com.aidigital.reportconstructor.service.admin.dto.AdminIssueSeverity;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns report jobs into the dashboard's failures list. It surfaces two kinds of issue, newest
 * first: a hard failure (the run threw and produced no report) and a degraded report (the run
 * finished but a slide shipped without its Claude copy, recorded as a generation warning). Each row
 * carries the pipeline step it reached and the message it recorded — the two things needed to tell a
 * Google timeout apart from an empty-observations warning without opening the server logs.
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
	private final ReportGenerationWarningsHelper warningsHelper;

	/**
	 * Collects the most recent issues — hard failures and degraded reports — newest first.
	 *
	 * @param all   all report jobs, already ordered newest first
	 * @param limit maximum rows to return
	 * @return the issue rows, at most {@code limit} of them
	 */
	public List<AdminFailedJob> recentFailures(List<ReportJobEntity> all, int limit) {
		List<AdminFailedJob> issues = new ArrayList<>();
		for (ReportJobEntity job : all) {
			if (issues.size() >= limit) {
				break;
			}
			if (STATUS_ERROR.equals(job.getStatus())) {
				issues.add(toError(job));
				continue;
			}
			List<String> warnings = warningsHelper.parseWarnings(job.getWarningsJson());
			if (!warnings.isEmpty()) {
				issues.add(toWarning(job, warnings));
			}
		}
		return issues;
	}

	/**
	 * Maps a hard-failed job to its dashboard row.
	 *
	 * @param job the failed report job
	 * @return the failure row, severity {@code error}
	 */
	AdminFailedJob toError(ReportJobEntity job) {
		String message = job.getErrorMessage();
		return toIssue(
				job,
				AdminIssueSeverity.ERROR,
				message == null || message.isBlank() ? NO_MESSAGE : message,
				List.of());
	}

	/**
	 * Maps a report that finished with generation warnings to its dashboard row.
	 *
	 * @param job      the completed report job
	 * @param warnings the generation warnings the run recorded, never empty
	 * @return the failure row, severity {@code warning}
	 */
	AdminFailedJob toWarning(ReportJobEntity job, List<String> warnings) {
		String summary = warnings.size() == 1
				? "Completed with 1 warning."
				: "Completed with " + warnings.size() + " warnings.";
		return toIssue(job, AdminIssueSeverity.WARNING, summary, warnings);
	}

	/**
	 * Builds a dashboard row from a job, its severity, headline message and warning detail.
	 *
	 * @param job      the report job
	 * @param severity whether this is a hard failure or a degraded report
	 * @param message  the headline message shown on the row
	 * @param warnings per-slide warning detail (empty for a hard failure)
	 * @return the assembled row
	 */
	AdminFailedJob toIssue(
			ReportJobEntity job, AdminIssueSeverity severity, String message, List<String> warnings) {
		String ownerEmail = job.getOwnerEmail();
		ReportSummary summary = summaryAssembler.toSummary(job);
		return new AdminFailedJob(
				job.getId(),
				job.getReportTypeCode(),
				summary.title(),
				summary.slideUrl(),
				ownerEmail,
				ownerEmail == null || ownerEmail.isBlank() ? null : displayNameHelper.fromEmail(ownerEmail),
				job.getCreatedAt() == null ? null : job.getCreatedAt().toLocalDateTime(),
				job.getUpdatedAt() == null ? null : job.getUpdatedAt().toLocalDateTime(),
				job.getStep() == null ? 0 : job.getStep(),
				job.getTotal() == null ? 0 : job.getTotal(),
				job.getLabel(),
				severity.getCode(),
				message,
				warnings,
				tokenUsage.totalTokens(job),
				tokenUsage.costUsd(job));
	}
}
