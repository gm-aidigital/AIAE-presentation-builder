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
import com.google.api.services.slides.v1.model.DuplicateObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
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
import java.util.Locale;
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
	 * Field mask for the {@code presentations.get} used by {@link #addBreakdownSlides} and
	 * {@link #addTacticSlides}: only the slide order ({@code objectId}) and the text needed to discover
	 * {@code {{…}}} tokens on the master slides (shape text and table-cell text). Keeping the mask tight
	 * avoids pulling the whole deck back.
	 */
	private static final String BREAKDOWN_FIELDS =
			"slides.objectId,"
			+ "slides.pageElements.shape.text.textElements.textRun.content,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #surplusRowRequests}: the summary table's
	 * cell text, plus — and this is the part that matters — each page element's own {@code objectId}. A field
	 * mask returns nothing it does not name, so reading the table text without the element ids gives back
	 * elements with a null id, the configured table is never recognized among them, and the row trim silently
	 * skips every deck: that is exactly how a two-tactic deck shipped with five raw {@code {{tactic N}}} rows.
	 */
	private static final String TABLE_TRIM_FIELDS =
			"slides.pageElements.objectId,"
			+ "slides.pageElements.table.tableRows.tableCells.text.textElements.textRun.content";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #deleteMasterSlides}: only the slide
	 * order ({@code objectId}) is needed to check which configured masters are present in the deck.
	 */
	private static final String MASTER_FIELDS = "slides.objectId";

	/**
	 * Field mask for the {@code presentations.get} used by {@link #deleteReportTypeSlides}: the slide order
	 * ({@code objectId}) plus the shape and table text the configured titles are matched against.
	 */
	private static final String TITLE_FIELDS = BREAKDOWN_FIELDS;

	/** Prefix the Slides editor puts before a slide's object id in its {@code #slide=id.…} URL fragment. */
	private static final String SLIDE_URL_ID_PREFIX = "id.";

	/** Everything that is not a letter or a digit, stripped before matching a slide title. */
	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

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
	private final EomDeckPolicy eomDeckPolicy;
	private final EomSlideFinder eomSlideFinder;
	private final String targetFolderId;
	private final Map<Integer, String> summaryTableObjectIds;
	private final Map<Integer, String> summarySlideObjectIds;
	private final Map<Integer, String> resultsSlideObjectIds;
	private final Map<Integer, String> tacticSlideObjectIds;
	private final Map<BreakdownType, String> breakdownMasterIds;
	private final Set<String> eomDropSlideObjectIds;
	private final List<String> eomDropSlideTitles;
	private final String thoughtsMasterId;
	private final String tacticMasterId;
	private final BreakdownSlideNaming breakdownSlideNaming;
	private final BreakdownThoughtsGate thoughtsGate;
	private final SummaryTableRowTrimmer summaryTableRowTrimmer;

	public RealSlidesProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, DriveSharer driveSharer,
			DriveShareRecipients shareRecipients, GoogleRequestRetrier retrier,
			BreakdownSlideNaming breakdownSlideNaming, BreakdownThoughtsGate thoughtsGate,
			SummaryTableRowTrimmer summaryTableRowTrimmer, EomDeckPolicy eomDeckPolicy,
			EomSlideFinder eomSlideFinder) {
		String slidesTemplateId = props.getSlidesTemplateId();
		this.eomDeckPolicy = eomDeckPolicy;
		this.eomSlideFinder = eomSlideFinder;
		String targetFolderId = props.getSlidesTargetFolderId();
		this.summaryTableObjectIds = props.getSummaryTableObjectIds();
		this.summarySlideObjectIds = props.getSummarySlideObjectIds();
		this.resultsSlideObjectIds = props.getResultsSlideObjectIds();
		this.tacticSlideObjectIds = props.getTacticSlideObjectIds();
		this.breakdownMasterIds = resolveBreakdownMasterIds(props.getBreakdownMasterSlideObjectIds());
		this.eomDropSlideObjectIds = normalizeSlideIds(eomDeckPolicy.dropSlideObjectIds());
		this.eomDropSlideTitles = normalizeTitles(eomDeckPolicy.dropSlideTitles());
		String thoughtsMaster = props.getThoughtsMasterSlideObjectId();
		this.thoughtsMasterId = thoughtsMaster == null ? "" : thoughtsMaster.trim();
		this.tacticMasterId = normalizeSlideId(props.getTacticMasterSlideObjectId());
		this.breakdownSlideNaming = breakdownSlideNaming;
		this.thoughtsGate = thoughtsGate;
		this.summaryTableRowTrimmer = summaryTableRowTrimmer;
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
		this.targetFolderId = targetFolderId == null ? "" : targetFolderId.trim();
		// Which template this instance will actually clone, and under which slide model. Logged at startup
		// because the answer is otherwise invisible: a deck built from the wrong template looks like a code
		// problem, while it is nearly always an environment variable that never reached the process (set but
		// not restarted, or set on the other environment). One line here settles that in a glance.
		log.info("[slides] template={} eomTemplate={} tacticModel={} tacticMaster={} breakdownMasters={}",
				this.slidesTemplateId,
				eomDeckPolicy.describeTemplate(),
				masterTacticModel() ? "master" : "legacy-28-slots",
				masterTacticModel() ? tacticMasterId : "(unset)",
				breakdownMasterIds.size());
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
		return eomDeckPolicy.templateIdOr(reportType, slidesTemplateId);
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
	public void trimTactics(
			String presentationId, int tacticCount, String reportType, String userGoogleAccessToken) {
		if (eomDeck(reportType)) {
			trimEomDashboardSlides(presentationId, tacticCount, userGoogleAccessToken);
			return;
		}
		if (tacticCount >= MAX_TACTICS) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			List<Request> requests = trimRequests(tacticCount);
			// The last partial group's surplus table rows are located by reading the deck rather than by
			// assuming fixed row indices: how many header rows the template keeps inside the table differs
			// per template, and guessing wrong deletes the Totals row instead of a tactic row.
			requests.addAll(surplusRowRequests(slidesClient, presentationId, tacticCount));
			if (requests.isEmpty()) {
				return;
			}
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
	 * Builds the whole-object delete requests for a deck trimmed to {@code tacticCount} tactics: the surplus
	 * per-tactic detail slides and the surplus "Our results" and summary group slides. Tactics are grouped
	 * 7‑per‑group (group 1 → tactics 1–7, group 2 → 8–14, …), so {@code groups = ceil(tacticCount / 7)} and
	 * every group above that is empty and deleted whole. The last, partial group's unused table rows are not
	 * handled here — they need the live table, see {@link #surplusRowRequests}. Requests are emitted only for
	 * configured (non-blank) object ids, so an unconfigured deck degrades to a safe no-op.
	 *
	 * @param tacticCount number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @return the ordered list of delete requests (empty when nothing is configured to trim)
	 */
	List<Request> trimRequests(int tacticCount) {
		int groups = (tacticCount + TACTICS_PER_GROUP - 1) / TACTICS_PER_GROUP;

		List<Request> requests = new ArrayList<>();
		// Surplus per-tactic detail slides — legacy model only: under the master model the deck is built with
		// exactly as many tactic slides as there are tactics, so there is nothing to delete.
		if (!masterTacticModel()) {
			for (int t = tacticCount + 1; t <= MAX_TACTICS; t++) {
				addDeleteObject(requests, tacticSlideObjectIds.get(t));
			}
		}
		// Surplus "Our results" and summary slides for empty groups.
		for (int g = groups + 1; g <= GROUP_COUNT; g++) {
			addDeleteObject(requests, resultsSlideObjectIds.get(g));
			addDeleteObject(requests, summarySlideObjectIds.get(g));
		}
		return requests;
	}

	/**
	 * Builds the {@code deleteTableRow} requests for the unused tactic rows of the last, partial group's
	 * summary table. Needs the live deck: the rows are located by reading the table (Totals row last, the
	 * seven tactic rows directly above it) instead of by fixed indices, because a template may or may not
	 * keep its header row inside the table — an assumption that, when wrong, deleted the Totals row and left
	 * a raw {@code {{tactic N}}} row behind.
	 *
	 * <p>Non-fatal by design: a failed read yields no row requests, so the slide deletes still go through and
	 * the deck ships with an untrimmed table rather than not at all.
	 *
	 * @param slidesClient   the authenticated Slides client
	 * @param presentationId the deck being trimmed
	 * @param tacticCount    number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @return the row delete requests, bottom-up; empty when there is nothing (or nothing safe) to delete
	 */
	List<Request> surplusRowRequests(Slides slidesClient, String presentationId, int tacticCount) {
		int groups = (tacticCount + TACTICS_PER_GROUP - 1) / TACTICS_PER_GROUP;
		int usedInLastGroup = tacticCount - (groups - 1) * TACTICS_PER_GROUP;
		String lastTableId = summaryTableObjectIds.get(groups);
		if (usedInLastGroup >= TACTICS_PER_GROUP || lastTableId == null || lastTableId.isBlank()) {
			return new ArrayList<>();
		}
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(TABLE_TRIM_FIELDS),
					"trimTactics get " + presentationId);
			return summaryTableRowTrimmer.deleteRowRequests(
					deck.getSlides(), lastTableId, TACTICS_PER_GROUP, usedInLastGroup);
		} catch (IOException ex) {
			log.warn("[slides] trimTactics: reading {} for the summary-table trim failed ({}) - "
					+ "leaving the table untrimmed", presentationId, ex.getMessage());
			return new ArrayList<>();
		}
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
	public void addTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken) {
		if (eomDeck(reportType)) {
			addEomTacticSlides(presentationId, tacticCount, placeholderMap, userGoogleAccessToken);
			return;
		}
		if (!masterTacticModel()) {
			return;
		}
		Map<String, String> values = placeholderMap == null ? Map.of() : placeholderMap;
		int count = Math.clamp(tacticCount, 1, MAX_TACTICS);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addTacticSlides get " + presentationId);
			List<Request> requests = buildTacticRequests(deck.getSlides(), count, values);
			// A configured master that is not in the deck means the deck will ship with no per-tactic slides at
			// all — every tactic loses its main slide, and its breakdowns lose the anchor they are placed after.
			// Raised rather than returned quietly so the caller can turn it into a job warning: silence here is
			// what made a whole run come back without tactic slides and still look successful.
			if (requests.isEmpty()) {
				throw new AppException(ErrorReason.C000,
						"master tactic slide " + tacticMasterId + " was not found in deck " + presentationId
								+ "; no per-tactic slides were built");
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addTacticSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addTacticSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides addTacticSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests that turn the single master tactic slide into one slide per
	 * active tactic: duplicate the master {@code tacticCount} times under the deterministic copy ids
	 * {@link BreakdownSlideNaming#tacticSlideId(int)}, write each copy's {@code n} tokens with that tactic's
	 * value (scoped to the copy), then move the whole run of copies into the master's own position so the
	 * tactic block lands exactly where the template drew it. The duplicates are emitted last tactic first,
	 * which is what leaves them in ascending order in the deck — the order the move request requires.
	 *
	 * <p>Requests are emitted in two ordered phases — all duplicates + token writes, then the single position
	 * move — so no request references a slide an earlier one has not created yet. The master itself is left in
	 * place for {@link #deleteMasterSlides}; the copies sit before it, so deleting it afterwards leaves the
	 * block where the move put it.
	 *
	 * @param slides         the deck's slides in order (from {@code presentations.get}), carrying the master's
	 *                       text for token discovery
	 * @param tacticCount    number of real tactics (already clamped to {@code [1, 28]} by the caller)
	 * @param placeholderMap resolved token → value pairs; a renumbered token absent from the map is only
	 *                       renumbered
	 * @return the ordered batchUpdate requests, or an empty list when the master is absent from the deck
	 */
	List<Request> buildTacticRequests(List<Page> slides, int tacticCount, Map<String, String> placeholderMap) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		int masterIndex = -1;
		Page master = null;
		for (int i = 0; i < slides.size(); i++) {
			if (tacticMasterId.equals(slides.get(i).getObjectId())) {
				masterIndex = i;
				master = slides.get(i);
				break;
			}
		}
		if (master == null) {
			log.warn("[slides] addTacticSlides: master tactic slide {} not in deck — skipping", tacticMasterId);
			return requests;
		}

		// Phase 1: one copy per tactic, each with its own values written into the copy's tokens. The deck's
		// global placeholder pass has already run and will not run again, so a token left merely renumbered
		// here would stay raw in the delivered deck — hence the values map, not a plain renumber.
		//
		// Duplicated from the last tactic down to the first, which is what leaves the copies in ascending
		// tactic order in the deck: every duplicate lands immediately after the master, pushing the previous
		// one further down. Duplicating 1..N instead leaves them reversed (N first), and the position move
		// below then fails the whole batch with "The slides should be in presentation order, with no
		// duplicates" — a 400 that shipped decks with no tactic slides at all.
		Set<String> tokens = extractRenumberableTokens(master);
		for (int n = tacticCount; n >= 1; n--) {
			String copyId = breakdownSlideNaming.tacticSlideId(n);
			requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
					.setObjectId(tacticMasterId)
					.setObjectIds(Map.of(tacticMasterId, copyId))));
			emitRenumberedTokens(requests, copyId, n, tokens, placeholderMap);
		}

		// Phase 2: the copies now sit directly after the master in ascending tactic order — the order this
		// list must be in — so moving them to the master's index puts the block exactly where the template
		// had it, master last.
		List<String> copyIds = new ArrayList<>(tacticCount);
		for (int n = 1; n <= tacticCount; n++) {
			copyIds.add(breakdownSlideNaming.tacticSlideId(n));
		}
		requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
				.setSlideObjectIds(copyIds)
				.setInsertionIndex(masterIndex)));
		return requests;
	}

	/**
	 * Whether the deck is built on the master tactic-slide model (one generic slide duplicated per tactic)
	 * rather than the legacy model of 28 drawn tactic slots that are trimmed down.
	 *
	 * @return {@code true} when a master tactic slide is configured
	 */
	boolean masterTacticModel() {
		return !tacticMasterId.isBlank();
	}

	/**
	 * Resolves the object id of a tactic's main slide in the deck: the deterministic id of the master's copy
	 * under the master model, or the configured per-slot template id under the legacy model. The breakdown
	 * step anchors each tactic's copies after this slide.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the main slide object id, or {@code null} when the legacy model has no slot configured
	 */
	String mainTacticSlideId(int tacticNum) {
		return masterTacticModel()
				? breakdownSlideNaming.tacticSlideId(tacticNum)
				: tacticSlideObjectIds.get(tacticNum);
	}

	@Override
	public void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String reportType, String userGoogleAccessToken) {
		if (eomDeck(reportType)) {
			addEomBreakdownSlides(presentationId, enabledByTactic, breakdownValues, userGoogleAccessToken);
			return;
		}
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
		return buildBreakdownRequests(slides, masterIds, enabledByTactic, breakdownValues, Map.of());
	}

	/**
	 * Same as {@link #buildBreakdownRequests(List, Map, Map, Map)}, with the anchor slide each tactic's
	 * copies are placed after supplied explicitly. An EOM deck builds two master slides per tactic, so its
	 * anchor is the second of them rather than the single slide {@link #mainTacticSlideId(int)} names.
	 *
	 * @param slides            the deck's slides in order (from {@code presentations.get})
	 * @param masterIds         master slide object id per breakdown type
	 * @param enabledByTactic   1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues   renumbered token → final value; a token absent from the map is only renumbered
	 * @param anchorSlideByTactic 1-based tactic number → the slide its copies follow; empty falls back to
	 *                          {@link #mainTacticSlideId(int)}
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildBreakdownRequests(
			List<Page> slides, Map<BreakdownType, String> masterIds,
			Map<Integer, Set<BreakdownType>> enabledByTactic, Map<String, String> breakdownValues,
			Map<Integer, String> anchorSlideByTactic) {
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
			String mainSlideId = anchorSlideByTactic.getOrDefault(tacticNum, mainTacticSlideId(tacticNum));
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
			// Keyed by the master's own index in the deck, because a duplicate lands immediately after its
			// master: the copies' order in the deck is their masters' order, whatever order they were created
			// in. The position move below rejects a list that is not in deck order (400 "The slides should be
			// in presentation order"), so the list is derived from the masters' positions rather than from the
			// BreakdownType declaration order — which only happens to match the template today.
			TreeMap<Integer, String> copiesByMasterIndex = new TreeMap<>();
			for (BreakdownType type : entry.getValue()) {
				String masterId = masterIds.get(type);
				String copyId = breakdownSlideId(type, tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(masterId)
						.setObjectIds(Map.of(masterId, copyId))));
				Set<String> tokens = tokensByType.computeIfAbsent(
						type, t -> extractRenumberableTokens(pageById.get(masterId)));
				emitRenumberedTokens(requests, copyId, tacticNum, tokens, breakdownValues);
				copiesByMasterIndex.put(indexById.get(masterId), copyId);
			}
			// The tactic's thoughts copy: its master sits after every breakdown master in the template, so
			// keying it the same way lands it right after the tactic's final breakdown slide.
			if (thoughtsEnabled && thoughtsGate.qualifies(enabledByTactic.get(tacticNum))) {
				String thoughtsCopyId = breakdownSlideNaming.thoughtsSlideId(tacticNum);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(thoughtsMasterId)
						.setObjectIds(Map.of(thoughtsMasterId, thoughtsCopyId))));
				emitRenumberedTokens(requests, thoughtsCopyId, tacticNum, thoughtsTokens, breakdownValues);
				copiesByMasterIndex.put(indexById.get(thoughtsMasterId), thoughtsCopyId);
			}
			copyIdsByTactic.put(tacticNum, new ArrayList<>(copiesByMasterIndex.values()));
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
	public int countSlides(String presentationId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			// Object ids only: the count is all that is wanted and pulling page elements would ship the
			// whole deck's content back for a single integer.
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(MASTER_FIELDS),
					"countSlides get " + presentationId);
			return deck.getSlides() == null ? 0 : deck.getSlides().size();
		} catch (IOException ex) {
			// Measuring a finished deck is bookkeeping, not delivery: a report that shipped must not be
			// reported as failed because its slide count could not be read.
			log.warn("[slides] countSlides failed for {}: {}", presentationId, ex.getMessage());
			return 0;
		}
	}


	/**
	 * Whether this deck build follows the EOM model: an EOM report whose own template is switched on. The
	 * EOC path is left untouched for every other case, which is what keeps an EOM change off the EOC deck.
	 *
	 * @param reportType report template code ({@code "EOC"}/{@code "EOM"}), may be {@code null}
	 * @return {@code true} when the EOM slide model applies
	 */
	boolean eomDeck(String reportType) {
		return eomDeckPolicy.appliesTo(reportType) && eomDeckPolicy.hasOwnTemplate();
	}

	/**
	 * Deterministic object id of a tactic's copy of one EOM master slide. An EOM tactic gets one copy per
	 * master (the EOC-style tactic slide and the EOM channel slide), so the id carries both the master's
	 * ordinal in the template and the tactic number.
	 *
	 * @param masterOrdinal 0-based position of the master among the deck's tactic masters
	 * @param tacticNum     the 1-based tactic number
	 * @return the copy's object id
	 */
	String eomTacticSlideId(int masterOrdinal, int tacticNum) {
		return "eom_m" + masterOrdinal + "_t" + tacticNum;
	}

	/**
	 * Deletes the EOM dashboard slides whose tactic slots the campaign never fills. The pacing-dashboard
	 * and performance-vs-plan slides are each drawn for a fixed block of seven tactics, so a three-tactic
	 * campaign leaves six of them showing raw {@code {{tactic 8 …}}} tokens. Nothing else is trimmed: an
	 * EOM deck builds its tactic slides from masters, so it has no surplus drawn slides to remove.
	 *
	 * @param presentationId        the deck to trim
	 * @param tacticCount           number of real tactics in the campaign
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void trimEomDashboardSlides(String presentationId, int tacticCount, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"trimEomDashboardSlides get " + presentationId);
			List<String> surplus = eomSlideFinder.surplusTacticSlideIds(deck.getSlides(), tacticCount);
			if (surplus.isEmpty()) {
				return;
			}
			List<Request> requests = new ArrayList<>();
			for (String objectId : surplus) {
				addDeleteObject(requests, objectId);
			}
			log.info("[slides] EOM trim: dropping {} dashboard slide(s) above tactic {}", surplus.size(),
					tacticCount);
			executeInChunks(slidesClient, presentationId, requests,
					"trimEomDashboardSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] trimEomDashboardSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides trimEomDashboardSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds an EOM deck's per-tactic slides: every master tactic slide found in the deck is duplicated
	 * once per tactic, and each tactic's copies are placed together, in the masters' own template order.
	 * The EOM template carries two such masters, so tactic 3 ends up as the pair
	 * {@code (EOC-style slide, channel slide)} — and its breakdown slides follow the pair, never split it.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param tacticCount           number of real tactics
	 * @param placeholderMap        resolved token → value pairs
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void addEomTacticSlides(
			String presentationId, int tacticCount, Map<String, String> placeholderMap,
			String userGoogleAccessToken) {
		Map<String, String> values = placeholderMap == null ? Map.of() : placeholderMap;
		int count = Math.clamp(tacticCount, 1, MAX_TACTICS);
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addEomTacticSlides get " + presentationId);
			List<Request> requests = buildEomTacticRequests(deck.getSlides(), count, values);
			// Same reasoning as the EOC master model: a deck with no tactic slides at all must be reported,
			// not returned quietly as a success.
			if (requests.isEmpty()) {
				throw new AppException(ErrorReason.C000,
						"no EOM master tactic slide found in deck " + presentationId
								+ "; no per-tactic slides were built");
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addEomTacticSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addEomTacticSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides addEomTacticSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the ordered batchUpdate requests behind {@link #addEomTacticSlides}: duplicate every master
	 * once per tactic (last tactic first, so the copies end up in ascending order behind their master),
	 * write each copy's {@code n} tokens with that tactic's values scoped to the copy, then move each
	 * tactic's copies as one block to where the first master sits.
	 *
	 * <p>The move list for a tactic is {@code (master 0's copy, master 1's copy, …)}, which is also their
	 * current order in the deck — every copy of master 0 precedes master 1 and therefore all of its copies
	 * — because {@code updateSlidesPosition} rejects a list that is not in presentation order. Blocks are
	 * moved in ascending tactic order while accumulating how many copies were already inserted, the same
	 * arithmetic the breakdown pass uses.
	 *
	 * @param slides         the deck's slides in order, from {@code presentations.get}
	 * @param tacticCount    number of real tactics (already clamped by the caller)
	 * @param placeholderMap resolved token → value pairs; a renumbered token absent from the map is only
	 *                       renumbered
	 * @return the ordered batchUpdate requests, or an empty list when the deck carries no master
	 */
	List<Request> buildEomTacticRequests(List<Page> slides, int tacticCount, Map<String, String> placeholderMap) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		List<String> masterIds = eomSlideFinder.tacticMasterSlideIds(slides);
		if (masterIds.isEmpty()) {
			log.warn("[slides] addEomTacticSlides: no master tactic slide in deck — skipping");
			return requests;
		}
		Map<String, Page> pageById = new HashMap<>();
		int firstMasterIndex = -1;
		for (int i = 0; i < slides.size(); i++) {
			Page page = slides.get(i);
			if (page.getObjectId() == null) {
				continue;
			}
			pageById.put(page.getObjectId(), page);
			if (firstMasterIndex < 0 && masterIds.contains(page.getObjectId())) {
				firstMasterIndex = i;
			}
		}

		// Phase 1: duplicate + fill. Per master, from the last tactic down to the first, so the copies sit
		// in ascending tactic order behind their master (the order the position move below requires).
		for (int ordinal = 0; ordinal < masterIds.size(); ordinal++) {
			String masterId = masterIds.get(ordinal);
			Set<String> tokens = extractRenumberableTokens(pageById.get(masterId));
			for (int n = tacticCount; n >= 1; n--) {
				String copyId = eomTacticSlideId(ordinal, n);
				requests.add(new Request().setDuplicateObject(new DuplicateObjectRequest()
						.setObjectId(masterId)
						.setObjectIds(Map.of(masterId, copyId))));
				emitRenumberedTokens(requests, copyId, n, tokens, placeholderMap);
			}
		}

		// Phase 2: each tactic's copies move together to the first master's position, tactic 1 first.
		int inserted = 0;
		for (int n = 1; n <= tacticCount; n++) {
			List<String> block = new ArrayList<>(masterIds.size());
			for (int ordinal = 0; ordinal < masterIds.size(); ordinal++) {
				block.add(eomTacticSlideId(ordinal, n));
			}
			requests.add(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
					.setSlideObjectIds(block)
					.setInsertionIndex(firstMasterIndex + inserted)));
			inserted += block.size();
		}
		return requests;
	}

	/**
	 * Inserts an EOM deck's breakdown slides. Masters are found by the tokens they carry rather than by
	 * configured id, and each tactic's copies are anchored after the LAST of that tactic's master copies,
	 * so a tactic reads as one block: its two channel slides first, then its breakdowns.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues       renumbered token → value to write
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void addEomBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String userGoogleAccessToken) {
		if (enabledByTactic == null || enabledByTactic.isEmpty()) {
			return;
		}
		Map<String, String> values = breakdownValues == null ? Map.of() : breakdownValues;
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"addEomBreakdownSlides get " + presentationId);
			List<Request> requests = buildEomBreakdownRequests(deck.getSlides(), enabledByTactic, values);
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"addEomBreakdownSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] addEomBreakdownSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides addEomBreakdownSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the EOM breakdown requests: the shared builder, given the deck's discovered breakdown masters
	 * and an anchor per tactic pointing at that tactic's last master copy.
	 *
	 * @param slides          the deck's slides in order, from {@code presentations.get}
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues renumbered token → value to write
	 * @return the ordered batchUpdate requests, or an empty list when there is nothing to insert
	 */
	List<Request> buildEomBreakdownRequests(
			List<Page> slides, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues) {
		Map<BreakdownType, String> masterIds = eomSlideFinder.breakdownMasterSlideIds(slides);
		if (masterIds.isEmpty()) {
			return new ArrayList<>();
		}
		int lastOrdinal = Math.max(eomSlideFinder.tacticMasterSlideIds(slides).size() - 1, 0);
		Map<Integer, String> anchors = new LinkedHashMap<>();
		for (Integer tacticNum : enabledByTactic.keySet()) {
			if (tacticNum != null) {
				anchors.put(tacticNum, eomTacticSlideId(lastOrdinal, tacticNum));
			}
		}
		return buildBreakdownRequests(slides, masterIds, enabledByTactic, breakdownValues, anchors);
	}

	/**
	 * Removes an EOM deck's master slides — both tactic masters and every breakdown master — after the
	 * copies have been made. They are found by their tokens, so a redrawn template needs no config change.
	 *
	 * @param presentationId        the already-built deck to clean
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token
	 */
	void deleteEomMasterSlides(String presentationId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(BREAKDOWN_FIELDS),
					"deleteEomMasterSlides get " + presentationId);
			List<Request> requests = buildEomMasterDeleteRequests(deck.getSlides());
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"deleteEomMasterSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteEomMasterSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides deleteEomMasterSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the delete requests for every EOM master slide present in the deck: the tactic masters and
	 * the breakdown masters. Only slides still carrying the tactic variable {@code n} are matched, so the
	 * copies — whose tokens were renumbered — are never at risk.
	 *
	 * @param slides the deck's slides in order, from {@code presentations.get}
	 * @return the delete requests, or an empty list when no master is left in the deck
	 */
	List<Request> buildEomMasterDeleteRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		Set<String> masters = new LinkedHashSet<>(eomSlideFinder.tacticMasterSlideIds(slides));
		masters.addAll(eomSlideFinder.breakdownMasterSlideIds(slides).values());
		for (String objectId : masters) {
			addDeleteObject(requests, objectId);
		}
		return requests;
	}

	@Override
	public void deleteMasterSlides(String presentationId, String reportType, String userGoogleAccessToken) {
		if (eomDeck(reportType)) {
			deleteEomMasterSlides(presentationId, userGoogleAccessToken);
			return;
		}
		if (breakdownMasterIds.isEmpty() && thoughtsMasterId.isBlank() && tacticMasterId.isBlank()) {
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

	@Override
	public void deleteReportTypeSlides(String presentationId, String reportType, String userGoogleAccessToken) {
		if (!eomDeckPolicy.appliesTo(reportType)
				|| (eomDropSlideObjectIds.isEmpty() && eomDropSlideTitles.isEmpty())) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
		try {
			Presentation deck = retrier.execute(
					slidesClient.presentations().get(presentationId).setFields(TITLE_FIELDS),
					"deleteReportTypeSlides get " + presentationId);
			List<Request> requests = buildTitleDeleteRequests(deck.getSlides());
			if (requests.isEmpty()) {
				log.warn("[slides] deleteReportTypeSlides matched none of the {} configured id(s) / {} title(s) in {}",
						eomDropSlideObjectIds.size(), eomDropSlideTitles.size(), presentationId);
				return;
			}
			executeInChunks(slidesClient, presentationId, requests,
					"deleteReportTypeSlides batchUpdate for " + presentationId);
		} catch (IOException ex) {
			log.error("[slides] deleteReportTypeSlides failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides deleteReportTypeSlides failed: " + ex.getMessage());
		}
	}

	/**
	 * Builds the {@code DeleteObject} requests for the slides an EOM deck must drop. A slide is deleted when
	 * its object id is one of the configured ids — the reliable route, since object ids survive the Drive copy
	 * that produced the deck — or, for slides not covered by an id, when its text contains one of the
	 * configured titles. Both the slide text and the configured title are reduced to their letters and digits
	 * before matching, so casing, spacing and punctuation in the template cannot break the fallback. Ids
	 * configured but absent from the deck are skipped rather than failing the batch.
	 *
	 * @param slides the deck's slides in order (from {@code presentations.get} with {@link #TITLE_FIELDS})
	 * @return the delete requests in slide order, or an empty list when no slide matched
	 */
	List<Request> buildTitleDeleteRequests(List<Page> slides) {
		List<Request> requests = new ArrayList<>();
		if (slides == null || slides.isEmpty()) {
			return requests;
		}
		for (Page page : slides) {
			String objectId = page.getObjectId();
			if (objectId == null) {
				continue;
			}
			if (eomDropSlideObjectIds.contains(objectId)) {
				requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
				continue;
			}
			String text = normalizeTitle(slideText(page));
			if (text.isEmpty()) {
				continue;
			}
			for (String title : eomDropSlideTitles) {
				if (text.contains(title)) {
					requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(objectId)));
					break;
				}
			}
		}
		return requests;
	}

	/**
	 * Concatenates every shape and table-cell text run on a slide into one string, so a title split across
	 * runs (Slides splits on any formatting change) still matches as one phrase.
	 *
	 * @param page the slide to read (may carry no page elements)
	 * @return the slide's text, or an empty string when it carries none
	 */
	String slideText(Page page) {
		StringBuilder joined = new StringBuilder();
		if (page.getPageElements() == null) {
			return "";
		}
		for (PageElement element : page.getPageElements()) {
			if (element.getShape() != null) {
				appendText(element.getShape().getText(), joined);
			}
			Table table = element.getTable();
			if (table != null && table.getTableRows() != null) {
				for (TableRow row : table.getTableRows()) {
					if (row.getTableCells() == null) {
						continue;
					}
					for (TableCell cell : row.getTableCells()) {
						appendText(cell.getText(), joined);
					}
				}
			}
		}
		return joined.toString();
	}

	/**
	 * Appends the text runs of one text container (a shape or a table cell) to the accumulator.
	 *
	 * @param text   the text container (may be {@code null})
	 * @param joined the accumulating slide text
	 */
	void appendText(TextContent text, StringBuilder joined) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
	}

	/**
	 * Normalizes the configured drop slide ids: blank entries are dropped and the {@code id.} prefix the
	 * Slides editor puts in its {@code #slide=id.…} URL fragment is stripped, so an id pasted straight from
	 * the address bar matches the object id the API reports.
	 *
	 * @param configured the raw configured slide object ids (may be null/empty)
	 * @return the normalized, non-blank object ids
	 */
	Set<String> normalizeSlideIds(List<String> configured) {
		Set<String> normalized = new LinkedHashSet<>();
		if (configured == null) {
			return normalized;
		}
		for (String id : configured) {
			String candidate = normalizeSlideId(id);
			if (!candidate.isEmpty()) {
				normalized.add(candidate);
			}
		}
		return normalized;
	}

	/**
	 * Normalizes one configured slide object id: trims it, treats null as blank, and strips the {@code id.}
	 * prefix the Slides editor puts in its {@code #slide=id.…} URL fragment — so an id pasted straight from
	 * the address bar matches the object id the API reports.
	 *
	 * @param raw the configured slide object id (may be {@code null})
	 * @return the normalized object id, or an empty string when unconfigured
	 */
	String normalizeSlideId(String raw) {
		if (raw == null) {
			return "";
		}
		String candidate = raw.trim();
		if (candidate.startsWith(SLIDE_URL_ID_PREFIX)) {
			candidate = candidate.substring(SLIDE_URL_ID_PREFIX.length());
		}
		return candidate;
	}

	/**
	 * Normalizes the configured drop titles for matching, dropping blank entries.
	 *
	 * @param configured the raw configured title phrases (may be null/empty)
	 * @return the normalized, non-blank titles
	 */
	List<String> normalizeTitles(List<String> configured) {
		List<String> normalized = new ArrayList<>();
		if (configured == null) {
			return normalized;
		}
		for (String title : configured) {
			String candidate = normalizeTitle(title);
			if (!candidate.isEmpty()) {
				normalized.add(candidate);
			}
		}
		return normalized;
	}

	/**
	 * Reduces a title (or a slide's text) to its upper-case letters and digits, so casing, spacing and
	 * punctuation differences between the configured phrase and the template cannot break the match.
	 *
	 * @param value the raw text (may be {@code null})
	 * @return the normalized text, or an empty string when there is nothing to match
	 */
	String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		return NON_ALPHANUMERIC.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/**
	 * Builds the {@code DeleteObject} requests that remove every configured master slide actually present in
	 * the deck: the breakdown masters, the thoughts master and the main tactic master. Presence is checked
	 * against the deck's slide object ids so a master configured but absent from this template variant (or
	 * already deleted) is skipped rather than failing the batch. Breakdown masters are de-duplicated and
	 * emitted first, then the thoughts master, then the tactic master.
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
		// The master tactic slide goes the same way: its copies carry the real tactics, so the master itself
		// would ship as a slide full of raw {{tactic n …}} tokens. Deleted here rather than in
		// addTacticSlides so it is cleaned even when that step was skipped or failed.
		if (!tacticMasterId.isBlank() && presentIds.contains(tacticMasterId)) {
			requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(tacticMasterId)));
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
