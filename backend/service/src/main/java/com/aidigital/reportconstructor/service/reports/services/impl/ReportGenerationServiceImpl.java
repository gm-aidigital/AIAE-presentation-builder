package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the end-to-end marketing report build: persists a {@link ReportJobEntity},
 * resolves placeholders, runs the Claude copy batches, renders the Google Slides deck and
 * its charts, and tracks per-step progress for the UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationServiceImpl implements ReportGenerationService {

	private final ReportJobProgressHelper jobProgress;
	private final ReportGenerationWarningsHelper warnings;
	private final ReportGenerationChartHelper chartHelper;
	private final PlaceholderResolverService placeholders;
	private final ClaudeClient claude;
	private final SlidesProvider slides;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	private final ObjectProvider<ReportGenerationService> self;
	private final ReportClaudeDefaults claudeDefaults;

	/**
	 * Validates the brief, then enqueues the job and launches the build through the
	 * self-proxy so the {@code @Async} boundary on {@link #run} takes effect.
	 */
	@Override
	public ReportJobEntity start(String userId, String clerkUserId, GeneratePayload payload) {
		if (payload.brief() == null || payload.brief().isBlank()) {
			throw new AppException(ErrorReason.C002, "Brief is required");
		}
		ReportJobEntity job = enqueue(userId, payload);
		self.getObject().run(job.getId(), payload, clerkUserId);
		return job;
	}

	/**
	 * Persists the queued job in its own transaction via {@link ReportJobProgressHelper}.
	 */
	@Override
	@Transactional
	public ReportJobEntity enqueue(String userId, GeneratePayload payload) {
		return jobProgress.createQueuedJob(userId, payload.reportType());
	}

	/**
	 * Runs on a Spring {@code @Async} thread. Any failure is caught and recorded on the
	 * job as {@code error} rather than propagated to the caller.
	 */
	@Override
	@Async
	public void run(Long jobId, GeneratePayload payload, String clerkUserId) {
		try {
			jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
			CampaignData data = placeholders.collectData(payload);
			String brief = payload.brief();

			jobProgress.markJobRunningAtStep(jobId, 2, "Resolving placeholders");

			boolean live = claude.isLive();

			jobProgress.markJobRunningAtStep(jobId, 3, "Claude — campaign batch (A)");
			ClaudeStrategic ccA = (live && placeholders.needStrategic(payload))
					? claude.batchStrategic(data, brief) : claudeDefaults.emptyStrategic();

			jobProgress.markJobRunningAtStep(jobId, 4, "Claude — tactics batch (B)");
			ClaudeTactical ccB = (live && placeholders.needTactical(payload, data))
					? claude.batchTactical(data, brief) : claudeDefaults.emptyTactical();

			jobProgress.markJobRunningAtStep(jobId, 5, "Claude — executive batch (C)");
			ClaudeResults ccC = (live && placeholders.needResults(payload, data))
					? claude.batchResults(data, brief) : claudeDefaults.emptyResults();

			String geoSummary = (live && placeholders.needGeoSummary(payload))
					? claude.summarizeGeo(payload.geoRows()) : null;

			jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
			Map<String, String> flatReplacements =
					placeholders.buildFlatReplacements(payload, data, ccA, ccB, ccC, geoSummary);
			UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
			String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);
			String slideUrl = slides.createDeck(String.valueOf(jobId), flatReplacements, userGoogleToken);

			chartHelper.trimUnusedTactics(slideUrl, payload, userGoogleToken);

			jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
			List<String> chartWarnings = chartHelper.buildCharts(
					slideUrl, payload, data, flatReplacements, userGoogleToken);

			jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(chartWarnings));
		} catch (Exception ex) {
			log.error("[report] job {} failed", jobId, ex);
			jobProgress.markJobFailed(jobId, ex.getMessage());
		}
	}

	/**
	 * Read-only lookup; delegates ownership enforcement to the job-progress helper and
	 * normalises null string fields to empty strings before returning.
	 */
	@Override
	@Transactional(readOnly = true)
	public ProgressView progress(String userId, Long jobId) {
		ReportJobEntity job = jobProgress.loadJobForOwner(userId, jobId);
		return new ProgressView(
				job.getStep(),
				job.getTotal(),
				job.getLabel() == null ? "" : job.getLabel(),
				job.getStatus(),
				job.getSlideUrl() == null ? "" : job.getSlideUrl(),
				job.getErrorMessage() == null ? "" : job.getErrorMessage(),
				warnings.parseWarnings(job.getWarningsJson())
		);
	}
}
