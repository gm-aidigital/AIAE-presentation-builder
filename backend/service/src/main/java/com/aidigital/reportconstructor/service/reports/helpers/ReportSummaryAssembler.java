package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

/**
 * Maps a persisted report job to a display-ready {@link ReportSummary}, so the
 * per-user ("My reports") and admin (all-reports) history screens render rows identically.
 */
public interface ReportSummaryAssembler {

	/**
	 * Builds a history row (including owner fields) from a persisted job, as a finished report.
	 *
	 * @param job the persisted report job
	 * @return the display summary for one history row
	 */
	ReportSummary toSummary(ReportJobEntity job);

	/**
	 * Builds a history row from a persisted job, marking it as a resumable draft or not. Only the
	 * per-user history distinguishes the two; the admin view lists finished reports and passes false.
	 *
	 * @param job   the persisted report job
	 * @param draft whether the row is a sheet build the owner can still resume
	 * @return the display summary for one history row
	 */
	ReportSummary toSummary(ReportJobEntity job, boolean draft);
}
