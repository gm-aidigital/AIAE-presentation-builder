package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
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
				ownerEmail,
				ownerEmail == null || ownerEmail.isBlank() ? null : displayNameHelper.fromEmail(ownerEmail));
	}

	/**
	 * Builds a human-friendly row title from the report type, e.g. {@code "EOM report"}.
	 *
	 * @param job the persisted report job
	 * @return a non-blank title for the row
	 */
	String title(ReportJobEntity job) {
		String type = job.getReportTypeCode();
		if (type == null || type.isBlank()) {
			return "Report";
		}
		return type.trim().toUpperCase(Locale.ROOT) + " report";
	}
}
