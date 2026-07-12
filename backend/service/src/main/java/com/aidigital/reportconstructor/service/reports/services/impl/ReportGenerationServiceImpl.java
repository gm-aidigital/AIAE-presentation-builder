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
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
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

	private static final String CLIENT_NAME_TOKEN = "{{client_name}}";
	private static final String CHANGE_LOG_TOKEN = "{{change log}}";

	/** Max tactics the report template carries; the derived tactic count is clamped to this. */
	private static final int MAX_TACTICS = 28;

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
	private final ReportFileNamer fileNamer;
	private final ReportNumberParser reportNumbers;
	private final Fmt fmt;

	/**
	 * Validates the brief, then enqueues the job and launches the build through the
	 * self-proxy so the {@code @Async} boundary on {@link #run} takes effect.
	 */
	@Override
	public ReportJobEntity start(
			String userId, String clerkUserId, String userEmail, GeneratePayload payload, GenerationTarget target,
			String mediaPlanUrl, String elevateUrl) {
		if (payload.brief() == null || payload.brief().isBlank()) {
			throw new AppException(ErrorReason.C002, "Brief is required");
		}
		if (target == GenerationTarget.SLIDES_FROM_SHEET
				&& (payload.sheetUrl() == null || payload.sheetUrl().isBlank())) {
			throw new AppException(ErrorReason.C002, "Sheet URL is required for the slides-from-sheet flow");
		}
		ReportJobEntity job = enqueue(userId, payload);
		jobProgress.recordJobContext(job.getId(), userEmail, target.name(), mediaPlanUrl, elevateUrl);
		self.getObject().run(job.getId(), payload, clerkUserId, userEmail, target);
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
	public void run(Long jobId, GeneratePayload payload, String clerkUserId, String userEmail, GenerationTarget target) {
		try {
			if (target == GenerationTarget.SLIDES_FROM_SHEET) {
				// Step 2 of the sheet-as-source flow: the user-reviewed sheet is the only input, so
				// none of the raw-grid collection or the Batch A/B copy runs here — they are already
				// baked into the sheet by step 1. This branch reads the sheet back and fills the deck.
				runSlidesFromSheet(jobId, payload, clerkUserId, userEmail);
				return;
			}

			jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
			CampaignData data = placeholders.collectData(payload);
			String brief = combineBriefWithChangeLog(payload.brief(), payload.changeLog());

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
			String fileName = fileNamer.buildFileName(
					payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);

			if (target == GenerationTarget.SHEET) {
				jobProgress.markJobRunningAtStep(jobId, 6, "Building sheet");
				String sheetUrl = sheetHelper.buildSheet(
						String.valueOf(jobId), fileName, flatReplacements, userGoogleToken);
				sheetHelper.trimUnusedTactics(sheetUrl, payload, userGoogleToken);

				jobProgress.markJobRunningAtStep(jobId, 7, "Building pacing tables");
				List<String> pacingWarnings = sheetHelper.writePacingTables(
						sheetUrl, payload, data, flatReplacements, userGoogleToken);

				jobProgress.recordArtifact(jobId, fileName, sheetUrl);
				jobProgress.markJobDone(jobId, sheetUrl, warnings.serializeWarnings(pacingWarnings));
				return;
			}

			jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
			String slideUrl = slides.createDeck(String.valueOf(jobId), fileName, flatReplacements, userGoogleToken);

			chartHelper.trimUnusedTactics(slideUrl, payload, userGoogleToken);

			jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
			List<String> chartWarnings = chartHelper.buildCharts(
					slideUrl, payload, data, flatReplacements, userGoogleToken);

			jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
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
	 * @param userEmail   email of the triggering user, used to name the generated deck
	 */
	void runSlidesFromSheet(Long jobId, GeneratePayload payload, String clerkUserId, String userEmail) {
		jobProgress.markJobRunningAtStep(jobId, 1, "Reading sheet data");
		UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
		String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(payload.sheetUrl(), userGoogleToken);
		Map<String, String> sheetValues = placeholderReader.readPlaceholders(grid);
		int tacticCount = deriveTacticCount(sheetValues);

		jobProgress.markJobRunningAtStep(jobId, 3, "Claude — narrative");
		// The change log is read back from the reviewed sheet (never the payload) so any edit the user made
		// in the sheet wins, consistent with the sheet-as-source contract of this flow.
		String brief = combineBriefWithChangeLog(payload.brief(), sheetValues.get(CHANGE_LOG_TOKEN));
		CampaignData data = sheetCampaign.read(sheetValues, tacticCount);
		// Frequencies are reconstructed from the reviewed sheet — never the raw media plan — and without a
		// fresh random reach uplift, so the Claude frequency narrative and the deck's frequency figures both
		// match exactly what the user sees in the sheet.
		CampaignFrequencies frequencies = sheetCampaign.readFrequencies(sheetValues);
		if (log.isInfoEnabled()) {
			log.info("[report] job {} slides-from-sheet context tacticCount={} tactics={}",
					jobId, tacticCount, describeTactics(data));
		}
		boolean live = claude.isLive();
		// Strategic narrative only (proposal + insights). Audience already lives in the sheet from step 1, so
		// this flow never regenerates it — no duplicate Claude work across the two steps.
		ClaudeStrategic ccA = live ? claude.batchStrategicNarrative(data, brief) : claudeDefaults.emptyStrategic();
		ClaudeResults ccC = live ? claude.batchResults(data, brief, frequencies) : claudeDefaults.emptyResults();

		// Build the narrative map from a payload stripped of every raw grid: the Claude-authored copy still
		// flows through, but each numeric placeholder is forced to come solely from the sheet overlay below.
		// A missing sheet anchor then renders as a blank (visible) rather than a stale raw value (silent).
		GeneratePayload narrativePayload = narrativeOnly(payload);
		Map<String, String> narrative = placeholders.buildFlatReplacements(
				narrativePayload, data, ccA, claudeDefaults.emptyTactical(), ccC, null, null, null, frequencies);
		Map<String, String> flatReplacements = new LinkedHashMap<>(narrative);
		flatReplacements.putAll(sheetValues);
		aliasSheetTokens(flatReplacements, tacticCount);

		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String fileName = fileNamer.buildFileName(
				payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);
		String slideUrl = slides.createDeck(String.valueOf(jobId), fileName, flatReplacements, userGoogleToken);
		chartHelper.trimUnusedTactics(slideUrl, tacticCount, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildChartsFromSheet(
				slideUrl, grid, flatReplacements, tacticCount, userGoogleToken);

		jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(chartWarnings));
	}

	/**
	 * Derives the active tactic count from a sheet-read placeholder map: the number of leading
	 * {@code {{tactic n}}} name tokens that carry a non-blank value, clamped to 1..28. Counting stops
	 * at the first missing or blank slot so trailing gaps never inflate the count.
	 *
	 * @param flatReplacements the placeholder map read back from the sheet
	 * @return the active tactic count (1..28)
	 */
	int deriveTacticCount(Map<String, String> flatReplacements) {
		int count = 0;
		for (int n = 1; n <= MAX_TACTICS; n++) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name == null || name.isBlank()) {
				break;
			}
			count = n;
		}
		return Math.clamp(count, 1, MAX_TACTICS);
	}

	/**
	 * Returns a copy of the payload with every raw source grid stripped (brief, report type, date filter
	 * and sheet URL are kept). The slides-from-sheet flow feeds this to the placeholder builder so the
	 * Claude-authored narrative still resolves while no per-tactic or total number can be recomputed from
	 * the raw media plan — the sheet overlay is the sole source of numeric placeholders.
	 *
	 * @param payload the inbound generation payload
	 * @return a narrative-only payload carrying no raw media-plan/adjustment/estimate grids
	 */
	GeneratePayload narrativeOnly(GeneratePayload payload) {
		return new GeneratePayload(
				payload.brief(), payload.reportType(), null,
				List.of(), List.of(), List.of(), List.of(), List.of(),
				null, null, payload.dateFilter(), payload.sheetUrl(), payload.changeLog());
	}

	/**
	 * Merges the required campaign brief with the optional change log into a single context string for the
	 * Claude batches, appending the change log under its own {@code === MID-FLIGHT CHANGES / CHANGE LOG ===}
	 * header so Claude reads it as a distinct, clearly-labelled section. Returns the brief unchanged when the
	 * change log is null or blank.
	 *
	 * @param brief     the required free-text campaign brief
	 * @param changeLog the optional change-log text (from the payload for the direct flow, or read back from the
	 *                  reviewed sheet for the slides-from-sheet flow); {@code null}/blank leaves the brief untouched
	 * @return the brief, optionally followed by a labelled change-log section
	 */
	String combineBriefWithChangeLog(String brief, String changeLog) {
		String safeBrief = brief == null ? "" : brief;
		if (changeLog == null || changeLog.isBlank()) {
			return safeBrief;
		}
		return safeBrief + "\n\n=== MID-FLIGHT CHANGES / CHANGE LOG ===\n" + changeLog.trim();
	}

	/**
	 * Copies sheet-read values onto the deck's alternately-spelled placeholder tokens so a token the sheet
	 * reader never emits under that exact spelling still renders the reviewed value instead of a blank:
	 * {@code {{total spend}}} mirrors {@code {{total_investment}}} and the correctly-spelled
	 * {@code {{tactic n completions}}} mirrors the sheet's {@code {{tactic n complitions}}}.
	 *
	 * <p>The sheet reader loads {@code {{reach_f}}} with the summary Frequency total purely so
	 * {@link SheetCampaignReader#readFrequencies} can read the reviewed frequency; here — after the
	 * frequency has been read — that token is reclaimed for its deck meaning ("reach fact", the
	 * "Market Captured" figure). The sheet carries a single Reach column, so the actual reach is that
	 * same reach, and the presentation-short reach tokens ({@code {{reach_p}}}, {@code {{reach_f_pres}}})
	 * render it abbreviated (e.g. {@code "70k"}) — matching the compaction the BigQuery flow applies —
	 * rather than as the raw grouped figure.
	 *
	 * @param flat        the assembled placeholder map (sheet values already overlaid)
	 * @param tacticCount the active tactic count driving the per-tactic aliases
	 */
	void aliasSheetTokens(Map<String, String> flat, int tacticCount) {
		copyToken(flat, "{{total_investment}}", "{{total spend}}");
		copyToken(flat, "{{reach}}", "{{reach_f}}");
		compactToken(flat, "{{reach}}", "{{reach_p}}");
		compactToken(flat, "{{reach}}", "{{reach_f_pres}}");
		for (int n = 1; n <= tacticCount; n++) {
			copyToken(flat, "{{tactic " + n + " complitions}}", "{{tactic " + n + " completions}}");
		}
	}

	/**
	 * Writes the compact abbreviation (e.g. {@code "70k"}, {@code "1.2M"}) of a source token's numeric
	 * value onto a destination token, leaving the destination untouched when the source is absent or blank.
	 *
	 * @param flat the placeholder map to update
	 * @param from the source token key whose value is parsed and abbreviated
	 * @param to   the destination token key receiving the compact string
	 */
	void compactToken(Map<String, String> flat, String from, String to) {
		String value = flat.get(from);
		if (value == null || value.isBlank()) {
			return;
		}
		flat.put(to, fmt.compact(reportNumbers.parseReportNumber(value)));
	}

	/**
	 * Copies a non-blank source token value onto a destination token, leaving the destination untouched
	 * when the source is absent or blank.
	 *
	 * @param flat the placeholder map to update
	 * @param from the source token key
	 * @param to   the destination token key
	 */
	void copyToken(Map<String, String> flat, String from, String to) {
		String value = flat.get(from);
		if (value != null && !value.isBlank()) {
			flat.put(to, value);
		}
	}

	/**
	 * Renders the sheet-reconstructed per-tactic name/spend/impressions as a compact one-line string for the
	 * diagnostic log, so a run can be checked against the edited sheet to confirm the reviewed numbers were
	 * read back (and reach Claude) rather than silently lost.
	 *
	 * @param data the campaign data reconstructed from the sheet
	 * @return a {@code "n=name spend=… imps=…"} summary, joined by {@code "; "}
	 */
	String describeTactics(CampaignData data) {
		if (data == null || data.tactics() == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (var e : data.tactics().entrySet()) {
			var t = e.getValue();
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(e.getKey()).append('=').append(t.name())
					.append(" spend=").append(t.spend())
					.append(" imps=").append(t.imps());
		}
		return sb.toString();
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
