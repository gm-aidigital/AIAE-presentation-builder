package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportDraftPolicy;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Default {@link ReportHistoryService} — reads the owner's jobs through the report-job entity
 * helper and maps each listed row to a display-ready {@link ReportSummary}.
 */
@Service
@RequiredArgsConstructor
public class ReportHistoryServiceImpl implements ReportHistoryService {

	private final ReportJobProgressHelper jobs;
	private final ReportSummaryAssembler assembler;
	/** Decides which of the owner's intermediate sheet builds are still worth offering as drafts. */
	private final ReportDraftPolicy draftPolicy;
	private final ReportResumeStateHelper resumeState;

	@Override
	public List<ReportSummary> historyForOwner(String userId) {
		List<ReportJobEntity> owned = jobs.listJobsByOwner(userId);
		// Computed once over the same list the rows are built from rather than queried per row: the
		// owner's history is already fully loaded, so this costs one pass and no extra round trip.
		Set<String> consumed = draftPolicy.consumedSheetUrls(owned);
		List<ReportSummary> rows = new ArrayList<>();
		for (ReportJobEntity job : owned) {
			if (!draftPolicy.isListed(job, consumed)) {
				continue;
			}
			rows.add(assembler.toSummary(job, draftPolicy.isDraft(job, consumed)));
		}
		return rows;
	}

	@Override
	public ReportResume resumeForOwner(String userId, Long jobId) {
		ReportJobEntity job = jobs.loadJobForOwner(userId, jobId);
		Set<String> consumed = draftPolicy.consumedSheetUrls(jobs.listJobsByOwner(userId));
		if (!draftPolicy.isDraft(job, consumed)) {
			throw new AppException(ErrorReason.C001, "No resumable draft for job " + jobId);
		}
		return new ReportResume(
				job.getId(),
				job.getSheetUrl(),
				job.getMediaPlanUrl(),
				job.getElevateUrl(),
				resumeState.parse(job.getPayloadJson()));
	}

	@Override
	public void dismissForOwner(String userId, Long jobId) {
		// Ownership is the whole authorization check. Dismissing a job that is not a draft changes
		// nothing visible, so it is not rejected — the operation stays idempotent for the caller.
		jobs.loadJobForOwner(userId, jobId);
		jobs.dismissJob(jobId);
	}
}
