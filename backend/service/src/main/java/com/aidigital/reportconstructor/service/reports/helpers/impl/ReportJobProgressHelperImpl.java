package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring bean implementation of {@link ReportJobProgressHelper}.
 */
@Component
@RequiredArgsConstructor
public class ReportJobProgressHelperImpl implements ReportJobProgressHelper {

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
}
