package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
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

import java.util.LinkedHashMap;
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
	private final ReportSheetHelper sheetHelper;
	private final SheetPlaceholderReader placeholderReader;
	private final SheetCampaignReader sheetCampaign;
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
	public ReportJobEntity start(String userId, String clerkUserId, GeneratePayload payload, GenerationTarget target) {
		if (payload.brief() == null || payload.brief().isBlank()) {
			throw new AppException(ErrorReason.C002, "Brief is required");
		}
		if (target == GenerationTarget.SLIDES_FROM_SHEET
				&& (payload.sheetUrl() == null || payload.sheetUrl().isBlank())) {
			throw new AppException(ErrorReason.C002, "Sheet URL is required for the slides-from-sheet flow");
		}
		ReportJobEntity job = enqueue(userId, payload);
		self.getObject().run(job.getId(), payload, clerkUserId, target);
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
	public void run(Long jobId, GeneratePayload payload, String clerkUserId, GenerationTarget target) {
		try {
			if (target == GenerationTarget.SLIDES_FROM_SHEET) {
				// Step 2 of the sheet-as-source flow: the user-reviewed sheet is the only input, so
				// none of the raw-grid collection or the Batch A/B copy runs here — they are already
				// baked into the sheet by step 1. This branch reads the sheet back and fills the deck.
				runSlidesFromSheet(jobId, payload, clerkUserId);
				return;
			}

			jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
			CampaignData data = placeholders.collectData(payload);
			String brief = payload.brief();

			jobProgress.markJobRunningAtStep(jobId, 2, "Resolving placeholders");

			boolean live = claude.isLive();
			CampaignFrequencies frequencies = placeholders.computeFrequencies(payload, data);

			ClaudeStrategic ccA;
			ClaudeTactical ccB;
			ClaudeResults ccC;

			if (target == GenerationTarget.SHEET) {
				// The sheet template consumes only the Batch A audience fields and the Batch B per-tactic
				// gender/daypart fields — never any Batch C copy — so a single merged call covers both and
				// the (expensive) executive batch is skipped entirely for this target.
				jobProgress.markJobRunningAtStep(jobId, 3, "Claude — sheet batch");
				ClaudeSheetBatch sheetBatch =
						(live && (placeholders.needStrategic(payload) || placeholders.needTactical(payload, data)))
								? claude.batchSheet(data, brief) : claudeDefaults.emptySheetBatch();
				ccA = new ClaudeStrategic(sheetBatch.audienceAge(), sheetBatch.audienceSegments(), null, List.of());
				ccB = new ClaudeTactical(sheetBatch.byTactic());
				ccC = claudeDefaults.emptyResults();
			} else {
				jobProgress.markJobRunningAtStep(jobId, 3, "Claude — campaign batch (A)");
				ccA = (live && placeholders.needStrategic(payload))
						? claude.batchStrategic(data, brief) : claudeDefaults.emptyStrategic();

				jobProgress.markJobRunningAtStep(jobId, 4, "Claude — tactics batch (B)");
				ccB = (live && placeholders.needTactical(payload, data))
						? claude.batchTactical(data, brief) : claudeDefaults.emptyTactical();

				jobProgress.markJobRunningAtStep(jobId, 5, "Claude — executive batch (C)");
				ccC = (live && placeholders.needResults(payload, data))
						? claude.batchResults(data, brief, frequencies) : claudeDefaults.emptyResults();
			}

			String geoSummary = (live && placeholders.needGeoSummary(payload))
					? claude.summarizeGeo(payload.geoRows()) : null;

			String funnelSummary = (live && placeholders.needFunnelSummary(payload))
					? claude.summarizeFunnelStages(payload.geoRows()) : null;

			String primaryKpis = (live && placeholders.needPrimaryKpis(payload))
					? claude.summarizePrimaryKpis(data) : null;

			Map<String, String> flatReplacements =
					placeholders.buildFlatReplacements(payload, data, ccA, ccB, ccC, primaryKpis, geoSummary,
							funnelSummary, frequencies);
			UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
			String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);

			if (target == GenerationTarget.SHEET) {
				jobProgress.markJobRunningAtStep(jobId, 6, "Building sheet");
				String sheetUrl = sheetHelper.buildSheet(String.valueOf(jobId), flatReplacements, userGoogleToken);
				sheetHelper.trimUnusedTactics(sheetUrl, payload, userGoogleToken);

				jobProgress.markJobRunningAtStep(jobId, 7, "Building pacing tables");
				List<String> pacingWarnings = sheetHelper.writePacingTables(
						sheetUrl, payload, data, flatReplacements, userGoogleToken);

				jobProgress.markJobDone(jobId, sheetUrl, warnings.serializeWarnings(pacingWarnings));
				return;
			}

			jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
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
	 * Builds a slide deck from a previously generated, user-edited Google Sheet. Reads the sheet grid
	 * into the placeholder map, generates only the narrative copy the sheet never carried (Batch A
	 * strategic + Batch C executive), overlays the sheet's own values on top so every reviewed field
	 * wins, then fills the deck and renders the pacing/distribution charts from the sheet's numbers.
	 *
	 * <p>No source grid is re-collected and the sheet's own numbers, audience copy and per-tactic
	 * daypart (Batch B) are only read — never recomputed — so no Claude work is duplicated: Batch A/C
	 * are requested here for the first time because step 1 never produced them.
	 *
	 * @param jobId       id of the queued job to build and update
	 * @param payload     generation request carrying the source {@code sheetUrl} and report type
	 * @param clerkUserId Clerk identity used to fetch the Google access token for sheet/deck access
	 */
	void runSlidesFromSheet(Long jobId, GeneratePayload payload, String clerkUserId) {
		jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
		UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
		String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(payload.sheetUrl(), userGoogleToken);
		Map<String, String> sheetValues = placeholderReader.readPlaceholders(grid);
		int tacticCount = deriveTacticCount(sheetValues);

		jobProgress.markJobRunningAtStep(jobId, 3, "Claude — narrative");
		String brief = payload.brief();
		CampaignData data = sheetCampaign.read(sheetValues, tacticCount);
		CampaignFrequencies frequencies = placeholders.computeFrequencies(payload, data);
		boolean live = claude.isLive();
		ClaudeStrategic ccA = live ? claude.batchStrategic(data, brief) : claudeDefaults.emptyStrategic();
		ClaudeResults ccC = live ? claude.batchResults(data, brief, frequencies) : claudeDefaults.emptyResults();

		// Build the narrative placeholder map from the reconstructed context, then overlay the sheet's
		// own values so every field the user reviewed wins; only the sheet-less narrative keys (proposal
		// overview, strategic points, results overview, recommendations, frequency and tactic copy)
		// survive from the Claude output.
		Map<String, String> narrative = placeholders.buildFlatReplacements(
				payload, data, ccA, claudeDefaults.emptyTactical(), ccC, null, null, null, frequencies);
		Map<String, String> flatReplacements = new LinkedHashMap<>(narrative);
		flatReplacements.putAll(sheetValues);

		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String slideUrl = slides.createDeck(String.valueOf(jobId), flatReplacements, userGoogleToken);
		chartHelper.trimUnusedTactics(slideUrl, tacticCount, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildChartsFromSheet(
				slideUrl, grid, flatReplacements, tacticCount, userGoogleToken);

		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(chartWarnings));
	}

	/**
	 * Derives the active tactic count from a sheet-read placeholder map: the number of leading
	 * {@code {{tactic n}}} name tokens that carry a non-blank value, clamped to 1..7. Counting stops
	 * at the first missing or blank slot so trailing gaps never inflate the count.
	 *
	 * @param flatReplacements the placeholder map read back from the sheet
	 * @return the active tactic count (1..7)
	 */
	int deriveTacticCount(Map<String, String> flatReplacements) {
		int count = 0;
		for (int n = 1; n <= 7; n++) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name == null || name.isBlank()) {
				break;
			}
			count = n;
		}
		return Math.clamp(count, 1, 7);
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
