package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminStatsCache;
import com.aidigital.reportconstructor.service.admin.services.AdminFailuresService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link AdminFailuresService}. Validates admin access, then clears failures severity-aware:
 * a hard failure ({@code status = error}) is deleted with its usage events, while a report that
 * completed with warnings keeps its report and only loses its warning flag.
 *
 * <p>The failures list is part of the cached dashboard snapshot, so every clear drops that cache:
 * otherwise the row keeps coming back on the next read even though it is gone from the database.
 */
@Service
@RequiredArgsConstructor
public class AdminFailuresServiceImpl implements AdminFailuresService {

	/** Status wire code the pipeline writes when a run throws. */
	private static final String STATUS_ERROR = "error";

	private final AdminAccessPolicy adminAccessPolicy;
	private final ReportJobProgressHelper jobs;
	private final ClaudeUsageEventService usageEvents;
	private final AdminStatsCache statsCache;

	@Override
	public void resolveFailure(String callerEmail, Long jobId) {
		requireAdmin(callerEmail);
		ReportJobEntity job = jobs.findJob(jobId)
				.orElseThrow(() -> new AppException(ErrorReason.C001, "Unknown job " + jobId));
		clearIssue(job);
		statsCache.invalidate();
	}

	@Override
	public void clearFailures(String callerEmail) {
		requireAdmin(callerEmail);
		for (ReportJobEntity job : jobs.listAllIssues()) {
			if (isIssue(job)) {
				clearIssue(job);
			}
		}
		statsCache.invalidate();
	}

	/**
	 * Clears one job's issue: a hard failure is deleted with its usage events; a degraded report
	 * keeps its report and only loses its warnings. A job that is neither is left untouched.
	 *
	 * @param job the job behind a failures row
	 */
	void clearIssue(ReportJobEntity job) {
		if (STATUS_ERROR.equals(job.getStatus())) {
			usageEvents.deleteByJobId(job.getId());
			jobs.deleteJob(job.getId());
		} else if (hasWarnings(job)) {
			jobs.clearJobWarnings(job.getId());
		}
	}

	/**
	 * Tells whether a job currently appears in the failures list — a hard failure or a report that
	 * shipped with warnings.
	 *
	 * @param job the job under test
	 * @return true when the job is an issue worth clearing
	 */
	boolean isIssue(ReportJobEntity job) {
		return STATUS_ERROR.equals(job.getStatus()) || hasWarnings(job);
	}

	/**
	 * Tells whether a job carries recorded generation warnings.
	 *
	 * @param job the job under test
	 * @return true when the job's warnings JSON holds something
	 */
	boolean hasWarnings(ReportJobEntity job) {
		return job.getWarningsJson() != null && !job.getWarningsJson().isBlank();
	}

	/**
	 * Throws when the caller is not an admin.
	 *
	 * @param callerEmail email of the calling user
	 */
	void requireAdmin(String callerEmail) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
	}
}
