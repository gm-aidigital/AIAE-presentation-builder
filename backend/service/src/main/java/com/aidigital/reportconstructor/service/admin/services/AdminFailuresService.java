package com.aidigital.reportconstructor.service.admin.services;

/**
 * Admin-only maintenance of the dashboard's failures list. Lets an admin clear resolved issues so
 * the list — and the failed counter — return to zero and show only what broke since.
 *
 * <p>Clearing is severity-aware: a hard failure produced no report and is deleted outright along
 * with its spend rows, while a report that merely shipped with warnings keeps its report and only
 * loses the warning flag.
 */
public interface AdminFailuresService {

	/**
	 * Clears one issue by its job id.
	 *
	 * @param callerEmail email of the calling admin, checked against the allow-list
	 * @param jobId       the report job behind the failures row
	 */
	void resolveFailure(String callerEmail, Long jobId);

	/**
	 * Clears every current issue — all hard failures and all degraded-report warnings.
	 *
	 * @param callerEmail email of the calling admin, checked against the allow-list
	 */
	void clearFailures(String callerEmail);
}
