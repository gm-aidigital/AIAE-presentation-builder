package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

import java.util.List;

/**
 * Read side for the "My reports" screen: lists a single user's report jobs, and serves the
 * resumable drafts among them.
 */
public interface ReportHistoryService {

	/**
	 * Lists the given owner's reports, newest first, mapped to compact summaries. Includes the
	 * finished sheet builds the owner can still resume, flagged as drafts.
	 *
	 * @param userId internal owner id whose history is returned
	 * @return the owner's reports, newest first (possibly empty)
	 */
	List<ReportSummary> historyForOwner(String userId);

	/**
	 * Loads the state needed to re-enter the wizard at the review step for one of the owner's drafts.
	 *
	 * @param userId internal owner id that must own the job
	 * @param jobId  id of the draft to resume
	 * @return the draft's workbook and stored wizard state
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException when the job does not
	 *         exist, belongs to someone else, or is not a resumable draft
	 */
	ReportResume resumeForOwner(String userId, Long jobId);

	/**
	 * Dismisses one of the owner's drafts so it stops being offered. Neither the job nor the
	 * generated workbook is deleted.
	 *
	 * @param userId internal owner id that must own the job
	 * @param jobId  id of the draft to dismiss
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException when the job does not
	 *         exist or belongs to someone else
	 */
	void dismissForOwner(String userId, Long jobId);
}
