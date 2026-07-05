package com.aidigital.reportconstructor.service.admin.services;

import com.aidigital.reportconstructor.service.admin.dto.AdminStats;

/**
 * Read side for the admin dashboard: aggregates team-wide report statistics.
 */
public interface AdminStatsService {

	/**
	 * Builds the admin dashboard aggregation, but only for an allow-listed caller.
	 *
	 * @param callerEmail email of the requesting user, checked against the admin allow-list
	 * @return the aggregated team statistics
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException with
	 *         {@code C004} when the caller is not an admin
	 */
	AdminStats statsFor(String callerEmail);
}
