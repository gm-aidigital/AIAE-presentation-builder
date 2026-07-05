package com.aidigital.reportconstructor.service.admin.services;

import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

import java.util.List;

/**
 * Admin read side for team-wide report history (every user's reports).
 */
public interface AdminReportsService {

	/**
	 * Lists every user's reports, newest first, for an admin caller.
	 *
	 * @param callerEmail email of the requesting user, checked against the admin allow-list
	 * @return all reports, newest first, with owner fields populated
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException with
	 *         {@code C004} when the caller is not an admin
	 */
	List<ReportSummary> allReports(String callerEmail);
}
