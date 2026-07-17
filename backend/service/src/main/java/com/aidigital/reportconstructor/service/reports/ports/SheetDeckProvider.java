package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
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

	/**
	 * Reads back the hand-entered "Creative analysis" blocks from the generated workbook's
	 * {@code "Breakdowns"} tab, so the deck can carry the creative rows exactly as the user typed them.
	 * The block-location rules are {@link #readPublisherTables}': the {@code "Creative analysis N"}
	 * anchor is matched on the <em>whole</em> cell (never a prefix, or block 1 would swallow block 15),
	 * and the {@code Creative} / {@code Impressions} / {@code CTR} / {@code VCR} / {@code Spend} columns
	 * are resolved by header text rather than fixed offsets.
	 *
	 * <p>Each block also carries four summary cells above the table ({@code CREATIVES LIVE},
	 * {@code BEST CTR / VCR}, {@code AVG. CTR / VCR}, {@code TOP CREATIVE}), located by their label and
	 * read from the first populated cell to the label's right — the template pairs them with the cell
	 * immediately beside the label, but resolving by label survives a column being inserted between them.
	 *
	 * <p>The whole tab is read once for every requested tactic. A tactic whose anchor is missing, or whose
	 * block is blank, maps to {@link CreativeTable#empty()} rather than failing the read; a workbook
	 * without a {@code "Breakdowns"} tab yields an empty map.
	 *
	 * @param spreadsheetId         the workbook to read
	 * @param tacticNums            1-based tactic numbers whose creative blocks are wanted
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return tactic number → its creative block, with rows in sheet order
	 */
	Map<Integer, CreativeTable> readCreativeTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken);

	/**
	 * Reads back the hand-entered "Geo analysis" blocks from the generated workbook's
	 * {@code "Breakdowns"} tab, so the deck can carry the geo rows exactly as the user typed them. The
	 * block-location rules are {@link #readCreativeTables}': the {@code "Geo analysis N"} anchor is matched
	 * on the <em>whole</em> cell (never a prefix, or block 1 would swallow block 15), and the {@code Geo} /
	 * {@code IMPS} columns are resolved by header text; the KPI column — whose header is the tactic's own
	 * {@code {{tactic n KPI type}}} token/value and so has no stable text — is taken as the next populated
	 * header column to the right of {@code IMPS}.
	 *
	 * <p>Each block also carries three summary cells above the table ({@code MARKETS ACTIVATED},
	 * {@code TOP GEO}, {@code MOST EFFICIENT …}), located by their label and read from the first populated
	 * cell to the label's right. {@code MARKETS ACTIVATED} and {@code TOP GEO} are matched whole-cell;
	 * {@code MOST EFFICIENT} is matched as a prefix because its label carries the KPI type after it.
	 *
	 * <p>The whole tab is read once for every requested tactic. A tactic whose anchor is missing, or whose
	 * block is blank, maps to {@link GeoTable#empty()} rather than failing the read; a workbook without a
	 * {@code "Breakdowns"} tab yields an empty map.
	 *
	 * @param spreadsheetId         the workbook to read
	 * @param tacticNums            1-based tactic numbers whose geo blocks are wanted
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return tactic number → its geo block, with rows in sheet order
	 */
	Map<Integer, GeoTable> readGeoTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken);

	/**
	 * Reads back the hand-entered "Audience analysis" blocks from the generated workbook's
	 * {@code "Breakdowns"} tab, so the deck can carry the audience stat tiles and segment rows exactly
	 * as the user typed them. The block-location rules are {@link #readGeoTables}': the
	 * {@code "Audience analysis N"} anchor is matched on the <em>whole</em> cell (never a prefix, or
	 * block 1 would swallow block 15).
	 *
	 * <p>Unlike the other sections, an audience block carries <em>two</em> side-by-side sub-tables,
	 * each located by its own header on the same row: the age-distribution table ({@code age} /
	 * {@code impressions}) and the top-audience-segments table ({@code Segment} /
	 * {@code Affinity index}). Above them sit two stat tiles ({@code AGE DISTRIBUTION},
	 * {@code GENDER DEMOGRAPHICS}), located by their label and read from the first populated cell to
	 * the label's right. The age table's bucket labels are pre-filled by the template, so its rows are
	 * kept only where the user typed an impressions value; the segment table's rows are kept only where
	 * the user typed a segment name.
	 *
	 * <p>The whole tab is read once for every requested tactic. A tactic whose anchor is missing, or
	 * whose block is blank, maps to {@link AudienceTable#empty()} rather than failing the read; a
	 * workbook without a {@code "Breakdowns"} tab yields an empty map.
	 *
	 * @param spreadsheetId         the workbook to read
	 * @param tacticNums            1-based tactic numbers whose audience blocks are wanted
	 * @param userGoogleAccessToken optional signed-in user's Google OAuth token; falls back to the
	 *                              service account when blank
	 * @return tactic number → its audience block, with rows in sheet order
	 */
	Map<Integer, AudienceTable> readAudienceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken);
}
