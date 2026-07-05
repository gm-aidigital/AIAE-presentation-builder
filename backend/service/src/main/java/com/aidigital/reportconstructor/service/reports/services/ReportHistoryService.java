package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

import java.util.List;

/**
 * Read side for the "My reports" screen: lists a single user's report jobs.
 */
public interface ReportHistoryService {

	/**
	 * Lists the given owner's reports, newest first, mapped to compact summaries.
	 *
	 * @param userId internal owner id whose history is returned
	 * @return the owner's reports, newest first (possibly empty)
	 */
	List<ReportSummary> historyForOwner(String userId);
}
