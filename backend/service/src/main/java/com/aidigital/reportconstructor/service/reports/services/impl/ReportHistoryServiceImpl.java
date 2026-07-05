package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link ReportHistoryService} — reads the owner's jobs through the report-job
 * entity helper and maps each deliverable to a display-ready {@link ReportSummary}.
 */
@Service
@RequiredArgsConstructor
public class ReportHistoryServiceImpl implements ReportHistoryService {

	private final ReportJobProgressHelper jobs;
	private final ReportSummaryAssembler assembler;

	@Override
	public List<ReportSummary> historyForOwner(String userId) {
		return jobs.listJobsByOwner(userId).stream()
				.filter(this::isDeliverable)
				.map(assembler::toSummary)
				.toList();
	}

	/**
	 * Keeps deliverable report jobs (decks) out of the intermediate SHEET-assembly jobs, so
	 * each report shows as one row. Legacy rows with no target are treated as deliverables.
	 *
	 * @param job the persisted report job
	 * @return true unless the job is a SHEET-assembly job
	 */
	boolean isDeliverable(ReportJobEntity job) {
		return !GenerationTarget.SHEET.name().equals(job.getTarget());
	}
}
