package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;

import java.util.Map;
import java.util.Set;

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

	/**
	 * @return true when the provider is talking to the real Google APIs.
	 */
	boolean isLive();

	/**
	 * @param jobId                 orchestration job id used as a correlation suffix
	 * @param fileName              Drive file name to give the cloned deck
	 * @param placeholderMap        resolved {@code {token}} → value pairs to write
	 *                              into the cloned deck
	 * @param userGoogleAccessToken optional Google OAuth access token for the
	 *                              signed-in user (obtained from Clerk). When
	 *                              non-blank the deck is created in that user's
	 *                              personal Drive; when null/blank the provider
	 *                              falls back to the service account.
	 * @return public Slides URL the UI shows in its "Slides ready" card
	 */
	String createDeck(String jobId, String fileName, Map<String, String> placeholderMap, String userGoogleAccessToken);

	/**
	 * Removes the template's unused per-tactic slides, surplus summary + "Our
	 * results" group slides, and the last summary table's unused rows when the
	 * campaign has fewer than the template's 28 tactic slots. Without this, the
	 * surplus slides survive showing raw {@code {{tactic N …}}} tokens and empty
	 * chart frames. A no-op when {@code tacticCount >= 28}.
	 *
	 * @param presentationId        the deck to trim
	 * @param tacticCount           number of real tactics (clamped 1..28)
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token;
	 *                              falls back to the service account when blank
	 */
	void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken);

	/**
	 * Inserts the per-tactic breakdown slides selected on Step 3 into an already-built deck. For each
	 * enabled {@code (tactic, breakdown)} pair the corresponding master slide is duplicated, its generic
	 * {@code n} tokens are renumbered to the tactic number (scoped to the copy so identical master tokens
	 * never overwrite each other across copies), and the copy is positioned immediately after that
	 * tactic's main slide. When a tactic enables several breakdowns they follow the {@link BreakdownType}
	 * declaration order (Top Publishers → Creative → Geo → Audience → Device). The master slides are
	 * deleted from the deck at the end.
	 *
	 * <p>A no-op when no masters are configured, the map is empty, or the deck carries no matching tactic
	 * slides — so an unconfigured deck degrades safely. Value fill is intentionally out of scope here: the
	 * renumbered tokens (e.g. {@code {{publisher_3.1}}}) are filled by the normal placeholder pass.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic, String userGoogleAccessToken);
}
