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
	 * @param reportType            report template code ({@code "EOC"}/{@code "EOM"}); selects which
	 *                              template deck is cloned
	 * @param userGoogleAccessToken optional Google OAuth access token for the
	 *                              signed-in user (obtained from Clerk). When
	 *                              non-blank the deck is created in that user's
	 *                              personal Drive; when null/blank the provider
	 *                              falls back to the service account.
	 * @return public Slides URL the UI shows in its "Slides ready" card
	 */
	String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken);

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
	 * declaration order (Top Publishers → Creative → Geo → Audience → Device). The master slides are left
	 * in place; {@link #deleteMasterSlides} removes them in a separate, unconditional pass.
	 *
	 * <p>A no-op when no masters are configured, the map is empty, or the deck carries no matching tactic
	 * slides — so an unconfigured deck degrades safely.
	 *
	 * <p>Values are filled here rather than by the deck's normal placeholder pass, because the copies do
	 * not exist yet when that pass runs: the deck is built (and every token replaced) before this method
	 * duplicates the masters, so a token first spelled {@code {{publisher_3.1}}} on a copy would never be
	 * seen by it and would ship raw. For each master token whose renumbered form has an entry in
	 * {@code breakdownValues}, the token is therefore replaced with its final value directly; tokens with
	 * no entry fall back to a plain renumber.
	 *
	 * @param presentationId        the already-built deck to insert into
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param breakdownValues       renumbered token (e.g. {@code {{publisher_3.1}}}) → value to write;
	 *                              tokens absent from the map are only renumbered
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String userGoogleAccessToken);

	/**
	 * Removes the breakdown master slides (Top Publishers, Creative, Geo, Audience, Device) and the
	 * "Thoughts on tactic performance" master from an already-built deck. These are template slides that
	 * must never ship, regardless of whether any breakdown slides were inserted — so unlike
	 * {@link #addBreakdownSlides} this runs unconditionally, after breakdown insertion has duplicated
	 * whatever it needed from the masters. Only masters that are both configured and present in the deck
	 * are deleted, so a deck without masters (or one already cleaned) degrades to a safe no-op.
	 *
	 * @param presentationId        the already-built deck to clean
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void deleteMasterSlides(String presentationId, String userGoogleAccessToken);
}
