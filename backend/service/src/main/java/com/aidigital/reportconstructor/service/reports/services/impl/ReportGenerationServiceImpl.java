package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetTacticCountHelper;
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.CreativeBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.DeviceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.GeoBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ImpressionContributionHelper;
import com.aidigital.reportconstructor.service.reports.helpers.PublisherBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownThoughtsGate;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticConclusionAssembler;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClientFlavors;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

	/** Sheet field carrying the campaign context: step 1 writes Claude's brief digest here. */
	private static final String RFP_INFO_TOKEN = "{{RFP info}}";

	/** Deck/sheet field carrying the campaign's marketing funnel stages. */
	private static final String FUNNEL_STAGES_TOKEN = "{{funnel_stages}}";

	/** Value an unresolved placeholder flattens to; treated as "no value" when filling the funnel stages. */
	private static final String DASH = "—";

	/** Report type whose deck ships without the frequency slide (and therefore without its narrative). */
	private static final String EOM_REPORT_TYPE = "EOM";

	/** Max tactics the report template carries; the derived tactic count is clamped to this. */
	private static final int MAX_TACTICS = 28;

	/** The per-tactic "thoughts on tactic performance" slide holds exactly four thought tokens. */
	private static final int THOUGHTS_ON_TACTIC_COUNT = 4;

	/** Max breakdown-conclusion lines fed to Batch D as read-only alignment context, bounding its input size. */
	private static final int BREAKDOWN_DIGEST_MAX_LINES = 80;
	/** Per-line character cap for the Batch D breakdown digest; long slide copy is truncated for context only. */
	private static final int BREAKDOWN_DIGEST_LINE_MAX = 160;

	private final ReportJobProgressHelper jobProgress;
	private final ReportGenerationWarningsHelper warnings;
	private final ReportGenerationChartHelper chartHelper;
	private final ReportSheetHelper sheetHelper;
	private final PublisherBreakdownHelper publisherBreakdown;
	private final CreativeBreakdownHelper creativeBreakdown;
	private final GeoBreakdownHelper geoBreakdown;
	private final AudienceBreakdownHelper audienceBreakdown;
	private final DeviceBreakdownHelper deviceBreakdown;
	private final SheetPlaceholderReader placeholderReader;
	private final SheetCampaignReader sheetCampaign;
	private final PlaceholderResolverService placeholders;
	/**
	 * Picks the Claude client whose prompts are written for this run's report type. Resolved once per run
	 * into a local {@code claude}, because an end-of-month deck has to be written as a mid-flight status
	 * while an end-of-campaign deck is a closing verdict, and the two need different prompt wording.
	 */
	private final ClaudeClientFlavors claudeClients;
	private final SlidesProvider slides;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	private final ObjectProvider<ReportGenerationService> self;
	private final ReportClaudeDefaults claudeDefaults;
	private final ReportFileNamer fileNamer;
	private final ReportNumberParser reportNumbers;
	private final Fmt fmt;
	/**
	 * Shared virtual-thread executor (the {@code applicationTaskExecutor} bean) used to run the five
	 * independent breakdown sections concurrently in {@link #runSlidesFromSheet}. Field name matches the
	 * bean name so injection resolves it by name.
	 */
	private final AsyncTaskExecutor applicationTaskExecutor;
	/** Per-run Claude token accounting, opened in {@link #run} and stamped onto the job when it ends. */
	private final ClaudeUsageTracker usageTracker;

	private final ClaudeFailureLog failureLog;
	/** Reduces the request's per-tactic breakdown selections into the typed enabled-sections map. */
	private final BreakdownSelectionResolver breakdownResolver;
	/** The shared "&gt; 2 breakdowns" gate deciding which tactics get Step-3 thoughts and the thoughts slide. */
	private final BreakdownThoughtsGate thoughtsGate;
	/** Assembles the Step-3 thoughts inputs and Step-4 campaign digests from the Step-2 conclusions. */
	private final TacticConclusionAssembler conclusionAssembler;
	/** Reads the per-tactic daily/monthly pacing series back out of the reviewed sheet grid. */
	private final SheetChartDataReader sheetChartData;
	/** Derives each tactic's KPI series (clicks vs completions) from its name, as the chart step does. */
	private final TacticExtractionHelper tacticExtraction;
	private final ImpressionContributionHelper contributions;
	/** Distils a finished sheet build into the state the report is resumed from in a later session. */
	private final ReportResumeStateHelper resumeState;
	/** Counts the tactics a reviewed workbook reports, shared with the adopt-a-sheet flow. */
	private final SheetTacticCountHelper tacticCounter;

	/**
	 * Validates the brief, then enqueues the job and launches the build through the
	 * self-proxy so the {@code @Async} boundary on {@link #run} takes effect.
	 */
	@Override
	public ReportJobEntity start(
			String userId, String clerkUserId, String userEmail, GeneratePayload payload, GenerationTarget target,
			String mediaPlanUrl, String elevateUrl) {
		// Only the sheet-building flow has nowhere else to get the campaign context from. A
		// slides-from-sheet run reads {{RFP info}} straight off the reviewed workbook and lets it win over
		// this payload (see runSlidesFromSheet), so requiring a brief here would reject a workbook that
		// carries a perfectly good one in its own cell — which is exactly what a resumed draft looks like
		// once the user fills that cell after the sheet was built.
		if (target != GenerationTarget.SLIDES_FROM_SHEET
				&& (payload.brief() == null || payload.brief().isBlank())) {
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
		// Opened before any Claude work and read back in the finally below, so a run that fails half-way
		// still reports the tokens it burned — those are billed either way, and a failed expensive run is
		// exactly what the admin token dashboard exists to make visible.
		ClaudeUsageScope usageScope = usageTracker.begin(jobId, null, userEmail);
		// Opened alongside it for the same reason, on the other axis: a reply Claude sent but the pipeline
		// rejected only shows up in the server log, which the person who ran the report cannot read. Collecting
		// the reasons here puts them on the result card next to the blank slide they produced.
		failureLog.begin();
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

			jobProgress.markJobRunningAtStep(jobId, 2, "Resolving placeholders");

			// EOC and EOM send different prompt text for the same calls; the report type picks which.
			ClaudeClient claude = claudeClients.forReportType(payload.reportType());
			boolean live = claude.isLive();
			// The brief is user-pasted and unbounded, and every batch below repeats it as context. Digest it
			// once here and feed the digest everywhere instead: the campaign facts the copy must stay faithful
			// to survive, the token cost of the raw text is paid a single time, and the digest is written into
			// the sheet's {{RFP info}} so the slides step reads it back rather than digesting again.
			String briefDigest = live ? claude.digestBrief(payload.brief()) : null;
			// The change log gets its own digest call rather than riding along inside the brief's: asked to
			// condense a brief, the model treats an appended change-log section as commentary and drops it, so
			// the log's content never reached the sheet or the later batches. Digested separately it lands in
			// {{change log}} in the sheet and in the context below, exactly like the brief.
			String changeLogDigest = live ? claude.digestChangeLog(payload.changeLog()) : null;
			String changeLogText = changeLogDigest == null || changeLogDigest.isBlank()
					? payload.changeLog() : changeLogDigest;
			String briefText = briefDigest == null || briefDigest.isBlank() ? payload.brief() : briefDigest;
			String brief = combineBriefWithChangeLog(briefText, changeLogText);
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

				// The campaign-level result copy is produced by the sheet flow's own campaign batch
				// (see runSlidesFromSheet), so this direct-to-deck path ships the empty shape and the
				// resolvers fall back to whatever the workbook already carries.
				ccC = claudeDefaults.emptyResults();
			}

			String geoSummary = (live && placeholders.needGeoSummary(payload))
					? claude.summarizeGeo(payload.geoRows()) : null;

			// Funnel stages are no longer inferred from a scan of the whole workbook — that call shipped every
			// tab of the plan to answer with one short line. They are derived from the per-tactic goals once
			// those are resolved, below, where the goal values already exist in the placeholder map.
			String primaryKpis = (live && placeholders.needPrimaryKpis(payload))
					? claude.summarizePrimaryKpis(data) : null;

			// Both the Sheet and the Slides deck fill only the tactics the campaign actually has, so the
			// placeholder map — and therefore the find-replace batch — is bounded to the real tactic count.
			// A two-tactic campaign no longer fans out ~800 replace requests (28 slots × ~27 tokens); that
			// volume was the cause of the "Read timed out" on both createSheet and createDeck. Unused template
			// slots are cleaned up afterwards: the deck deletes its surplus slides, and the sheet blanks any
			// leftover {{token}} in a single regex pass (see RealSheetDeckProvider#createSheet) plus trims the
			// now-empty rows.
			int flatTacticCount = maxTacticNumber(data);
			Map<String, String> flatReplacements =
					placeholders.buildFlatReplacements(payload, data, ccA, ccB, ccC, primaryKpis, geoSummary,
							null, briefDigest, changeLogDigest, frequencies, flatTacticCount);
			fillFunnelStages(claude, flatReplacements, flatTacticCount, live);
			UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
			String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);
			String fileName = fileNamer.buildFileName(
					payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);

			if (target == GenerationTarget.SHEET) {
				jobProgress.markJobRunningAtStep(jobId, 6, "Building sheet");
				String sheetUrl = sheetHelper.buildSheet(
						String.valueOf(jobId), fileName, flatReplacements, payload.reportType(), userGoogleToken);
				sheetHelper.trimUnusedTactics(sheetUrl, payload, userGoogleToken);
				sheetHelper.clearUnselectedBreakdowns(sheetUrl, payload, userGoogleToken);

				jobProgress.markJobRunningAtStep(jobId, 7, "Building pacing tables");
				List<String> pacingWarnings = sheetHelper.writePacingTables(
						sheetUrl, payload, data, flatReplacements, userGoogleToken);

				jobProgress.recordArtifact(jobId, fileName, sheetUrl);
				// The workbook now goes to the user, who fills it by hand — often over days, and rarely in
				// the browser tab that is still open. Everything the slides step will need is stored on the
				// job here so that tab is expendable: the report resumes from "My reports" at the review step.
				jobProgress.recordResumeState(jobId, resumeState.serialize(resumeState.toState(payload, data)));
				jobProgress.markJobDone(jobId, sheetUrl, warnings.serializeWarnings(pacingWarnings));
				return;
			}

			// The tactic slide's contribution legend. Deck-only, so it is derived here rather than before the
			// SHEET branch above — the workbook has no such column, and every token added to the sheet's
			// find-replace map costs a request on a write that already timed out once when it grew too large.
			contributions.fillContributions(flatReplacements, flatTacticCount);

			jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
			String slideUrl = slides.createDeck(
					String.valueOf(jobId), fileName, flatReplacements, payload.reportType(), userGoogleToken);

			// Main tactic slides from the single master, same as the sheet flow above; a no-op on the legacy
			// 28-slot template, where the trim below removes the surplus slots instead.
			List<String> tacticSlideWarnings =
					chartHelper.addTacticSlides(slideUrl, flatTacticCount, flatReplacements, userGoogleToken);
			chartHelper.trimUnusedTactics(slideUrl, payload, userGoogleToken);
			// Template master slides must never ship. This flow inserts no breakdowns, but it does duplicate the
			// tactic master above, and the master itself would otherwise arrive full of raw {{tactic n …}} tokens.
			chartHelper.deleteMasterSlides(slideUrl, userGoogleToken);
			// EOC-only story slides the EOM deck inherited from the template it was copied from.
			chartHelper.deleteReportTypeSlides(slideUrl, payload.reportType(), userGoogleToken);

			jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
			List<String> chartWarnings = chartHelper.buildCharts(
					slideUrl, payload, data, flatReplacements, userGoogleToken);

			List<String> deckWarnings = new ArrayList<>(tacticSlideWarnings);
			deckWarnings.addAll(chartWarnings);

			jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
			recordSlideCount(jobId, slideUrl, userGoogleToken);
			jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(deckWarnings));
		} catch (Exception ex) {
			log.error("[report] job {} failed", jobId, ex);
			jobProgress.markJobFailed(jobId, ex.getMessage());
		} finally {
			recordUsage(jobId, usageScope);
			usageTracker.clear();
			failureLog.clear();
		}
	}

	/**
	 * Measures the finished deck and stamps its slide count onto the job, for the admin dashboard's
	 * saved-hours figure. Runs after the surplus template slides have been deleted, so the number is
	 * what the client receives. Like token accounting, this is bookkeeping: a deck that shipped must
	 * never be reported as failed because its slides could not be counted.
	 *
	 * @param jobId           id of the job that just finished
	 * @param slideUrl        the finished deck
	 * @param userGoogleToken signed-in user's Google OAuth token, or {@code null} for the service account
	 */
	void recordSlideCount(Long jobId, String slideUrl, String userGoogleToken) {
		try {
			jobProgress.recordSlideCount(jobId, slides.countSlides(slideUrl, userGoogleToken));
		} catch (Exception ex) {
			log.warn("[report] job {} slide count could not be recorded: {}", jobId, ex.getMessage());
		}
	}

	/**
	 * Stamps the run's token consumption onto the job. Accounting must never turn a finished report
	 * into a failed one, so a persistence problem here is logged and swallowed.
	 *
	 * @param jobId      id of the job that just finished
	 * @param usageScope the run's token counters
	 */
	void recordUsage(Long jobId, ClaudeUsageScope usageScope) {
		try {
			ClaudeUsage usage = usageScope.snapshot();
			jobProgress.recordTokenUsage(jobId, usage);
			log.info("[report] job {} claude usage calls={} in={} out={} cacheWrite={} cacheRead={}",
					jobId, usage.calls(), usage.inputTokens(), usage.outputTokens(),
					usage.cacheWriteTokens(), usage.cacheReadTokens());
		} catch (Exception ex) {
			log.warn("[report] job {} token usage could not be recorded: {}", jobId, ex.getMessage());
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
		// Both halves of the campaign context — the brief digest in {{RFP info}} and the change-log digest in
		// {{change log}} — are read back from the reviewed sheet, so any edit the user made there wins and the
		// two steps run on exactly the same text. The payload's raw values are the fallback when the sheet
		// carries neither (older sheet, or Claude stubbed when step 1 ran).
		String sheetBrief = sheetValues.get(RFP_INFO_TOKEN);
		String briefContext = sheetBrief == null || sheetBrief.isBlank() ? payload.brief() : sheetBrief;
		String sheetChangeLog = sheetValues.get(CHANGE_LOG_TOKEN);
		String changeLogContext = sheetChangeLog == null || sheetChangeLog.isBlank()
				? payload.changeLog() : sheetChangeLog;
		String briefSource = combineBriefWithChangeLog(briefContext, changeLogContext);
		CampaignData data = sheetCampaign.read(sheetValues, tacticCount);
		// Frequencies are reconstructed from the reviewed sheet — never the raw media plan — and without a
		// fresh random reach uplift, so the Claude frequency narrative and the deck's frequency figures both
		// match exactly what the user sees in the sheet.
		CampaignFrequencies frequencies = sheetCampaign.readFrequencies(sheetValues);
		if (log.isInfoEnabled()) {
			log.info("[report] job {} slides-from-sheet context tacticCount={} tactics={}",
					jobId, tacticCount, describeTactics(data));
		}
		// EOC and EOM send different prompt text for the same calls; the report type picks which.
		ClaudeClient claude = claudeClients.forReportType(payload.reportType());
		boolean live = claude.isLive();
		// Every call below repeats this text as context, so it is bounded to the digest budget before any of them
		// sees it. The sheet's {{RFP info}} normally already holds step 1's digest, but it is a user-editable cell,
		// the change log above is appended raw, and an older sheet falls back to the payload's raw brief — this is
		// where those three paths are prevented from pushing the full brief into every prompt of the run.
		String brief = live ? claude.digestBriefIfOversized(briefSource) : briefSource;
		// Batch A: strategic narrative only (proposal + insights). Audience already lives in the sheet from the
		// sheet-build step, so this flow never regenerates it — no duplicate Claude work across the two steps.
		// The pacing blocks the user reviewed, read straight off the same grid the charts are built from.
		// An EOM deck's strategic insights are asked for as month-over-month movements and its per-tactic
		// overviews argue the month's pacing off the daily curve — neither of which the month's own totals
		// can express; the EOC wording ignores both series and is unaffected.
		SheetChartData pacing = live
				? readPacing(grid, data, tacticCount) : new SheetChartData(Map.of(), Map.of());
		ClaudeStrategic ccA = live
				? claude.batchStrategicNarrative(data, brief, pacing.monthlyPivots())
				: claudeDefaults.emptyStrategic();

		// Seed map for the breakdown data reads (tactic names, KPI types, gender split). The results copy is
		// intentionally empty here: the per-tactic overviews come from Step 2 and the campaign results from
		// Step 4; only the deck-filling map rebuilt at the end carries them.
		Map<String, String> prelim = buildSheetFlatReplacements(
				payload, data, ccA, claudeDefaults.emptyResults(), frequencies, tacticCount, sheetValues);

		// Step 2 (data) — read every breakdown section's tables and per-tactic Claude inputs. These are pure
		// Google-Sheet reads with no Claude call, so they run concurrently on the shared virtual-thread
		// executor; each helper catches its own failures, so one section failing cannot abort the others.
		ClaudeUsageScope usageScope = usageTracker.current();
		CompletableFuture<BreakdownSectionInputs<PublisherObservationInput>> pubF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> publisherBreakdown.readPublisherInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<CreativeTakeawayInput>> creF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> creativeBreakdown.readCreativeInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<GeoInsightInput>> geoF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> geoBreakdown.readGeoInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<AudienceInsightInput>> audF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> audienceBreakdown.readAudienceInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		CompletableFuture<BreakdownSectionInputs<DeviceInsightInput>> devF = CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, () -> deviceBreakdown.readDeviceInputs(
						payload.sheetUrl(), payload.breakdownSelections(), prelim, userGoogleToken)),
				applicationTaskExecutor);
		BreakdownSectionInputs<PublisherObservationInput> pub = pubF.join();
		BreakdownSectionInputs<CreativeTakeawayInput> cre = creF.join();
		BreakdownSectionInputs<GeoInsightInput> geo = geoF.join();
		BreakdownSectionInputs<AudienceInsightInput> aud = audF.join();
		BreakdownSectionInputs<DeviceInsightInput> dev = devF.join();

		// Step 2 (Claude) — the per-tactic conclusions call, which writes each tactic's overview and nothing
		// else. Every tactic on the reviewed sheet is included so its overview (and the whole downstream results
		// narrative) is always written from the EOC sheet figures + brief, whether or not it ran a breakdown.
		List<Integer> conclusionTactics = conclusionTacticNums(data, pub, cre, geo, aud, dev);
		List<TacticConclusion> conclusions = live && !conclusionTactics.isEmpty()
				? claude.batchTacticConclusions(data, conclusionTactics, brief, pacing.dailyPivots()) : List.of();
		// Breakdown slide copy: every section is produced by its own dedicated per-tactic call. All of them are
		// dispatched up front (across all sections and tactics) so they run maximally in parallel under the
		// shared Claude concurrency limit, then joined.
		BreakdownBullets bullets = new BreakdownBullets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		if (live) {
			var pubC = dispatchSection(pub.inputs(), usageScope, in -> claude.publisherSection(data, in, brief));
			var creC = dispatchSection(cre.inputs(), usageScope, in -> claude.creativeSection(data, in, brief));
			var geoC = dispatchSection(geo.inputs(), usageScope, in -> claude.geoSection(data, in, brief));
			var audC = dispatchSection(aud.inputs(), usageScope, in -> claude.audienceSection(data, in, brief));
			var devC = dispatchSection(dev.inputs(), usageScope, in -> claude.deviceSection(data, in, brief));
			bullets = new BreakdownBullets(joinSection(pubC), joinSection(creC), joinSection(geoC),
					joinSection(audC), joinSection(devC));
		}

		// Breakdown token map: the section data tokens first, then the Claude section tokens written from the
		// per-section calls. Each section only emits tokens for its own tactics, so the merge cannot collide.
		// The warnings collect every tactic whose section shipped without its Claude copy.
		Map<String, String> breakdownValues = new LinkedHashMap<>();
		breakdownValues.putAll(pub.dataValues());
		breakdownValues.putAll(cre.dataValues());
		breakdownValues.putAll(geo.dataValues());
		breakdownValues.putAll(aud.dataValues());
		breakdownValues.putAll(dev.dataValues());
		List<String> jobWarnings = new ArrayList<>();
		jobWarnings.addAll(publisherBreakdown.writePublisherObservations(breakdownValues, pub.tactics(),
				pub.inputs().keySet(), bullets.publisher(), prelim));
		jobWarnings.addAll(creativeBreakdown.writeCreativeTakeaways(breakdownValues, cre.tactics(),
				cre.inputs().keySet(), bullets.creative(), prelim));
		jobWarnings.addAll(geoBreakdown.writeGeoInsights(breakdownValues, geo.tactics(),
				geo.inputs().keySet(), bullets.geo(), prelim));
		jobWarnings.addAll(audienceBreakdown.writeAudienceInsights(breakdownValues, aud.tactics(),
				aud.inputs().keySet(), bullets.audience(), prelim));
		jobWarnings.addAll(deviceBreakdown.writeDeviceInsights(breakdownValues, dev.tactics(),
				dev.inputs().keySet(), bullets.device(), prelim));

		// Step 3 — per-tactic "thoughts on tactic performance" for the tactics with more than two breakdowns
		// (the same gate the thoughts slide uses). One call per tactic, all dispatched at once so they run in
		// parallel like Step 2's section calls. Written into the breakdown token map so the slide picks them up
		// when it is duplicated.
		Map<Integer, Set<BreakdownType>> enabledByTactic = qualifyingSelections(payload, tacticCount);
		Set<Integer> qualifying = thoughtsGate.qualifyingTactics(enabledByTactic);
		Map<Integer, String> namesByTactic = tacticNames(prelim, tacticCount);
		List<TacticThoughts> thoughts = List.of();
		if (live && !qualifying.isEmpty()) {
			List<TacticThoughtsInput> thoughtsInputs = conclusionAssembler.toThoughtsInputs(
					conclusions, namesByTactic, qualifying, bullets);
			thoughts = joinThoughts(dispatchThoughts(claude, thoughtsInputs, usageScope, brief));
		}
		writeThoughtsTokens(breakdownValues, qualifying, thoughts, prelim);

		// Step 4 — campaign-level results (results overviews, performance thoughts, recommendations, frequency)
		// from the per-tactic digests; the Step-2 overviews are merged back in for the tactic-overview slides.
		List<TacticNarrativeDigest> digests =
				conclusionAssembler.toCampaignDigests(conclusions, namesByTactic, thoughts, bullets);
		// EOM decks drop the frequency slide entirely, so the frequency narrative ({{f_oppartunity}} /
		// {{f_fact}} / {{f_storytelling}}) has nowhere to land: passing no frequencies keeps those three
		// fields out of the prompt and out of the reply, instead of paying for copy that is deleted.
		CampaignFrequencies resultsFrequencies =
				EOM_REPORT_TYPE.equals(payload.reportType()) ? null : frequencies;
		ClaudeResults campaign = live
				? claude.batchCampaignResults(data, brief, resultsFrequencies, digests)
				: claudeDefaults.emptyResults();
		// A silent empty campaign result despite real per-tactic digests means Batch C degraded to the empty DTO
		// (timeout / parse failure / non-200) — the results overview, performance thoughts and recommendations
		// will all render as dashes. Surface it as a job warning so the blank sections are visible rather than
		// looking like an intended empty report.
		if (live && !digests.isEmpty() && isCampaignResultsEmpty(campaign)) {
			log.warn("[report] job {} campaign results came back empty for {} tactic digest(s); "
					+ "results overview / performance thoughts / recommendations will be blank", jobId, digests.size());
			jobWarnings.add("Campaign-level results (results overview, performance thoughts, recommendations) came "
					+ "back empty from Claude despite " + digests.size() + " tactic conclusion(s); those sections "
					+ "will show dashes. Usually a Claude timeout or parse failure — re-running the report fixes it.");
		}
		ClaudeResults ccC = mergeTacticOverviews(campaign, conclusions);

		// Step 5 — final campaign narrative alignment: reconcile Batch A and the campaign results into one
		// storyline faithful to the brief, informed by a read-only digest of the breakdown conclusions. Purely
		// additive: on any failure the originals are returned, so the deck is never worse than before this ran.
		if (live) {
			List<String> breakdownDigest =
					buildBreakdownDigest(List.of(new BreakdownValues(breakdownValues, List.of())));
			ClaudeNarrative aligned = claude.batchAlignCampaign(ccA, ccC, breakdownDigest, brief, data.flightDates());
			if (aligned != null) {
				ccA = aligned.strategic();
				ccC = aligned.results();
			}
		}

		// Rebuild the narrative map from the aligned copy. The sheet overlay still wins for every numeric anchor,
		// so a missing sheet value renders blank (visible) rather than a stale raw value (silent).
		Map<String, String> flatReplacements = buildSheetFlatReplacements(
				payload, data, ccA, ccC, frequencies, tacticCount, sheetValues);
		// Funnel stages are inferred here, from the reviewed per-tactic goals, rather than from a scan of the
		// source workbook. A value the user already put on the sheet wins and costs no call at all.
		fillFunnelStages(claude, flatReplacements, tacticCount, live);
		// The tactic slide's contribution legend, derived last so it reads the same impressions the deck
		// prints — which on this flow are the ones the user reviewed on the sheet, not the parsed originals.
		contributions.fillContributions(flatReplacements, tacticCount);

		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String fileName = fileNamer.buildFileName(
				payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);
		String slideUrl = slides.createDeck(
				String.valueOf(jobId), fileName, flatReplacements, payload.reportType(), userGoogleToken);
		// Main tactic slides: on a master-model template the deck arrives with one generic tactic slide, which
		// is duplicated into exactly `tacticCount` filled slides here. Runs before the breakdowns, which anchor
		// their copies after each tactic's main slide, and before the trim, which then has no tactic slides
		// left to delete. A no-op on the legacy 28-slot template.
		jobWarnings.addAll(chartHelper.addTacticSlides(slideUrl, tacticCount, flatReplacements, userGoogleToken));
		chartHelper.trimUnusedTactics(slideUrl, tacticCount, userGoogleToken);
		// Per-tactic breakdown + thoughts slides: duplicate the selected masters, fill their tokens (already in
		// breakdownValues, including the {{thoughts on tactic n performance}} tokens for the > 2-breakdown
		// tactics), and place them after the tactic's main slide. Non-fatal — the deck still ships on failure.
		chartHelper.addBreakdownSlides(slideUrl, payload, tacticCount, breakdownValues, userGoogleToken);
		// Give each audience/device breakdown slide its own live chart: the master's embedded chart is
		// duplicated pointing at a shared, empty source workbook, so this relinks every copy to a per-tactic
		// copy of that workbook filled with the tactic's impressions. Runs after the slides exist; non-fatal.
		List<String> breakdownChartWarnings = chartHelper.buildBreakdownCharts(
				slideUrl, payload, tacticCount, flatReplacements, userGoogleToken);
		// Remove the breakdown/thoughts master template slides. Unconditional and independent of whether any
		// breakdown slides were inserted — the masters must never ship, even when Step 3 selected no
		// breakdowns. Runs after the charts so every copy has finished duplicating from the masters.
		chartHelper.deleteMasterSlides(slideUrl, userGoogleToken);
		// Slides an EOM deck must never ship (the frequency & velocity play, the awareness / market-share
		// slide): the EOM template is a copy of the EOC one, so they arrive with it and are deleted here.
		chartHelper.deleteReportTypeSlides(slideUrl, payload.reportType(), userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildChartsFromSheet(
				slideUrl, grid, flatReplacements, tacticCount, userGoogleToken);

		jobWarnings.addAll(breakdownChartWarnings);
		jobWarnings.addAll(chartWarnings);

		// Every reply Claude sent that the pipeline could not use, in the order it happened: the parse failure
		// or the wrong item count that left a slide blank, verbatim enough to act on without the server log.
		ClaudeFailureScope failures = failureLog.current();
		if (failures != null) {
			jobWarnings.addAll(failures.snapshot());
		}

		jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
		recordSlideCount(jobId, slideUrl, userGoogleToken);
		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(jobWarnings));
	}

	/**
	 * Reads every tactic's daily and monthly pacing series back out of the reviewed sheet grid.
	 *
	 * <p>Same reader, same grid and same KPI-type derivation the chart step uses later in the run, so the
	 * numbers the narrative reasons over are exactly the ones the pacing charts will plot. It is read here
	 * rather than reused from the chart step because the narrative runs first, and both series come from the
	 * one read the reader already does.
	 *
	 * @param grid        the reviewed workbook's first tab
	 * @param data        parsed campaign data supplying the tactic names the KPI types are derived from
	 * @param tacticCount number of active tactics on the sheet
	 * @return both pacing pivot maps; empty when the grid carries no pacing blocks
	 */
	SheetChartData readPacing(List<List<String>> grid, CampaignData data, int tacticCount) {
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			Tactic tactic = data == null || data.tactics() == null ? null : data.tactics().get(n);
			kpiTypes.put(n, tacticExtraction.getTacticKpiSeries(tactic == null ? null : tactic.name()));
		}
		return sheetChartData.read(grid, tacticCount, kpiTypes);
	}

	/**
	 * The tactic numbers the Step-2 conclusions call covers: every tactic on the reviewed sheet, plus any that
	 * only a breakdown section knows about, in ascending order. Every sheet tactic is included so its overview —
	 * and, through the digests, the whole downstream campaign-results narrative — is always produced from the
	 * EOC sheet figures + brief, even when no breakdown was selected.
	 *
	 * @param data the parsed campaign data whose tactic keys are the full tactic set
	 * @param pub  the publisher section inputs
	 * @param cre  the creative section inputs
	 * @param geo  the geo section inputs
	 * @param aud  the audience section inputs
	 * @param dev  the device section inputs
	 * @return every covered tactic number, ascending
	 */
	List<Integer> conclusionTacticNums(
			CampaignData data,
			BreakdownSectionInputs<PublisherObservationInput> pub,
			BreakdownSectionInputs<CreativeTakeawayInput> cre,
			BreakdownSectionInputs<GeoInsightInput> geo,
			BreakdownSectionInputs<AudienceInsightInput> aud,
			BreakdownSectionInputs<DeviceInsightInput> dev) {
		Set<Integer> tactics = new TreeSet<>();
		if (data != null && data.tactics() != null) {
			tactics.addAll(data.tactics().keySet());
		}
		tactics.addAll(pub.inputs().keySet());
		tactics.addAll(cre.inputs().keySet());
		tactics.addAll(geo.inputs().keySet());
		tactics.addAll(aud.inputs().keySet());
		tactics.addAll(dev.inputs().keySet());
		return List.copyOf(tactics);
	}

	/**
	 * Dispatches one breakdown section's dedicated per-tactic calls — one call per tactic that enabled the
	 * section — on the shared virtual-thread executor and under the same usage scope the sheet reads use, so each
	 * call's tokens are still billed to this job even though it runs off the request thread. It returns the
	 * still-running futures without joining, so the caller can dispatch every section up front and let them all
	 * run in parallel (bounded by the transport's shared Claude concurrency limit) before joining any of them.
	 *
	 * @param inputs     the section's per-tactic inputs (tactic number → the tactic's section input)
	 * @param usageScope the usage scope to run each call under so its tokens are billed to this job
	 * @param call       invokes the section's Claude call for one tactic's input, returning its validated strings
	 * @param <T>        the section's per-tactic input type
	 * @return tactic number → the running future of its section call
	 */
	<T> Map<Integer, CompletableFuture<List<String>>> dispatchSection(
			Map<Integer, T> inputs, ClaudeUsageScope usageScope, Function<T, List<String>> call) {
		Map<Integer, CompletableFuture<List<String>>> futures = new LinkedHashMap<>();
		// Both scopes are read here, on the run's own thread, and bound around each call on its worker thread —
		// otherwise a section rejected off-thread would leave neither its tokens nor its reason behind.
		ClaudeFailureScope failureScope = failureLog.current();
		inputs.forEach((tacticNum, input) -> futures.put(tacticNum, CompletableFuture.supplyAsync(
				usageTracker.inScope(usageScope, failureLog.inScope(failureScope, () -> call.apply(input))),
				applicationTaskExecutor)));
		return futures;
	}

	/**
	 * Dispatches the Step-3 per-tactic thoughts calls — one call per qualifying tactic — on the shared
	 * virtual-thread executor and under the same usage and failure scopes {@link #dispatchSection} uses, so each
	 * call's tokens are still billed to this job and its rejection reasons still reach the report card even
	 * though it runs off the request thread. It returns the still-running futures in input order without joining,
	 * so every tactic runs in parallel (bounded by the transport's shared Claude concurrency limit) instead of
	 * one after another.
	 *
	 * @param claude     the run's report-type-specific Claude client
	 * @param inputs     one input per qualifying tactic, in slide order
	 * @param usageScope the usage scope to run each call under so its tokens are billed to this job
	 * @param brief      free-text campaign brief passed through to every call
	 * @return the running futures, in input order
	 */
	List<CompletableFuture<TacticThoughts>> dispatchThoughts(
			ClaudeClient claude, List<TacticThoughtsInput> inputs, ClaudeUsageScope usageScope, String brief) {
		List<CompletableFuture<TacticThoughts>> futures = new ArrayList<>();
		// Both scopes are read here, on the run's own thread, and bound around each call on its worker thread —
		// otherwise a rejected tactic would leave neither its tokens nor its reason behind.
		ClaudeFailureScope failureScope = failureLog.current();
		for (TacticThoughtsInput input : inputs) {
			futures.add(CompletableFuture.supplyAsync(
					usageTracker.inScope(usageScope, failureLog.inScope(failureScope,
							() -> claude.tacticThoughts(input, brief))),
					applicationTaskExecutor));
		}
		return futures;
	}

	/**
	 * Joins the dispatched Step-3 futures into the thoughts list the token writer and the campaign digests
	 * expect, in input order. A tactic whose call produced no usable reply ({@code null}) is left out, so its
	 * slide ships those tokens blank rather than carrying invented copy.
	 *
	 * @param futures the running futures from {@link #dispatchThoughts}, in input order
	 * @return one entry per tactic that produced a usable reply, in input order
	 */
	List<TacticThoughts> joinThoughts(List<CompletableFuture<TacticThoughts>> futures) {
		List<TacticThoughts> thoughts = new ArrayList<>();
		for (CompletableFuture<TacticThoughts> future : futures) {
			TacticThoughts value = future.join();
			if (value != null) {
				thoughts.add(value);
			}
		}
		return thoughts;
	}

	/**
	 * Joins one section's dispatched futures into the {@code tactic → bullets} map the section write helpers
	 * expect. A tactic whose call returned no usable reply (an empty list) is left out, so its slide ships blank
	 * (and the write helper surfaces that as a warning) rather than carrying invented copy.
	 *
	 * @param futures tactic number → the running future of its section call, from {@link #dispatchSection}
	 * @return tactic number → its validated strings, only for tactics that returned a usable reply
	 */
	Map<Integer, List<String>> joinSection(Map<Integer, CompletableFuture<List<String>>> futures) {
		Map<Integer, List<String>> out = new LinkedHashMap<>();
		futures.forEach((tacticNum, future) -> {
			List<String> value = future.join();
			if (value != null && !value.isEmpty()) {
				out.put(tacticNum, value);
			}
		});
		return out;
	}

	/**
	 * Resolves the request's breakdown selections into the clamped enabled-sections map, matching how the
	 * slide-insertion step clamps: only real tactics (1..{@code tacticCount}) with at least one section enabled.
	 * This is the map the "&gt; 2 breakdowns" gate reads, so Step 3 and the thoughts slide qualify the same
	 * tactics.
	 *
	 * @param payload     the inbound generation payload carrying the breakdown selections
	 * @param tacticCount the active tactic count
	 * @return the clamped 1-based tactic number → enabled breakdown sections map
	 */
	Map<Integer, Set<BreakdownType>> qualifyingSelections(GeneratePayload payload, int tacticCount) {
		Map<Integer, Set<BreakdownType>> enabledByTactic = new LinkedHashMap<>();
		breakdownResolver.resolve(payload.breakdownSelections()).forEach((tacticNum, enabled) -> {
			if (tacticNum != null && tacticNum >= 1 && tacticNum <= tacticCount && !enabled.isEmpty()) {
				enabledByTactic.put(tacticNum, enabled);
			}
		});
		return enabledByTactic;
	}

	/**
	 * Reads each tactic's display name back out of the seed placeholder map ({@code {{tactic n}}}), for the
	 * Step-3 thoughts inputs. A blank slot is skipped.
	 *
	 * @param flatReplacements the seed placeholder map
	 * @param tacticCount      the active tactic count
	 * @return 1-based tactic number → display name, for the tactics that carry a name
	 */
	Map<Integer, String> tacticNames(Map<String, String> flatReplacements, int tacticCount) {
		Map<Integer, String> names = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name != null && !name.isBlank()) {
				names.put(n, name.trim());
			}
		}
		return names;
	}

	/**
	 * Writes the four {@code {{thoughts on tactic n performance 1..4}}} tokens for every qualifying tactic from
	 * its Step-3 thoughts, blanking a qualifying tactic that produced none so its (inserted) slide never ships a
	 * raw token. Also carries the tactic's {@code {{tactic n}}} display name into the map, so the thoughts slide
	 * title renumbers to the name the same way the breakdown slides do (their helpers copy the name token in).
	 *
	 * @param breakdownValues the breakdown token map to write the thoughts tokens into
	 * @param qualifying      the tactics that passed the "&gt; 2 breakdowns" gate (and get the thoughts slide)
	 * @param thoughts        the Step-3 thoughts that were produced
	 * @param flatReplacements the seed placeholder map, read for each tactic's {@code {{tactic n}}} display name
	 */
	void writeThoughtsTokens(
			Map<String, String> breakdownValues, Set<Integer> qualifying, List<TacticThoughts> thoughts,
			Map<String, String> flatReplacements) {
		Map<Integer, List<String>> byTactic = new LinkedHashMap<>();
		for (TacticThoughts t : thoughts) {
			if (t != null && t.thoughts() != null) {
				byTactic.put(t.tacticNum(), t.thoughts());
			}
		}
		for (Integer n : qualifying) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name != null && !name.isBlank()) {
				breakdownValues.put("{{tactic " + n + "}}", name);
			}
			List<String> tacticThoughts = byTactic.getOrDefault(n, List.of());
			for (int i = 1; i <= THOUGHTS_ON_TACTIC_COUNT; i++) {
				String value = i <= tacticThoughts.size() ? tacticThoughts.get(i - 1) : null;
				breakdownValues.put(
						"{{thoughts on tactic " + n + " performance " + i + "}}", value == null ? "" : value);
			}
		}
	}

	/**
	 * Rebuilds the campaign results with the Step-2 per-tactic overviews merged into their (empty) slot, so the
	 * tactic-overview slides fill while the campaign-level copy stays as Step 4 (and the Step-5 alignment)
	 * produced it.
	 *
	 * @param campaign    the Step-4 campaign results, carrying an empty tactic-overview map
	 * @param conclusions the Step-2 conclusions, source of the per-tactic overviews
	 * @return the campaign results with the per-tactic overviews filled in
	 */
	ClaudeResults mergeTacticOverviews(ClaudeResults campaign, List<TacticConclusion> conclusions) {
		Map<Integer, String> overviews = new LinkedHashMap<>();
		for (TacticConclusion c : conclusions) {
			if (c.overview() != null && !c.overview().isBlank()) {
				overviews.put(c.tacticNum(), c.overview());
			}
		}
		return new ClaudeResults(
				campaign.resultsOverviews(), campaign.thoughtsOnPerformance(), overviews,
				campaign.recommendations(), campaign.fOpportunity(), campaign.fFact(), campaign.fStorytelling());
	}

	/**
	 * Reports whether a campaign-results batch carries no campaign-level narrative at all, i.e. it degraded to the
	 * {@link ReportClaudeDefaults#emptyResults()} shape (empty results overviews, performance thoughts and
	 * recommendations). The per-tactic {@code tacticOverviews} and the frequency copy are deliberately excluded:
	 * the overviews are merged in from the Step-2 conclusions afterwards, and the frequency copy is legitimately
	 * {@code null} when the sheet has no reach data, so neither indicates a Batch C failure.
	 *
	 * @param campaign the raw {@link ClaudeResults} returned by {@code batchCampaignResults}, before overview merge
	 * @return {@code true} when the results overviews, performance thoughts and recommendations are all empty
	 */
	boolean isCampaignResultsEmpty(ClaudeResults campaign) {
		return campaign.resultsOverviews().isEmpty()
				&& campaign.thoughtsOnPerformance().isEmpty()
				&& campaign.recommendations().isEmpty();
	}

	/**
	 * Returns the highest tactic number the collector recognised in the campaign data, clamped to
	 * 1..{@link #MAX_TACTICS}. The collector's {@code data.tactics()} keys already exclude unnamed slots, so
	 * this is the real tactic count used to bound the Slides placeholder map to tactics the campaign has.
	 *
	 * @param data the aggregated campaign snapshot whose tactic keys bound the count
	 * @return the real tactic count (1..28)
	 */
	int maxTacticNumber(CampaignData data) {
		int max = 0;
		if (data != null && data.tactics() != null) {
			for (Integer n : data.tactics().keySet()) {
				if (n != null && n > max) {
					max = n;
				}
			}
		}
		return Math.clamp(max, 1, MAX_TACTICS);
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
		// A deck is always built for at least one tactic: this flow only runs on a workbook that
		// already passed review, so an unnamed first tactic means a bad read, not an empty report.
		return Math.clamp(tacticCounter.countFromPlaceholders(flatReplacements), 1, MAX_TACTICS);
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
				null, null, null, payload.dateFilter(), payload.sheetUrl(),
				payload.changeLog(), payload.estimateDaypartGender());
	}

	/**
	 * Assembles the slides-from-sheet placeholder map: the Claude-authored narrative first (resolved from a
	 * grid-stripped payload so numbers cannot come from the raw plan), then the reviewed-sheet overlay — which
	 * wins for every numeric anchor — then the sheet-token aliases. Extracted because the flow builds this map
	 * twice: once to seed the breakdown batches, then again from the Batch D-aligned narrative.
	 *
	 * @param payload     the inbound generation payload
	 * @param data        the campaign data read back from the reviewed sheet
	 * @param ccA         the (possibly aligned) Batch A strategic narrative
	 * @param ccC         the (possibly aligned) Batch C results copy
	 * @param frequencies the frequencies reconstructed from the reviewed sheet
	 * @param tacticCount the active tactic count
	 * @param sheetValues the reviewed-sheet placeholder values overlaid on top of the narrative
	 * @return the merged placeholder map, sheet values winning over narrative, with aliases applied
	 */
	Map<String, String> buildSheetFlatReplacements(
			GeneratePayload payload, CampaignData data, ClaudeStrategic ccA, ClaudeResults ccC,
			CampaignFrequencies frequencies, int tacticCount, Map<String, String> sheetValues) {
		GeneratePayload narrativePayload = narrativeOnly(payload);
		Map<String, String> narrative = placeholders.buildFlatReplacements(
				narrativePayload, data, ccA, claudeDefaults.emptyTactical(), ccC, null, null, null, null, null,
				frequencies, tacticCount);
		Map<String, String> flat = new LinkedHashMap<>(narrative);
		flat.putAll(sheetValues);
		aliasSheetTokens(flat, tacticCount);
		return flat;
	}

	/**
	 * Flattens the per-tactic breakdown conclusions into a compact, bounded list of {@code token: text} lines
	 * for Batch D to read as read-only alignment context. Blank values are skipped, each line is capped at
	 * {@link #BREAKDOWN_DIGEST_LINE_MAX} characters, and the whole digest is capped at
	 * {@link #BREAKDOWN_DIGEST_MAX_LINES} lines so a large, many-tactic deck cannot bloat the alignment prompt.
	 *
	 * @param sections the built breakdown-value bundles (publisher/creative/geo/audience/device), any of which
	 *                 may be empty when its section was not selected
	 * @return one short line per non-blank breakdown conclusion, bounded in both line length and count
	 */
	List<String> buildBreakdownDigest(List<BreakdownValues> sections) {
		List<String> digest = new ArrayList<>();
		for (BreakdownValues section : sections) {
			if (section == null || section.values() == null) {
				continue;
			}
			for (Map.Entry<String, String> entry : section.values().entrySet()) {
				String value = entry.getValue();
				if (value == null || value.isBlank()) {
					continue;
				}
				String tag = entry.getKey().replace("{{", "").replace("}}", "").trim();
				String line = tag + ": " + value.trim();
				if (line.length() > BREAKDOWN_DIGEST_LINE_MAX) {
					line = line.substring(0, BREAKDOWN_DIGEST_LINE_MAX);
				}
				digest.add(line);
				if (digest.size() >= BREAKDOWN_DIGEST_MAX_LINES) {
					return digest;
				}
			}
		}
		return digest;
	}

	/**
	 * Fills {@code {{funnel_stages}}} from the campaign's per-tactic goals when nothing else resolved it.
	 *
	 * <p>Replaces the old whole-workbook scan: that call flattened every tab of the media plan into one
	 * prompt — megabytes on a large client plan, enough on its own to overrun the model's context window —
	 * to produce a single ≤60-character line. The per-tactic goals already carry exactly the signal the
	 * stages are inferred from, they are a dozen short strings, and in the sheet flow the user has seen and
	 * can correct them before this runs.
	 *
	 * <p>A manual or media-plan value always wins: the map is only touched when the token is missing, blank
	 * or a dash, so a reviewed value is never overwritten and costs no request at all.
	 *
	 * @param claude           the run's report-type-specific Claude client
	 * @param flatReplacements the placeholder map to fill, mutated in place
	 * @param tacticCount      number of real tactics whose {@code {{tactic n goal}}} values are read
	 * @param live             whether a live Claude client is configured; no call is made when it is not
	 */
	void fillFunnelStages(
			ClaudeClient claude, Map<String, String> flatReplacements, int tacticCount, boolean live) {
		if (!live) {
			return;
		}
		String current = flatReplacements.get(FUNNEL_STAGES_TOKEN);
		if (current != null && !current.isBlank() && !DASH.equals(current.trim())) {
			return;
		}
		List<String> goals = new ArrayList<>();
		for (int n = 1; n <= tacticCount; n++) {
			String goal = flatReplacements.get("{{tactic " + n + " goal}}");
			if (goal != null && !goal.isBlank() && !DASH.equals(goal.trim())) {
				goals.add(goal.trim());
			}
		}
		if (goals.isEmpty()) {
			return;
		}
		String stages = claude.summarizeFunnelStages(goals);
		if (stages != null && !stages.isBlank()) {
			flatReplacements.put(FUNNEL_STAGES_TOKEN, stages.trim());
		}
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
