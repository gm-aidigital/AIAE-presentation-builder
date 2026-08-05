package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spring bean implementation of {@link ReportJobProgressHelper}.
 */
@Component
@RequiredArgsConstructor
public class ReportJobProgressHelperImpl implements ReportJobProgressHelper {

	/** Wire code of a job that ended in error. */
	private static final String STATUS_ERROR = "error";

	/** Wire codes of a job that has not finished yet, in either order it can be in. */
	private static final List<String> IN_FLIGHT_STATUSES = List.of("queued", "running");

	/** Wire code of the intermediate sheet target, which is a step of a report rather than a report. */
	private static final String SHEET_TARGET = "SHEET";

	private final ReportJobRepository jobs;

	@Transactional
	@Override
	public ReportJobEntity createQueuedJob(String userId, String reportTypeCode) {
		ReportJobEntity job = new ReportJobEntity();
		job.setOwnerUserId(userId);
		job.setStatus("queued");
		job.setStep(0);
		job.setTotal(7);
		job.setLabel("Queued…");
		job.setReportTypeCode(reportTypeCode);
		OffsetDateTime now = OffsetDateTime.now();
		job.setCreatedAt(now);
		job.setUpdatedAt(now);
		return jobs.save(job);
	}

	@Transactional
	@Override
	public void recordJobContext(Long jobId, String ownerEmail, String target, String mediaPlanUrl, String elevateUrl) {
		jobs.findById(jobId).ifPresent(job -> {
			job.setOwnerEmail(ownerEmail);
			job.setTarget(target);
			job.setMediaPlanUrl(mediaPlanUrl);
			job.setElevateUrl(elevateUrl);
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}

	@Transactional
	@Override
	public void recordArtifact(Long jobId, String artifactName, String sheetUrl) {
		jobs.findById(jobId).ifPresent(job -> {
			job.setArtifactName(artifactName);
			job.setSheetUrl(sheetUrl);
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}

	@Transactional
	@Override
	public void recordSlideCount(Long jobId, int slideCount) {
		if (slideCount <= 0) {
			return;
		}
		jobs.findById(jobId).ifPresent(job -> {
			job.setSlideCount(slideCount);
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}

	@Transactional
	@Override
	public void recordTokenUsage(Long jobId, ClaudeUsage usage) {
		if (usage == null || usage.calls() == 0) {
			return;
		}
		jobs.findById(jobId).ifPresent(job -> {
			job.setInputTokens(usage.inputTokens());
			job.setOutputTokens(usage.outputTokens());
			job.setCacheWriteTokens(usage.cacheWriteTokens());
			job.setCacheReadTokens(usage.cacheReadTokens());
			job.setClaudeCalls(usage.calls());
			job.setClaudeModel(usage.model());
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}

	@Transactional(readOnly = true)
	@Override
	public List<ReportJobEntity> listJobsByOwner(String userId) {
		return jobs.findByOwnerUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	@Override
	public List<ReportJobEntity> listAllJobs() {
		return jobs.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	@Override
	public int countInFlight() {
		return jobs.countByStatusIn(IN_FLIGHT_STATUSES);
	}

	@Transactional(readOnly = true)
	@Override
	public int countFailed() {
		return jobs.countByStatusIn(List.of(STATUS_ERROR));
	}

	@Transactional(readOnly = true)
	@Override
	public List<ReportJobEntity> listRecentIssues(int limit) {
		Pageable page = PageRequest.of(0, Math.max(1, limit));
		// Two targeted queries rather than one scan of the table: a hard failure is found by status, a
		// degraded report by its warnings column, and neither can be expressed as the other.
		return newestFirst(
				jobs.findByStatusInOrderByCreatedAtDesc(List.of(STATUS_ERROR), page),
				jobs.findRecentWarned(page));
	}

	@Transactional(readOnly = true)
	@Override
	public List<ReportJobEntity> listAllIssues() {
		return newestFirst(
				jobs.findByStatusInOrderByCreatedAtDesc(List.of(STATUS_ERROR)),
				jobs.findRecentWarned(Pageable.unpaged()));
	}

	/**
	 * Merges the two kinds of issue into one list, newest first.
	 *
	 * @param failures hard failures
	 * @param warned   reports that shipped with warnings
	 * @return both kinds interleaved by creation time, newest first
	 */
	List<ReportJobEntity> newestFirst(List<ReportJobEntity> failures, List<ReportJobEntity> warned) {
		List<ReportJobEntity> issues = new ArrayList<>(failures);
		issues.addAll(warned);
		issues.sort(Comparator.comparing(
				ReportJobEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return issues;
	}

	@Transactional(readOnly = true)
	@Override
	public OffsetDateTime earliestJobCreatedAt() {
		return jobs.earliestCreatedAt();
	}

	@Transactional(readOnly = true)
	@Override
	public List<JobOwner> listOwners() {
		return jobs.listOwners();
	}

	@Transactional(readOnly = true)
	@Override
	public Page<ReportJobEntity> listDeliverables(Pageable pageable) {
		return jobs.findDeliverables(SHEET_TARGET, pageable);
	}

	@Transactional
	@Override
	public void markJobRunningAtStep(Long jobId, int step, String label) {
		ReportJobEntity job = loadRequiredJob(jobId);
		job.setStatus("running");
		job.setStep(step);
		job.setLabel(label);
		job.setUpdatedAt(OffsetDateTime.now());
		jobs.save(job);
	}

	@Transactional
	@Override
	public void markJobDone(Long jobId, String slideUrl, String warningsJson) {
		ReportJobEntity job = loadRequiredJob(jobId);
		job.setStatus("done");
		job.setStep(7);
		job.setLabel("Done!");
		job.setSlideUrl(slideUrl);
		job.setWarningsJson(warningsJson);
		job.setUpdatedAt(OffsetDateTime.now());
		jobs.save(job);
	}

	@Transactional
	@Override
	public void markJobFailed(Long jobId, String errorMessage) {
		jobs.findById(jobId).ifPresent(job -> {
			job.setStatus("error");
			job.setErrorMessage(errorMessage);
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}

	@Transactional(readOnly = true)
	@Override
	public ReportJobEntity loadRequiredJob(Long jobId) {
		return jobs.findById(jobId).orElseThrow(() ->
				new IllegalStateException("Report job not found: " + jobId));
	}

	@Transactional(readOnly = true)
	@Override
	public ReportJobEntity loadJobForOwner(String userId, Long jobId) {
		return jobs.findById(jobId)
				.filter(j -> userId != null && userId.equals(j.getOwnerUserId()))
				.orElseThrow(() -> new AppException(ErrorReason.C001, "Unknown job " + jobId));
	}

	@Transactional(readOnly = true)
	@Override
	public Optional<ReportJobEntity> findJob(Long jobId) {
		return jobs.findById(jobId);
	}

	@Transactional
	@Override
	public void deleteJob(Long jobId) {
		jobs.deleteById(jobId);
	}

	@Transactional
	@Override
	public void clearJobWarnings(Long jobId) {
		jobs.findById(jobId).ifPresent(job -> {
			job.setWarningsJson(null);
			job.setUpdatedAt(OffsetDateTime.now());
			jobs.save(job);
		});
	}
}
