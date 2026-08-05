package com.aidigital.reportconstructor.service.admin.services;

import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;

/**
 * Admin read side for team-wide report history (every user's reports).
 */
public interface AdminReportsService {

	/**
	 * Lists one page of every user's reports, in the requested order, for an admin caller.
	 *
	 * <p>Paged and sorted in the database rather than in the browser: the history grows without bound,
	 * and the table only ever shows a screenful of it.
	 *
	 * @param callerEmail email of the requesting user, checked against the admin allow-list
	 * @param page        zero-based page index; negative values are read as the first page
	 * @param size        rows per page; clamped to a sane range, defaulted when absent
	 * @param sort        wire code of the column to order by; unknown codes fall back to the default
	 * @param dir         {@code asc} for ascending, anything else for descending
	 * @return the requested page, with owner fields populated and the unfiltered total attached
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException with
	 *         {@code C004} when the caller is not an admin
	 */
	AdminReportPage allReports(String callerEmail, Integer page, Integer size, String sort, String dir);
}
