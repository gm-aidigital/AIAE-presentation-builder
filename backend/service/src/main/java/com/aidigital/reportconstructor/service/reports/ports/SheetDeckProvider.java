package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstraction over Google Sheets + Drive workbook generation, mirroring
 * {@link SlidesProvider} for the "Generate Sheet" flow. The real provider clones
 * a Sheets template via Drive and runs {@code batchUpdate.findReplace}; the stub
 * fabricates a static template URL for offline demos.
 *
 * <p>Bean selection is automatic: when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is
 * present at startup the real provider wins via {@code @Primary}; otherwise the
 * stub is the only candidate.
 */
public interface SheetDeckProvider {

	/**
	 * @return true when the provider is talking to the real Google APIs.
	 */
	boolean isLive();

	/**
	 * Clones the Sheets template into a new workbook and replaces every
	 * {@code {{token}}} with its resolved value across all tabs.
	 *
	 * @param jobId                 orchestration job id used as a correlation suffix
	 * @param fileName              Drive file name to give the cloned workbook
	 * @param placeholderMap        resolved {@code {{token}}} → value pairs to write
	 *                              into the cloned workbook
	 * @param userGoogleAccessToken optional Google OAuth access token for the
	 *                              signed-in user (obtained from Clerk). When
	 *                              non-blank the workbook is created in that user's
	 *                              personal Drive; when null/blank the provider
	 *                              falls back to the service account.
	 * @return public Sheets URL the UI shows in its "Sheet ready" card
	 */
	String createSheet(String jobId, String fileName, Map<String, String> placeholderMap, String userGoogleAccessToken);

	/**
	 * Clears the template's unused per-tactic cell ranges (values <em>and</em>
	 * formatting) when the campaign has fewer than the template's 28 tactic
	 * slots, without deleting whole spreadsheet rows or columns. The unused rows
	 * of the per-tactic summary table and the unused "Main slide N" detail blocks
	 * are located by scanning the sheet for their header/anchor labels rather than
	 * fixed cell references, so the trim survives template layout edits. The
	 * summary table's totals row is relocated (copy, not delete) to sit directly
	 * under the last real tactic instead of below a block of cleared rows. A
	 * no-op when {@code tacticCount >= 28}; slots whose anchor cannot be found are skipped.
	 *
	 * @param spreadsheetId         the workbook to trim
	 * @param tacticCount           number of real tactics (clamped 1..28)
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token;
	 *                              falls back to the service account when blank
	 */
	void trimTactics(String spreadsheetId, int tacticCount, String userGoogleAccessToken);

	/**
	 * Writes the Daily pacing / Monthly pacing / Channel Distribution tables for every
	 * active tactic directly into the cloned workbook. Each block is located by scanning
	 * for its {@code "Daily pacing N"} / {@code "Monthly pacing N"} / {@code "Channel
	 * Distribution N"} anchor label rather than a fixed cell reference, mirroring the
	 * Slides chart pipeline's tactic pivot/distribution data. Per-tactic failures are
	 * collected and returned rather than aborting the rest of the workbook.
	 *
	 * @param spreadsheetId the cloned workbook to write into
	 * @param request       the pacing-table inputs
	 * @return human-readable error strings for any per-tactic failures (empty on full success)
	 */
	List<String> writePacingTables(String spreadsheetId, PacingTablesRequest request);

	/**
	 * Reads the workbook's first tab back as a rectangular-tolerant grid of trimmed cell
	 * strings, so the "Slides from Sheet" flow can re-read the numbers and metrics the user
	 * reviewed and edited. The reverse of {@link #createSheet}: reads the same tab
	 * {@link #writePacingTables} writes into.
	 *
	 * @param spreadsheetId         the workbook to read
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back
	 *                              to the service account when blank
	 * @return the first tab's rows, each cell trimmed; an empty list when the workbook has no tabs
	 */
	List<List<String>> readSheetGrid(String spreadsheetId, String userGoogleAccessToken);

	/**
	 * Applies the Step-3 breakdown toggles to the generated workbook's {@code "Breakdowns"}
	 * tab: for every per-tactic breakdown section (Top Publishers / Creative / Geo / Audience
	 * / Device) that the tactic did <em>not</em> enable, clears that section's cells (values,
	 * formulas and formatting) in place — no rows or columns are ever deleted, so the surviving
	 * sections and any totals formulas stay valid. Each section is located by its
	 * {@code "<label> N"} header anchor (e.g. {@code "Geo analysis 3"}); its column span is
	 * derived from the next section's anchor on the same row and its row height from the spacing
	 * between tactic blocks, so the clear survives template layout edits. A tactic absent from
	 * {@code enabledByTactic} (or mapped to an empty set) has all five sections cleared.
	 *
	 * <p>A no-op when the workbook has no {@code "Breakdowns"} tab; sections whose anchor cannot
	 * be found are skipped rather than failing the whole operation.
	 *
	 * @param spreadsheetId         the workbook to update
	 * @param enabledByTactic       1-based tactic number → the breakdown sections that tactic enabled
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 */
	void clearBreakdowns(
			String spreadsheetId, Map<Integer, Set<BreakdownType>> enabledByTactic, String userGoogleAccessToken);

	/**
	 * Reads back the hand-entered "Top Publishers" tables from the generated workbook's
	 * {@code "Breakdowns"} tab, so the deck can carry the publisher rows exactly as the user typed
	 * them. Each tactic's table is located by its {@code "Top Publishers N"} anchor cell — matched
	 * on the <em>whole</em> cell, never a prefix, because {@code "Top Publishers 1"} is a prefix of
	 * {@code "Top Publishers 15"} and a loose match would pull tactic 15's rows into tactic 1.
	 *
	 * <p>Within each block the {@code Publisher} / {@code Impressions} / {@code Share of voice}
	 * columns are resolved by their header text rather than fixed offsets, so a column shift in the
	 * template cannot silently misalign the data. Rows the user left blank are omitted, so the
	 * returned list is only as long as the table was actually filled (never padded).
	 *
	 * <p>The whole tab is read once for every requested tactic. A tactic whose anchor is missing, or
	 * whose table is empty, maps to an empty list rather than failing the read; a workbook without a
	 * {@code "Breakdowns"} tab yields an empty map.
	 *
	 * @param spreadsheetId         the workbook to read
	 * @param tacticNums            1-based tactic numbers whose publisher tables are wanted
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return tactic number → its filled publisher rows, in sheet order
	 */
	Map<Integer, List<PublisherRow>> readPublisherTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken);
}
