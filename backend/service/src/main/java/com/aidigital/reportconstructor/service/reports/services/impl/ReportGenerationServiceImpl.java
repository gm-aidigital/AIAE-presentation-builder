package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
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
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.CreativeBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.DeviceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.GeoBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.PublisherBreakdownHelper;
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

import java.util.ArrayList;
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
							funnelSummary, frequencies, flatTacticCount);
			UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
			String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);
			String fileName = fileNamer.buildFileName(
					payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);

			if (target == GenerationTarget.SHEET) {
				jobProgress.markJobRunningAtStep(jobId, 6, "Building sheet");
				String sheetUrl = sheetHelper.buildSheet(
						String.valueOf(jobId), fileName, flatReplacements, userGoogleToken);
				sheetHelper.trimUnusedTactics(sheetUrl, payload, userGoogleToken);
				sheetHelper.clearUnselectedBreakdowns(sheetUrl, payload, userGoogleToken);

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

		// Breakdown copy is generated BEFORE the deck is built so the Batch D alignment pass can reconcile the
		// campaign-level narrative against the deeper per-tactic conclusions. The values do not need the deck —
		// only their later insertion does — and the helpers read only sheet-derived tokens (tactic names, gender
		// splits) that Batch D never rewrites, so seeding them from the pre-alignment map is safe. A preliminary
		// map is used here; the map that actually fills the deck is rebuilt below from the aligned narrative.
		Map<String, String> prelim = buildSheetFlatReplacements(
				payload, data, ccA, ccC, frequencies, tacticCount, sheetValues);
		BreakdownValues publisherValues = publisherBreakdown.buildPublisherValues(
				payload.sheetUrl(), payload.breakdownSelections(), prelim, brief, userGoogleToken);
		BreakdownValues creativeValues = creativeBreakdown.buildCreativeValues(
				payload.sheetUrl(), payload.breakdownSelections(), prelim, brief, userGoogleToken);
		BreakdownValues geoValues = geoBreakdown.buildGeoValues(
				payload.sheetUrl(), payload.breakdownSelections(), prelim, brief, userGoogleToken);
		BreakdownValues audienceValues = audienceBreakdown.buildAudienceValues(
				payload.sheetUrl(), payload.breakdownSelections(), prelim, brief, userGoogleToken);
		BreakdownValues deviceValues = deviceBreakdown.buildDeviceValues(
				payload.sheetUrl(), payload.breakdownSelections(), prelim, brief, userGoogleToken);

		// Batch D — final narrative alignment. Reconcile the independently written proposal, strategic insights,
		// results overviews, thoughts and frequency copy into one storyline faithful to the brief, informed by a
		// read-only digest of the breakdown conclusions. Purely additive: on any failure the originals are
		// returned, so the deck is never worse than before this pass ran.
		if (live) {
			List<String> breakdownDigest = buildBreakdownDigest(
					List.of(publisherValues, creativeValues, geoValues, audienceValues, deviceValues));
			ClaudeNarrative aligned = claude.batchAlignNarrative(ccA, ccC, breakdownDigest, brief);
			if (aligned != null) {
				ccA = aligned.strategic();
				ccC = aligned.results();
			}
		}

		// Rebuild the narrative map from the aligned copy. The sheet overlay still wins for every numeric anchor,
		// so a missing sheet value renders blank (visible) rather than a stale raw value (silent).
		Map<String, String> flatReplacements = buildSheetFlatReplacements(
				payload, data, ccA, ccC, frequencies, tacticCount, sheetValues);

		jobProgress.markJobRunningAtStep(jobId, 6, "Building slide deck");
		String fileName = fileNamer.buildFileName(
				payload.reportType(), flatReplacements.get(CLIENT_NAME_TOKEN), userEmail);
		String slideUrl = slides.createDeck(String.valueOf(jobId), fileName, flatReplacements, userGoogleToken);
		chartHelper.trimUnusedTactics(slideUrl, tacticCount, userGoogleToken);
		// Step-3 per-tactic breakdown slides: duplicate the selected master slides, fill their tokens for
		// each tactic, and place them after the tactic's main slide. Non-fatal — the deck still ships
		// without them on failure.
		//
		// The values were assembled above (before the deck existed) because these slides do not exist yet when
		// createDeck runs its placeholder pass: they are duplicated from the masters afterwards, so their tokens
		// have to be filled as part of that same insertion.
		//
		// One map across all breakdown sections: each helper only emits tokens for the tactics that enabled
		// its own section, and the sections share no tokens, so the merge cannot collide.
		Map<String, String> breakdownValues = new LinkedHashMap<>(publisherValues.values());
		breakdownValues.putAll(creativeValues.values());
		breakdownValues.putAll(geoValues.values());
		breakdownValues.putAll(audienceValues.values());
		breakdownValues.putAll(deviceValues.values());
		chartHelper.addBreakdownSlides(slideUrl, payload, tacticCount, breakdownValues, userGoogleToken);
		// Give each audience/device breakdown slide its own live chart: the master's embedded chart is
		// duplicated pointing at a shared, empty source workbook, so this relinks every copy to a per-tactic
		// copy of that workbook filled with the tactic's impressions. Runs after the slides exist; non-fatal.
		List<String> breakdownChartWarnings = chartHelper.buildBreakdownCharts(
				slideUrl, payload, tacticCount, flatReplacements, userGoogleToken);

		jobProgress.markJobRunningAtStep(jobId, 7, "Building charts");
		List<String> chartWarnings = chartHelper.buildChartsFromSheet(
				slideUrl, grid, flatReplacements, tacticCount, userGoogleToken);

		// One list for the "Report ready" card: a breakdown slide that shipped without its Claude bullets is
		// as much a partial result as a chart that could not be drawn, and is invisible on the slide itself.
		List<String> jobWarnings = new ArrayList<>(publisherValues.warnings());
		jobWarnings.addAll(creativeValues.warnings());
		jobWarnings.addAll(geoValues.warnings());
		jobWarnings.addAll(audienceValues.warnings());
		jobWarnings.addAll(deviceValues.warnings());
		jobWarnings.addAll(breakdownChartWarnings);
		jobWarnings.addAll(chartWarnings);

		jobProgress.recordArtifact(jobId, fileName, payload.sheetUrl());
		jobProgress.markJobDone(jobId, slideUrl, warnings.serializeWarnings(jobWarnings));
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
				null, null, null, payload.dateFilter(), payload.sheetUrl(), payload.changeLog());
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
				narrativePayload, data, ccA, claudeDefaults.emptyTactical(), ccC, null, null, null, frequencies,
				tacticCount);
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
