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

	/** Tactics per group; the deck carries one summary slide + one "Our results" slide per group. */
	private static final int TACTICS_PER_GROUP = 7;

	/** Number of tactic groups (28 tactics / 7 per group). */
	private static final int GROUP_COUNT = 4;

	private final GoogleCredentialsFactory creds;
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

	public RealSlidesProvider(GoogleCredentialsFactory creds, GoogleProperties props, DriveSharer driveSharer) {
		String templateId = props.getSlidesTemplateId();
		String targetFolderId = props.getSlidesTargetFolderId();
		this.summaryTableObjectIds = props.getSummaryTableObjectIds();
		this.summarySlideObjectIds = props.getSummarySlideObjectIds();
		this.resultsSlideObjectIds = props.getResultsSlideObjectIds();
		this.tacticSlideObjectIds = props.getTacticSlideObjectIds();
		this.driveSharer = driveSharer;
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
			File copied = driveClient.files().copy(templateId, copy)
					.setFields("id,webViewLink")
					.setSupportsAllDrives(true)
					.execute();
			String newId = copied.getId();

			// Grant standing access to the configured recipients (e.g. an admin
			// owner) so decks created in a user's own My Drive remain reachable.
			driveSharer.shareWith(driveClient, newId, shareWithEmails);

			List<Request> requests = new ArrayList<>(placeholderMap.size());
			for (Map.Entry<String, String> e : placeholderMap.entrySet()) {
				// Template tokens are double-brace {{...}} — the key is already the full token.
				String token = e.getKey();
				requests.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
						.setContainsText(new SubstringMatchCriteria().setText(token).setMatchCase(true))
						.setReplaceText(e.getValue() == null ? "" : e.getValue())));
			}
			if (!requests.isEmpty()) {
				slidesClient.presentations()
						.batchUpdate(newId, new BatchUpdatePresentationRequest().setRequests(requests))
						.execute();
			}
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
			slidesClient.presentations()
					.batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
					.execute();
		} catch (IOException ex) {
			log.error("[slides] trimTactics failed for {}", presentationId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Slides trimTactics failed: " + ex.getMessage());
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
