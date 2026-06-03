package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.Slides;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DeleteTableRowRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.api.services.slides.v1.model.TableCellLocation;
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

    private final GoogleCredentialsFactory creds;
    private final Slides slides;
    private final Drive drive;
    private final String templateId;
    private final String targetFolderId;
    private final String summaryTableObjectId;
    private final Map<Integer, String> tacticSlideObjectIds;

    public RealSlidesProvider(GoogleCredentialsFactory creds, GoogleProperties props) {
        String templateId = props.getSlidesTemplateId();
        String targetFolderId = props.getSlidesTargetFolderId();
        this.summaryTableObjectId = props.getSummaryTableObjectId() == null ? "" : props.getSummaryTableObjectId().trim();
        this.tacticSlideObjectIds = props.getTacticSlideObjectIds();
        this.creds = creds;
        this.slides = new Slides.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
            .setApplicationName(APPLICATION_NAME)
            .build();
        this.drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
            .setApplicationName(APPLICATION_NAME)
            .build();
        this.templateId = templateId;
        this.targetFolderId = targetFolderId == null ? "" : targetFolderId.trim();
        log.info("[slides] live Google Slides + Drive clients initialised (template={}, targetFolder={})",
            templateId, this.targetFolderId.isEmpty() ? "<none>" : this.targetFolderId);
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public String createDeck(String jobId, Map<String, String> placeholderMap, String userGoogleAccessToken) {
        boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
        Drive driveClient = asUser ? buildDrive(userGoogleAccessToken) : drive;
        Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;
        log.info("[slides] job {} → creating deck under {}", jobId,
            asUser ? "the signed-in user's Google account" : "the service account");
        try {
            File copy = new File().setName("Report — " + jobId);
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
            log.info("[slides] deck {} created with {} replacements", newId, requests.size());
            return "https://docs.google.com/presentation/d/" + newId + "/edit";
        } catch (IOException ex) {
            log.error("[slides] createDeck failed for job {}", jobId, ex);
            throw new AppException(ErrorReason.C000, "Google Slides deck creation failed");
        }
    }

    @Override
    public void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken) {
        if (tacticCount >= 7) {
            return;
        }
        boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
        Slides slidesClient = asUser ? buildSlides(userGoogleAccessToken) : slides;

        List<Request> requests = new ArrayList<>();
        // Delete summary-table rows bottom-up so earlier indices don't shift.
        for (int t = 7; t >= tacticCount + 1; t--) {
            requests.add(new Request().setDeleteTableRow(new DeleteTableRowRequest()
                .setTableObjectId(summaryTableObjectId)
                .setCellLocation(new TableCellLocation().setRowIndex(t).setColumnIndex(0))));
        }
        // Delete the surplus tactic slides.
        for (int t = 7; t >= tacticCount + 1; t--) {
            String slideId = tacticSlideObjectIds.get(t);
            if (slideId != null) {
                requests.add(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(slideId)));
            }
        }
        if (requests.isEmpty()) {
            return;
        }
        try {
            slidesClient.presentations()
                .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                .execute();
            log.info("[slides] trimmed deck {} to {} tactic(s) ({} delete request(s))",
                presentationId, tacticCount, requests.size());
        } catch (IOException ex) {
            log.error("[slides] trimTactics failed for {}", presentationId, ex);
            throw new AppException(ErrorReason.C000,
                "Google Slides trimTactics failed: " + ex.getMessage());
        }
    }

    /**
     * Builds a Drive client authenticated as the signed-in user via their
     * short-lived Google OAuth access token (sourced from Clerk). The template
     * must be readable by that user for the copy to succeed.
     */
    private Drive buildDrive(String accessToken) {
        return new Drive.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    private Slides buildSlides(String accessToken) {
        return new Slides.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    private static HttpRequestInitializer userInitializer(String accessToken) {
        return new HttpCredentialsAdapter(
            GoogleCredentials.create(new AccessToken(accessToken, null)));
    }
}
