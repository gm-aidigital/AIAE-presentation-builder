package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.api.services.slides.v1.model.TableCellLocation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real Google Slides + Drive implementation. Clones {@code SLIDES_TEMPLATE_ID}
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

	private final GoogleCredentialsFactory creds;
	private final GoogleRequestRetrier retrier;
	private final DriveSharer driveSharer;
	private final List<String> shareWithEmails;
	private final Slides slides;
	private final Drive drive;
	private final String templateId;
	private final String targetFolderId;
	private final Map<Integer, String> summaryTableObjectIds;
	private final Map<Integer, String> summarySlideObjectIds;
	private final Map<Integer, String> resultsSlideObjectIds;
	private final Map<Integer, String> tacticSlideObjectIds;

	public RealSlidesProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, DriveSharer driveSharer,
			GoogleRequestRetrier retrier) {
		String templateId = props.getSlidesTemplateId();
		String targetFolderId = props.getSlidesTargetFolderId();
		this.summaryTableObjectIds = props.getSummaryTableObjectIds();
		this.summarySlideObjectIds = props.getSummarySlideObjectIds();
		this.resultsSlideObjectIds = props.getResultsSlideObjectIds();
		this.tacticSlideObjectIds = props.getTacticSlideObjectIds();
		this.driveSharer = driveSharer;
		this.retrier = retrier;
		this.shareWithEmails = props.getShareWithEmails();
		this.creds = creds;
		this.slides = new Slides.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.templateId = templateId;
		this.targetFolderId = targetFolderId == null ? "" : targetFolderId.trim();
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String userGoogleAccessToken) {
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
			driveSharer.shareWith(driveClient, newId, shareWithEmails);
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
