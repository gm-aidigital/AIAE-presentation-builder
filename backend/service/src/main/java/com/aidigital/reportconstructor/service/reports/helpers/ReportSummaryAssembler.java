package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

/**
 * Maps a persisted report job to a display-ready {@link ReportSummary}, so the
 * per-user ("My reports") and admin (all-reports) history screens render rows identically.
 */
public interface ReportSummaryAssembler {

	/**
	 * Builds a history row (including owner fields) from a persisted job.
	 *
	 * @param job the persisted report job
	 * @return the display summary for one history row
	 */
	ReportSummary toSummary(ReportJobEntity job);
}
