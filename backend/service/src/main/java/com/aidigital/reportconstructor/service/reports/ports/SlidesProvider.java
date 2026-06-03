package com.aidigital.reportconstructor.service.reports.ports;

import java.util.Map;

/**
 * Abstraction over Google Slides + Drive deck generation. The real provider
 * clones a template via Drive and runs {@code batchUpdate.replaceAllText};
 * the stub provider fabricates a static template URL for offline demos.
 *
 * <p>Bean selection is automatic: when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is
 * present at startup the real provider wins via {@code @Primary}; otherwise
 * the stub is the only candidate.
 */
public interface SlidesProvider {

    /** @return true when the provider is talking to the real Google APIs. */
    boolean isLive();

    /**
     * @param jobId                 orchestration job id used as a correlation suffix
     * @param placeholderMap        resolved {@code {token}} → value pairs to write
     *                              into the cloned deck
     * @param userGoogleAccessToken optional Google OAuth access token for the
     *                              signed-in user (obtained from Clerk). When
     *                              non-blank the deck is created in that user's
     *                              personal Drive; when null/blank the provider
     *                              falls back to the service account.
     * @return public Slides URL the UI shows in its "Slides ready" card
     */
    String createDeck(String jobId, Map<String, String> placeholderMap, String userGoogleAccessToken);

    /**
     * Removes the template's unused per-tactic slides (and their summary-table
     * rows) when the campaign has fewer than the template's seven tactic slots.
     * Mirrors {@code trimTactics} in {@code tactic_utils.php}: without this the
     * surplus slides survive showing raw {@code {{tactic N …}}} tokens and empty
     * chart frames. A no-op when {@code tacticCount >= 7}.
     *
     * @param presentationId        the deck to trim
     * @param tacticCount           number of real tactics (clamped 1..7)
     * @param userGoogleAccessToken optional signed-in user's Google OAuth token;
     *                              falls back to the service account when blank
     */
    void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken);
}
