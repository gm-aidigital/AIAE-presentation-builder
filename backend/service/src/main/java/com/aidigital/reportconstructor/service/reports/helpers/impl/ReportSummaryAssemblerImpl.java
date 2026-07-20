package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Default {@link ReportSummaryAssembler}.
 */
@Component
@RequiredArgsConstructor
public class ReportSummaryAssemblerImpl implements ReportSummaryAssembler {

	private final DisplayNameHelper displayNameHelper;
	private final JobTokenUsage tokenUsage;

	@Override
	public ReportSummary toSummary(ReportJobEntity job) {
		String ownerEmail = job.getOwnerEmail();
		return new ReportSummary(
				job.getId(),
				job.getReportTypeCode(),
				job.getStatus(),
				title(job),
				job.getCreatedAt() == null ? null : job.getCreatedAt().toLocalDateTime(),
				job.getSlideUrl(),
				job.getSheetUrl(),
				job.getArtifactName(),
				job.getMediaPlanUrl(),
				job.getElevateUrl(),
				ownerEmail,
				ownerEmail == null || ownerEmail.isBlank() ? null : displayNameHelper.fromEmail(ownerEmail),
				tokenUsage.allInputTokens(job),
				tokenUsage.outputTokens(job),
				tokenUsage.totalTokens(job),
				tokenUsage.costUsd(job));
	}

	/**
	 * Builds a display title for the row — the generated file name when known, else a
	 * type-derived label like {@code "EOM report"}.
	 *
	 * @param job the persisted report job
	 * @return a non-blank title for the row
	 */
	String title(ReportJobEntity job) {
		if (job.getArtifactName() != null && !job.getArtifactName().isBlank()) {
			return job.getArtifactName();
		}
		String type = job.getReportTypeCode();
		if (type == null || type.isBlank()) {
			return "Report";
		}
		return type.trim().toUpperCase(Locale.ROOT) + " report";
	}
}
