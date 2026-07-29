package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownThoughtsGate;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.DuplicateObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.UpdateSlidesPositionRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Google Slides + Drive implementation. Clones {@code SLIDES_TEMPLATE_ID} (or
 * {@code EOM_SLIDES_TEMPLATE_ID} for an EOM report, see {@link #templateIdFor})
 * into a new deck named after the job, runs {@code replaceAllText} for every
 * {@code {token}} → value pair, and returns the public Slides edit URL.
 *
 * <p>Activated when {@link GoogleCredentialsFactory} is on the context.
 * Falls back to {@code StubSlidesProvider} otherwise.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealSlidesProvider implements SlidesProvider {

	private static final String APPLICATION_NAME = "Report Constructor — AI Digital";

	/** Max tactics the deck template carries (per-tactic detail slides 1..28). */
	private static final int MAX_TACTICS = 28;

	/**
	 * Max requests sent in a single {@code batchUpdate}. Mirrors the Sheets provider: a full 28-tactic
	 * report expands to ~800 {@code replaceAllText} operations, and packing them into one atomic
	 * batchUpdate risks repeated 500/503 {@code backendError}s from Slides under the payload weight (the
	 * same failure that hit the Sheets step on job 128). Splitting into fixed-size chunks — each its own
	 * batchUpdate, retried independently — keeps every request small enough for Slides to accept. Safe
	 * because replaceAllText targets disjoint tokens and the trim's ordered deletes stay in sequence
	 * across chunk boundaries, so chunking never changes the outcome.
	 */
	private static final int BATCH_UPDATE_CHUNK_SIZE = 100;

	/** Tactics per group; the deck carries one summary slide + one "Our results" slide per group. */
	private static final int TACTICS_PER_GROUP = 7;

	/** Number of tactic groups (28 tactics / 7 per group). */
	private static final int GROUP_COUNT = 4;

	/**
	 * Field mask for the {@code presentations.get} used by {@link #addBreakdownSlides}: only the slide
	 * order ({@code objectId}) and the text needed to discover {@code {{…}}} tokens on the master slides
	 * (shape text and table-cell text). Keeping the mask tight avoids pulling the whole deck back.
	 */
	private static final String BREAKDOWN_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements.shape.text.textElements.textRun.content,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #deleteMasterSlides}: only the slide
	 * order ({@code objectId}) is needed to check which configured masters are present in the deck.
	 */
	private static final String MASTER_FIELDS = "slides.objectId";

	/** Matches a whole {@code {{…}}} placeholder token (no nested braces). */
	private static final Pattern TOKEN = Pattern.compile("\\{\\{[^{}]*\\}\\}");

	/**
	 * Matches the standalone tactic variable {@code n} inside a token: a single {@code n} or {@code N}
	 * bounded on both sides by a token delimiter ({@code _}, {@code .}, whitespace, or a brace), so the
	 * {@code n} in words like {@code name} or {@code imps} is never touched. Both cases are accepted
	 * because master templates use either (e.g. {@code {{tactic n}}} vs {@code {{tactic N}}}). Replaced
	 * with the tactic number.
	 */
	private static final Pattern N_VARIABLE = Pattern.compile("(?<=[_.\\s{])[nN](?=[_.\\s}])");

	private final GoogleCredentialsFactory creds;
	private final GoogleRequestRetrier retrier;
	private final DriveSharer driveSharer;
	private final DriveShareRecipients shareRecipients;
	private final Slides slides;
	private final Drive drive;
	private final String slidesTemplateId;
	private final String eomSlidesTemplateId;
	private final String targetFolderId;
	private final Map<Integer, String> summaryTableObjectIds;
	private final Map<Integer, String> summarySlideObjectIds;
	private final Map<Integer, String> resultsSlideObjectIds;
	private final Map<Integer, String> tacticSlideObjectIds;
	private final Map<BreakdownType, String> breakdownMasterIds;
	private final String thoughtsMasterId;
	private final BreakdownSlideNaming breakdownSlideNaming;
	private final BreakdownThoughtsGate thoughtsGate;

	public RealSlidesProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, DriveSharer driveSharer,
			DriveShareRecipients shareRecipients, GoogleRequestRetrier retrier,
			BreakdownSlideNaming breakdownSlideNaming, BreakdownThoughtsGate thoughtsGate) {
		String slidesTemplateId = props.getSlidesTemplateId();
		String eomSlidesTemplateId = props.getEomSlidesTemplateId();
		String targetFolderId = props.getSlidesTargetFolderId();
		this.summaryTableObjectIds = props.getSummaryTableObjectIds();
		this.summarySlideObjectIds = props.getSummarySlideObjectIds();
		this.resultsSlideObjectIds = props.getResultsSlideObjectIds();
		this.tacticSlideObjectIds = props.getTacticSlideObjectIds();
		this.breakdownMasterIds = resolveBreakdownMasterIds(props.getBreakdownMasterSlideObjectIds());
		String thoughtsMaster = props.getThoughtsMasterSlideObjectId();
		this.thoughtsMasterId = thoughtsMaster == null ? "" : thoughtsMaster.trim();
		this.breakdownSlideNaming = breakdownSlideNaming;
		this.thoughtsGate = thoughtsGate;
		this.driveSharer = driveSharer;
		this.retrier = retrier;
		this.shareRecipients = shareRecipients;
		this.creds = creds;
		this.slides = new Slides.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.slidesTemplateId = slidesTemplateId;
		this.eomSlidesTemplateId = eomSlidesTemplateId;
		this.targetFolderId = targetFolderId == null ? "" : targetFolderId.trim();
	}

	/**
	 * Resolves which template deck to clone for the given report type: the EOM deck when
	 * {@code reportType} is {@code "EOM"} and an EOM template id is configured, otherwise the EOC
	 * (default) deck.
	 *
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return the template id to clone
	 */
	String templateIdFor(String reportType) {
		if ("EOM".equals(reportType) && eomSlidesTemplateId != null && !eomSlidesTemplateId.isBlank()) {
			return eomSlidesTemplateId;
		}
		return slidesTemplateId;
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken) {
		String templateId = templateIdFor(reportType);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Drive driveClient = asUser ? buildDrive(userGoogleAccessToken) : drive;
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			File copy = new File().setName(fileName);
			if (!targetFolderId.isEmpty()) {
				copy.setParents(List.of(targetFolderId));
			} else if (asUser) {
				// Drive's files.copy inherits the source's parent when no parent
				// is given, so the deck would land in the template's (shared)
				// folder and show up under the user's "Shared with me" instead of
				// "My Drive". Force the user's own My Drive root so the deck is
				// both owned by — and located in — the signed-in user's drive.
				copy.setParents(List.of("root"));
			}
			File copied = retrier.execute(
					driveClient.files().copy(templateId, copy)
							.setFields("id,webViewLink")
							.setSupportsAllDrives(true),
					"createDeck copy of " + templateId);
			String newId = copied.getId();

			List<Request> requests = new ArrayList<>(placeholderMap.size());
			for (Map.Entry<String, String> e : placeholderMap.entrySet()) {
				// Template tokens are double-brace {{...}} — the key is already the full token.
				String token = e.getKey();
				requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
						.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
						.setReplaceText(e.getValue() == null ? "" : e.getValue())));
			}
			if (!requests.isEmpty()) {
				// Fill the freshly-copied deck before granting access. Sharing is a Drive ACL write on the
				// same file, and issuing it concurrently with a large content batchUpdate lets Google abort
				// the write with 409 ABORTED (seen on long decks, whose batchUpdate runs longer). Each chunk
				// is retried on transient conflicts/rate limits — Slides batchUpdate is atomic, so a retry
				// never double-applies. Chunking also shortens each write, further reducing the 409 window.
				executeInChunks(slidesClient, newId, requests, "createDeck batchUpdate for " + newId);
			}

			// Grant standing access to the configured recipients (e.g. an admin owner) so decks created in a
			// user's own My Drive remain reachable — after the fill, to keep the ACL write off the content write.
			driveSharer.shareWith(driveClient, newId, shareRecipients.resolve());
			return "https://docs.google.com/presentation/d/" + newId + "/edit";
		} catch (IOException ex) {
			log.error("[slides] createDeck failed for job {}", jobId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides deck creation failed: " + ex.getMessage());
		}
	}

	@Override
	public void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken) {
		if (tacticCount >= MAX_TACTICS) {
			return;
		}
		List<Request> requests = trimRequests(tacticCount);
		if (requests.isEmpty()) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			executeInChunks(slidesClient, presentationId, requests, "trimTactics batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] trimTactics failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides trimTactics failed: " + ex.getMessage());
		}
	}

	/**
	 * Applies the given batchUpdate requests to a deck in fixed-size chunks of at most
	 * {@link #BATCH_UPDATE_CHUNK_SIZE}, each sent as its own {@code batchUpdate} and retried
	 * independently via {@link GoogleRequestRetrier}. Mirrors {@code RealSheetDeckProvider#executeInChunks}:
	 * a single batchUpdate carrying every {@code replaceAllText} (~800 for a full 28-tactic report) risks
	 * repeated 500/503 {@code backendError}s from Slides; chunking keeps each request small. Chunks are
	 * sent in list order, so the trim's bottom-up {@code deleteTableRow} sequence stays valid across
	 * boundaries, and replaceAllText targets disjoint tokens — chunking never changes the result.
	 *
	 * @param slidesClient   the authenticated Slides client
	 * @param presentationId the deck to update
	 * @param requests       the batchUpdate requests to apply, in list order
	 * @param description    short context used in retry log lines
	 * @throws IOException when a chunk fails with a non-retryable error or exhausts all attempts
	 */
	void executeInChunks(Slides slidesClient, String presentationId, List<Request> requests, String description)
			throws IOException {
		int total = requests.size();
		int chunks = (total + BATCH_UPDATE_CHUNK_SIZE - 1) / BATCH_UPDATE_CHUNK_SIZE;
		for (int start = 0, index = 1; start < total; start += BATCH_UPDATE_CHUNK_SIZE, index++) {
			int end = Math.min(start + BATCH_UPDATE_CHUNK_SIZE, total);
			List<Request> chunk = new ArrayList<>(requests.subList(start, end));
			retrier.execute(
					slidesClient.presentations()
							.batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(chunk)),
					description + " (chunk " + index + "/" + chunks + ")");
		}
	}

	/**
	 * Builds the delete requests for a deck trimmed to {@code tacticCount} tactics: the surplus
	 * per-tactic detail slides, the surplus "Our results" and summary group slides, and the last
	 * partial summary table's unused rows. Tactics are grouped 7‑per‑group (group 1 → tactics 1–7,
	 * group 2 → 8–14, …); {@code groups = ceil(tacticCount / 7)} and {@code usedInLastGroup} is how
	 * many of the last group's 7 rows are real. Requests are emitted only for configured (non-blank)
	 * object ids, so an unconfigured deck degrades to a safe no-op.
	 *
	 * @param tacticCount number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @return the ordered list of delete requests (empty when nothing is configured to trim)
	 */
	List<Request> trimRequests(int tacticCount) {
		int groups = (tacticCount + TACTICS_PER_GROUP - 1) / TACTICS_PER_GROUP;
		int usedInLastGroup = tacticCount - (groups - 1) * TACTICS_PER_GROUP;

		List<Request> requests = new ArrayList<>();
		// Surplus per-tactic detail slides.
		for (int t = tacticCount + 1; t <= MAX_TACTICS; t++) {
			addDeleteObject(requests, tacticSlideObjectIds.get(t));
		}
		// Surplus "Our results" and summary slides for empty groups.
		for (int g = groups + 1; g <= GROUP_COUNT; g++) {
			addDeleteObject(requests, resultsSlideObjectIds.get(g));
			addDeleteObject(requests, summarySlideObjectIds.get(g));
		}
		// Unused rows of the last (partial) summary table, bottom-up so earlier indices don't shift.
		// Row 0 is the header; tactic rows occupy indices 1..7.
		String lastTableId = summaryTableObjectIds.get(groups);
		if (lastTableId != null && !lastTableId.isBlank()) {
			for (int row = TACTICS_PER_GROUP; row >= usedInLastGroup + 1; row--) {
				requests.add(new Request().setDeleteTableRow(new DeleteTableRowRequest()
						.setTableObjectId(lastTableId)
						.setCellLocation(new TableCellLocation().setRowIndex(row).setColumnIndex(0))));
			}
		}
		return requests;
	}

	/**
	 * Appends a {@code DeleteObject} request for the given page-element object id, but only when it
	 * is configured (non-blank) — so missing object ids are skipped rather than failing the trim.
	 *
	 * @param requests the accumulating request list
	 * @param objectId the slide/page-element object id to delete (may be {@code null}/blank)
	 */
	void addDeleteObject(List<Request> requests, String objectId) {
		if (objectId != null && !objectId.isBlank()) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
		}
	}

	@Override
	public void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String userGoogleAccessToken) {
		if (enabledByTactic == null || enabledByTactic.isEmpty() || breakdownMasterIds.isEmpty()) {
			return;
		}
		Map<String, String> values = breakdownValues == null ? Map.of() : breakdownValues;
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addBreakdownSlides get " + presentationId);
			List<Request> requests =
					buildBreakdownRequests(deck.getSlides(), breakdownMasterIds, enabledByTactic, values);
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addBreakdownSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addBreakdownSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides addBreakdownSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests that insert the selected per-tactic breakdown slides into
	 * an already-built deck: duplicate each master, renumber its {@code n} tokens to the tactic number
	 * (scoped to the copy), then position each tactic's copies right after its main slide. Requests are
	 * emitted in two ordered phases — all duplicates+renumbers, then all position moves — so a later
	 * request never references a slide an earlier one has not yet created. The masters themselves are left
	 * in place for {@link #deleteMasterSlides} to remove in a separate, unconditional pass.
	 *
	 * <p>Only tactics whose main slide is present in the deck and that enable at least one breakdown with
	 * a configured, in-deck master are processed; everything else is skipped so the method degrades to a
	 * safe no-op. The position math assumes the master slides (and therefore the freshly duplicated
	 * copies, which land next to their masters) sit after every tactic slide — the deck's fixed layout —
	 * so duplicating never shifts a tactic slide's index. Tactics are positioned in ascending main-slide
	 * order while accumulating the number of already-inserted copies, keeping every insertion index valid
	 * against the live arrangement.
	 *
	 * @param slides           the deck's slides in order (from {@code presentations.get}), carrying master
	 *                         text for token discovery
	 * @param masterIds        configured master slide object id per breakdown type
	 * @param enabledByTactic  1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues  renumbered token → final value; a token found here is written straight to
	 *                         its value, one found nowhere is only renumbered
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildBreakdownRequests(
			List<Page> slides, Map<BreakdownType, String> masterIds,
			Map<Integer, Set<BreakdownType>> enabledByTactic, Map<String, String> breakdownValues) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty() || masterIds.isEmpty() || enabledByTactic.isEmpty()) {
			return requests;
		}

		Map<String, Integer> indexById = new HashMap<>();
		Map<String, Page> pageById = new HashMap<>();
		for (int i = 0; i < slides.size(); i++) {
			Page page = slides.get(i);
			if (page.getObjectId() != null) {
				indexById.put(page.getObjectId(), i);
				pageById.put(page.getObjectId(), page);
			}
		}

		// Qualifying tactics → their enabled breakdown types in declaration (priority) order, and the
		// index of each tactic's main slide (used to order and place the copies).
		Map<Integer, List<BreakdownType>> orderedByTactic = new LinkedHashMap<>();
		TreeMap<Integer, Integer> tacticByMainSlideIndex = new TreeMap<>();
		for (Map.Entry<Integer, Set<BreakdownType>> entry : enabledByTactic.entrySet()) {
			Integer tacticNum = entry.getKey();
			if (tacticNum == null || entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}
			String mainSlideId = tacticSlideObjectIds.get(tacticNum);
			if (mainSlideId == null || !indexById.containsKey(mainSlideId)) {
				log.warn("[slides] addBreakdownSlides: no main slide for tactic {} in deck — skipping", tacticNum);
				continue;
			}
			List<BreakdownType> types = new ArrayList<>();
			for (BreakdownType type : BreakdownType.values()) {
				String masterId = masterIds.get(type);
				if (entry.getValue().contains(type) && masterId != null && indexById.containsKey(masterId)) {
					types.add(type);
				}
			}
			if (types.isEmpty()) {
				continue;
			}
			orderedByTactic.put(tacticNum, types);
			tacticByMainSlideIndex.put(indexById.get(mainSlideId), tacticNum);
		}
		if (orderedByTactic.isEmpty()) {
			return requests;
		}

		// Phase 1: duplicate each master, then write each of its n-tokens on the copy — to the token's
		// final value when one is known, otherwise just renumbered. Both are scoped to the copy, which is
		// what stops identical master tokens on different copies from overwriting each other. The deck's
		// global placeholder pass has already run by now and will not run again, so a token left merely
		// renumbered here stays raw in the delivered deck.
		Map<BreakdownType, Set<String>> tokensByType = new EnumMap<>(BreakdownType.class);
		Map<Integer, List<String>> copyIdsByTactic = new LinkedHashMap<>();
		// The "Thoughts on tactic performance" master is one generic slide (not a breakdown type): it is
		// duplicated once per tactic that passes the shared ">2 breakdowns" gate and appended after that
		// tactic's breakdown copies. A blank or absent master disables it as a safe no-op.
		boolean thoughtsEnabled = !thoughtsMasterId.isBlank() && indexById.containsKey(thoughtsMasterId);
		Set<String> thoughtsTokens = thoughtsEnabled
				? extractRenumberableTokens(pageById.get(thoughtsMasterId)) : Set.of();
		for (Map.Entry<Integer, List<BreakdownType>> entry : orderedByTactic.entrySet()) {
			int tacticNum = entry.getKey();
			List<String> copyIds = new ArrayList<>();
			for (BreakdownType type : entry.getValue()) {
				String masterId = masterIds.get(type);
				String copyId = breakdownSlideId(type, tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(masterId)
						.setObjectIds(Map.of(masterId, copyId))));
				Set<String> tokens = tokensByType.computeIfAbsent(
						type, t -> extractRenumberableTokens(pageById.get(masterId)));
				emitRenumberedTokens(requests, copyId, tacticNum, tokens, breakdownValues);
				copyIds.add(copyId);
			}
			// Append the tactic's thoughts copy last, so it lands right after its final breakdown slide.
			if (thoughtsEnabled && thoughtsGate.qualifies(enabledByTactic.get(tacticNum))) {
				String thoughtsCopyId = breakdownSlideNaming.thoughtsSlideId(tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(thoughtsMasterId)
						.setObjectIds(Map.of(thoughtsMasterId, thoughtsCopyId))));
				emitRenumberedTokens(requests, thoughtsCopyId, tacticNum, thoughtsTokens, breakdownValues);
				copyIds.add(thoughtsCopyId);
			}
			copyIdsByTactic.put(tacticNum, copyIds);
		}

		// Phase 2: place each tactic's copies right after its main slide. Processing in ascending
		// main-slide order and adding the running count of already-inserted copies keeps every
		// insertion index correct against the live arrangement (copies are pulled from after all
		// tactic slides, so earlier tactics' slide indices never shift).
		int inserted = 0;
		for (Map.Entry<Integer, Integer> entry : tacticByMainSlideIndex.entrySet()) {
			int mainSlideIndex = entry.getKey();
			List<String> copyIds = copyIdsByTactic.get(entry.getValue());
			requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
					.setSlideObjectIds(new ArrayList<>(copyIds))
					.setInsertionIndex(mainSlideIndex + 1 + inserted)));
			inserted += copyIds.size();
		}

		// The master slides are intentionally left in place here: {@link #deleteMasterSlides} removes them
		// in a separate, unconditional pass, so masters are cleaned even when no breakdowns were selected
		// (and this method returned early).
		return requests;
	}

	@Override
	public void deleteMasterSlides(String presentationId, String userGoogleAccessToken) {
		if (breakdownMasterIds.isEmpty() && thoughtsMasterId.isBlank()) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(MASTER_FIELDS),
					"deleteMasterSlides get " + presentationId);
			List<Request> requests = buildMasterDeleteRequests(deck.getSlides());
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"deleteMasterSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteMasterSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides deleteMasterSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the {@code DeleteObject} requests that remove every configured breakdown master and the
	 * thoughts master that is actually present in the deck. Presence is checked against the deck's slide
	 * object ids so a master configured but absent from this template variant (or already deleted) is
	 * skipped rather than failing the batch. Breakdown masters are de-duplicated and emitted before the
	 * thoughts master.
	 *
	 * @param slides the deck's slides in order (from {@code presentations.get} with {@link #MASTER_FIELDS})
	 * @return the ordered delete requests, or an empty list when no configured master is present
	 */
	List<Request> buildMasterDeleteRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		Set<String> presentIds = new HashSet<>();
		for (Page page : slides) {
			if (page.getObjectId() != null) {
				presentIds.add(page.getObjectId());
			}
		}
		for (String masterId : new LinkedHashSet<>(breakdownMasterIds.values())) {
			if (masterId != null && presentIds.contains(masterId)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(masterId)));
			}
		}
		if (!thoughtsMasterId.isBlank() && presentIds.contains(thoughtsMasterId)) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(thoughtsMasterId)));
		}
		return requests;
	}

	/**
	 * Emits the copy-scoped {@code replaceAllText} requests that turn a duplicated slide's generic {@code n}
	 * tokens into the tactic's concrete tokens — replaced with the token's final value when one is known in
	 * {@code breakdownValues}, otherwise just renumbered. Scoping every replacement to {@code copyId} is what
	 * stops identical master tokens on sibling copies from overwriting each other. A token that renumbers to
	 * itself (carries no {@code n}) is skipped. Shared by the breakdown copies and the thoughts copy so both
	 * fill their tokens identically.
	 *
	 * @param requests        the request list to append the replacements to
	 * @param copyId          the duplicated slide's object id, used to scope each replacement
	 * @param tacticNum       the 1-based tactic number the copy belongs to
	 * @param tokens          the master's distinct renumberable tokens
	 * @param breakdownValues renumbered token → final value; a token found here is written straight to its value
	 */
	void emitRenumberedTokens(
			List<Request> requests, String copyId, int tacticNum, Set<String> tokens,
			Map<String, String> breakdownValues) {
		for (String token : tokens) {
			String concrete = renumber(token, tacticNum);
			String value = breakdownValues.get(concrete);
			String replacement = value != null ? value : concrete;
			if (replacement.equals(token)) {
				continue;
			}
			requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
					.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
					.setReplaceText(replacement)
					.setPageObjectIds(List.of(copyId))));
		}
	}

	/**
	 * Extracts every distinct {@code {{…}}} token on a master slide, concatenating the text runs within
	 * each shape and table cell first so tokens Google split across runs are reassembled before matching.
	 *
	 * @param master the master slide page (may be {@code null} when not found)
	 * @return the distinct tokens found, in first-seen order (empty when the page has no text)
	 */
	Set<String> extractRenumberableTokens(Page master) {
		Set<String> tokens = new LinkedHashSet<>();
		if (master == null || master.getPageElements() == null) {
			return tokens;
		}
		for (PageElement element : master.getPageElements()) {
			if (element.getShape() != null) {
				collectTokens(element.getShape().getText(), tokens);
			}
			Table table = element.getTable();
			if (table != null && table.getTableRows() != null) {
				for (TableRow row : table.getTableRows()) {
					if (row.getTableCells() == null) {
						continue;
					}
					for (TableCell cell : row.getTableCells()) {
						collectTokens(cell.getText(), tokens);
					}
				}
			}
		}
		return tokens;
	}

	/**
	 * Concatenates the text runs of one text container (a shape or a table cell) and adds any
	 * {@code {{…}}} tokens found to the accumulator. Concatenating first is what lets a token split
	 * across runs still match as one string.
	 *
	 * @param text   the text container (may be {@code null})
	 * @param tokens the accumulating set of distinct tokens
	 */
	void collectTokens(TextContent text, Set<String> tokens) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		StringBuilder joined = new StringBuilder();
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
		Matcher matcher = TOKEN.matcher(joined.toString());
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
	}

	/**
	 * Renumbers the standalone tactic variable {@code n} in a token to the given tactic number, leaving
	 * every other character (including an {@code n} inside a word) untouched.
	 *
	 * @param token     the generic master token (e.g. {@code {{publisher_n.1}}})
	 * @param tacticNum the 1-based tactic number to substitute for {@code n}
	 * @return the renumbered token (e.g. {@code {{publisher_3.1}}}); unchanged when it carries no {@code n}
	 */
	String renumber(String token, int tacticNum) {
		return N_VARIABLE.matcher(token).replaceAll(String.valueOf(tacticNum));
	}

	/**
	 * Builds the deterministic object id for a duplicated breakdown slide, unique per
	 * {@code (breakdown type, tactic)} pair, e.g. {@code bd_tp_3}.
	 *
	 * @param type      the breakdown section
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	String breakdownSlideId(BreakdownType type, int tacticNum) {
		return breakdownSlideNaming.slideId(type, tacticNum);
	}

	/**
	 * Resolves the configured {@code code → master slide object id} map into a typed map keyed by
	 * {@link BreakdownType}, dropping unknown codes and blank ids so an unconfigured or partially
	 * configured deck degrades to a safe no-op.
	 *
	 * @param configured the raw wire-code → object-id map from configuration (may be null/empty)
	 * @return the typed master ids, keyed by breakdown type
	 */
	Map<BreakdownType, String> resolveBreakdownMasterIds(Map<String, String> configured) {
		Map<BreakdownType, String> resolved = new EnumMap<>(BreakdownType.class);
		if (configured == null) {
			return resolved;
		}
		configured.forEach((code, id) -> {
			if (id != null && !id.isBlank() && code != null && !code.isBlank()) {
				BreakdownType type = BreakdownType.BY_CODE.get(code.trim().toLowerCase());
				if (type != null) {
					resolved.put(type, id.trim());
				}
			}
		});
		return resolved;
	}

	/**
	 * Builds a Drive client authenticated as the signed-in user via their
	 * short-lived Google OAuth access token (sourced from Clerk). The template
	 * must be readable by that user for the copy to succeed.
	 */
	Drive buildDrive(String accessToken) {

		return new Drive.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	Slides buildSlides(String accessToken) {

		return new Slides.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	HttpRequestInitializer userInitializer(String accessToken) {

		return creds.withTimeout(new HttpCredentialsAdapter(
				GoogleCredentials.create(new AccessToken(accessToken, null))));
	}
}
