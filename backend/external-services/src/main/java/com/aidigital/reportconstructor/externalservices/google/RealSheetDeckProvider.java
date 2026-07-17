package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.FindReplaceRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Google Sheets + Drive implementation for the "Generate Sheet" flow. Clones
 * {@code external.google.sheets-template-id} into a new workbook named after the job,
 * runs {@code batchUpdate.findReplace} for every {@code {{token}}} → value pair, and
 * returns the public Sheets edit URL. Mirrors {@link RealSlidesProvider}.
 *
 * <p>Activated when {@link GoogleCredentialsFactory} is on the context. Falls back
 * to {@link StubSheetDeckProvider} otherwise.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealSheetDeckProvider implements SheetDeckProvider {

	private static final String APPLICATION_NAME = "Report Constructor — AI Digital";

	/**
	 * Stable leading columns of the per-tactic summary table's header row, used to locate that table by
	 * content rather than a fixed cell reference. Only the first three columns are matched: the fourth
	 * header has drifted across template revisions ({@code "KPI"} → {@code "KPI (fact)"}), and matching it
	 * exactly silently broke table detection so no unused rows were ever cleared.
	 */
	private static final List<String> SUMMARY_HEADER = List.of("Tactic name", "Benchmark", "KPI type");

	/**
	 * Labels the anchor cell of each per-tactic detail block ({@code "Main slide 1"} … {@code "Main slide 28"}).
	 */
	private static final Pattern MAIN_SLIDE_LABEL = Pattern.compile("(?i)^main slide\\s+(\\d+)$");

	/**
	 * Per-tactic detail block size below/right of its "Main slide N" anchor cell.
	 */
	private static final int MAIN_SLIDE_ROWS_DOWN = 14;
	private static final int MAIN_SLIDE_COLS_RIGHT = 1;

	/**
	 * Rows above a {@code "Main slide N"} anchor that hold the block's {@code {{tactic N}}} heading, cleared
	 * along with the block so an unused slot's title is not left as a raw token.
	 */
	private static final int MAIN_SLIDE_TITLE_ROWS_UP = 1;

	/**
	 * Max tactics the EOC template carries — summary rows and "Main slide" blocks are numbered 1..28.
	 */
	private static final int MAX_TACTICS = 28;

	/**
	 * Em-dash written into an unused tactic slot's cells. Text is ignored by the totals row's {@code =SUM(...)}
	 * formulas, so dashing empty slots (instead of deleting or relocating rows) keeps those sums intact.
	 */
	private static final String DASH = "—";

	/**
	 * Max requests sent in a single {@code batchUpdate}. A 20–28-tactic EOC report expands to ~800
	 * {@code {{token}}} find/replace operations; packing them all into one atomic batchUpdate made
	 * Google Sheets return repeated 500/503 {@code backendError}s under the payload's weight (job 128
	 * failed at "Building sheet" after exhausting every retry on a single oversized call). Splitting the
	 * work into fixed-size chunks — each sent as its own batchUpdate and retried independently — keeps
	 * every request small enough for Sheets to accept. Safe because find/replace and cell-clear requests
	 * target disjoint tokens/ranges, so chunk boundaries and ordering never change the outcome.
	 */
	private static final int BATCH_UPDATE_CHUNK_SIZE = 100;

	/**
	 * Tab holding the per-tactic breakdown blocks (Top Publishers / Creative / Geo / Audience / Device),
	 * one 18-row block per tactic. Located by title (never assumed to be the first tab).
	 */
	private static final String BREAKDOWN_TAB = "Breakdowns";

	/**
	 * Header text of the "Top Publishers" block's publisher-name column. Matched on the whole cell
	 * (case-insensitively), which is also what keeps it from colliding with the block's own
	 * {@code "Top Publishers N"} anchor — that cell contains the plural "Publishers".
	 */
	private static final String PUBLISHER_NAME_HEADER = "Publisher";

	/** Header text of the "Top Publishers" block's impressions column. */
	private static final String PUBLISHER_IMPRESSIONS_HEADER = "Impressions";

	/** Header text of the "Top Publishers" block's share-of-voice column. */
	private static final String PUBLISHER_SOV_HEADER = "Share of voice";

	/**
	 * Header text of the "Creative analysis" block's creative-name column. Whole-cell matching keeps it
	 * from colliding with the block's own {@code "Creative analysis N"} anchor or its {@code "TOP CREATIVE"}
	 * summary label.
	 */
	private static final String CREATIVE_NAME_HEADER = "Creative";

	/**
	 * Header texts of the "Creative analysis" table's metric columns, in slide order. Each is resolved on
	 * the header row by text, so inserting a column in the template cannot shift a metric onto the wrong
	 * slide cell.
	 */
	private static final String CREATIVE_IMPRESSIONS_HEADER = "Impressions";
	private static final String CREATIVE_CTR_HEADER = "CTR";
	private static final String CREATIVE_VCR_HEADER = "VCR";
	private static final String CREATIVE_SPEND_HEADER = "Spend";

	/**
	 * Row labels of the four summary cells above each "Creative analysis" table, whose values the slide
	 * shows in its stat tiles. Each label is matched on the whole cell and its value taken from the first
	 * populated cell to its right within the block.
	 */
	private static final String CREATIVES_LIVE_LABEL = "CREATIVES LIVE";
	private static final String CREATIVE_BEST_KPI_LABEL = "BEST CTR / VCR";
	private static final String CREATIVE_AVG_KPI_LABEL = "AVG. CTR / VCR";
	private static final String CREATIVE_TOP_LABEL = "TOP CREATIVE";

	/**
	 * Header text of the "Geo analysis" block's geo-name column. Whole-cell matching keeps it from
	 * colliding with the block's own {@code "Geo analysis N"} anchor cell.
	 */
	private static final String GEO_NAME_HEADER = "Geo";

	/** Header text of the "Geo analysis" table's impressions column. */
	private static final String GEO_IMPRESSIONS_HEADER = "IMPS";

	/**
	 * Row labels of the three summary cells above each "Geo analysis" table, whose values the slide shows
	 * in its stat tiles. {@code MARKETS ACTIVATED} and {@code TOP GEO} are matched whole-cell; the "most
	 * efficient" label is matched as a prefix because it carries the tactic's KPI type after it (typed in
	 * the template as {@code "MOST EFFICIENT {{tactic n KPI type}}"}), so the trailing text is not stable.
	 */
	private static final String GEO_MARKETS_LABEL = "MARKETS ACTIVATED";
	private static final String GEO_TOP_GEO_LABEL = "TOP GEO";
	private static final String GEO_TOP_KPI_LABEL_PREFIX = "MOST EFFICIENT";

	/**
	 * Row labels of the two summary cells above each "Audience analysis" block, whose values the slide
	 * shows in its stat tiles. Both are matched whole-cell and their value taken from the first populated
	 * cell to the label's right — the same rule the creative and geo stat tiles use.
	 */
	private static final String AUDIENCE_AGE_LABEL = "AGE DISTRIBUTION";
	private static final String AUDIENCE_GENDER_LABEL = "GENDER DEMOGRAPHICS";

	/**
	 * Header texts of the "Audience analysis" block's age-distribution sub-table (age bucket → delivered
	 * impressions). Whole-cell matching keeps {@code "age"} from matching the {@code AGE DISTRIBUTION}
	 * stat-tile label.
	 */
	private static final String AUDIENCE_AGE_HEADER = "age";
	private static final String AUDIENCE_AGE_IMPRESSIONS_HEADER = "impressions";

	/**
	 * Header texts of the "Audience analysis" block's top-audience-segments sub-table (segment name →
	 * affinity index), which sits to the right of the age-distribution sub-table on the same header row.
	 */
	private static final String AUDIENCE_SEGMENT_HEADER = "Segment";
	private static final String AUDIENCE_SEGMENT_INDEX_HEADER = "Affinity index";

	/**
	 * The only tokens the {@code "Breakdowns"} tab carries — each block's {@code {{tactic N}}} heading,
	 * its {@code {{tactic N imps}}} total, and its {@code {{tactic N KPI type}}} KPI label (the geo table's
	 * KPI column header and its "MOST EFFICIENT" stat tile). {@link #createSheet} scopes its find/replace
	 * to the first tab for speed, so these are re-sent against the breakdown tab as well; matching on the
	 * whole token keeps the extra pass to a few requests per tactic instead of the full ~800.
	 */
	private static final Pattern BREAKDOWN_TAB_TOKENS =
			Pattern.compile("^\\{\\{tactic \\d+( imps| KPI type)?}}$");

	/**
	 * Fallback height (rows) of one tactic's breakdown block, used only when the spacing between
	 * consecutive block headers cannot be inferred from the anchors themselves.
	 */
	private static final int BREAKDOWN_BLOCK_ROWS_FALLBACK = 18;

	/**
	 * Compiled {@code "<label> N"} anchor pattern for each breakdown section, keyed by type. Each tactic
	 * block repeats these headers (e.g. {@code "Geo analysis 3"}); the capture group is the 1-based tactic
	 * number. Matching is case-insensitive so template casing drift does not break detection.
	 */
	private static final Map<BreakdownType, Pattern> BREAKDOWN_ANCHORS = new EnumMap<>(BreakdownType.class);

	static {
		for (BreakdownType type : BreakdownType.values()) {
			BREAKDOWN_ANCHORS.put(type, Pattern.compile("(?i)^" + Pattern.quote(type.anchorLabel()) + "\\s+(\\d+)$"));
		}
	}

	private final GoogleCredentialsFactory creds;
	private final GoogleRequestRetrier retrier;
	private final Sheets sheets;
	private final Drive drive;
	private final String templateId;
	private final String targetFolderId;
	private final SheetPacingTableWriter pacingTableWriter;

	public RealSheetDeckProvider(
			GoogleCredentialsFactory creds, GoogleProperties props, SheetPacingTableWriter pacingTableWriter,
			GoogleRequestRetrier retrier) {
		this.creds = creds;
		this.retrier = retrier;
		this.pacingTableWriter = pacingTableWriter;
		this.sheets = new Sheets.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.drive = new Drive.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
		this.templateId = props.getSheetsTemplateId();
		this.targetFolderId = props.getSheetsTargetFolderId() == null ? "" : props.getSheetsTargetFolderId().trim();
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public String createSheet(
			String jobId, String fileName, Map<String, String> placeholderMap, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Drive driveClient = asUser ? buildDrive(userGoogleAccessToken) : drive;
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			File copy = new File().setName(fileName);
			if (!targetFolderId.isEmpty()) {
				copy.setParents(List.of(targetFolderId));
			} else if (asUser) {
				// Force the user's own My Drive root so the workbook is both owned by —
				// and located in — the signed-in user's drive rather than inheriting the
				// template's shared parent (mirrors RealSlidesProvider#createDeck).
				copy.setParents(List.of("root"));
			}
			File copied = retrier.execute(
					driveClient.files().copy(templateId, copy)
							.setFields("id,webViewLink")
							.setSupportsAllDrives(true),
					"createSheet copy of " + templateId);
			String newId = copied.getId();

			// Almost every EOC placeholder lives on the workbook's first tab, so scope the find/replace
			// to that one sheet. The former setAllSheets(true) re-scanned every tab for every
			// token — with 28 tactic slots (~800 tokens) times the template's many tabs that
			// blew past the Sheets read timeout even for tiny campaigns. Fall back to all-sheets
			// only when the tab id can't be resolved.
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, newId);
			Integer placeholderSheetId = tabSheetIds.isEmpty() ? null : tabSheetIds.values().iterator().next();
			Integer breakdownSheetId = tabSheetIds.get(BREAKDOWN_TAB);
			List<Request> requests = new ArrayList<>(placeholderMap.size());
			for (Map.Entry<String, String> e : placeholderMap.entrySet()) {
				// Template tokens are double-brace {{...}} — the key is the full token.
				String replacement = e.getValue() == null ? "" : e.getValue();
				FindReplaceRequest findReplace = new FindReplaceRequest()
						.setFind(e.getKey())
						.setReplacement(replacement)
						.setMatchCase(true);
				if (placeholderSheetId != null) {
					findReplace.setSheetId(placeholderSheetId);
				} else {
					findReplace.setAllSheets(true);
				}
				requests.add(new Request().setFindReplace(findReplace));
				// The "Breakdowns" tab repeats a couple of the first tab's tokens in its block headers, so
				// it needs its own scoped pass. Only the handful of tokens that tab actually carries are
				// re-sent: replaying all ~800 across a second tab is exactly what caused the original
				// timeout, so the filter — not the tab count — is what keeps this affordable.
				if (breakdownSheetId != null && placeholderSheetId != null
						&& BREAKDOWN_TAB_TOKENS.matcher(e.getKey()).matches()) {
					requests.add(new Request().setFindReplace(new FindReplaceRequest()
							.setFind(e.getKey())
							.setReplacement(replacement)
							.setMatchCase(true)
							.setSheetId(breakdownSheetId)));
				}
			}
			if (!requests.isEmpty()) {
				executeInChunks(sheetsClient, newId, requests, "createSheet batchUpdate for " + newId);
			}
			return "https://docs.google.com/spreadsheets/d/" + newId + "/edit";
		} catch (IOException ex) {
			log.error("[sheets] createSheet failed for job {}", jobId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets workbook creation failed: " + ex.getMessage());
		}
	}

	@Override
	public void trimTactics(String spreadsheetId, int tacticCount, String userGoogleAccessToken) {
		if (tacticCount >= MAX_TACTICS) {
			return;
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (tabSheetIds.isEmpty()) {
				return;
			}
			Map.Entry<String, Integer> firstTab = tabSheetIds.entrySet().iterator().next();
			int sheetId = firstTab.getValue();
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, firstTab.getKey());

			List<Request> requests = new ArrayList<>();
			requests.addAll(summaryRowDashRequests(grid, sheetId, tacticCount));
			requests.addAll(mainSlideClearRequests(grid, sheetId, tacticCount));

			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(sheetsClient, spreadsheetId, requests, "trimTactics batchUpdate for " + spreadsheetId);
		} catch (IOException ex) {
			log.error("[sheets] trimTactics failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets trimTactics failed: " + ex.getMessage());
		}
	}

	@Override
	public List<String> writePacingTables(String spreadsheetId, PacingTablesRequest request) {
		boolean asUser = request.userGoogleAccessToken() != null && !request.userGoogleAccessToken().isBlank();
		Sheets sheetsClient = asUser ? buildSheets(request.userGoogleAccessToken()) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (tabSheetIds.isEmpty()) {
				return List.of("Pacing tables skipped — workbook has no tabs");
			}
			String tabName = tabSheetIds.keySet().iterator().next();
			return pacingTableWriter.writeAll(sheetsClient, spreadsheetId, tabName, request);
		} catch (IOException ex) {
			log.error("[sheets] writePacingTables failed for {}", spreadsheetId, ex);
			return List.of("Pacing tables failed: " + ex.getMessage());
		}
	}

	@Override
	public List<List<String>> readSheetGrid(String spreadsheetId, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (tabSheetIds.isEmpty()) {
				return List.of();
			}
			// The EOC workbook keeps all placeholders on its first tab — the same tab
			// writePacingTables writes into — so reading it back is enough.
			String firstTab = tabSheetIds.keySet().iterator().next();
			return readGrid(sheetsClient, spreadsheetId, firstTab);
		} catch (IOException ex) {
			log.error("[sheets] readSheetGrid failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets read failed: " + ex.getMessage());
		}
	}

	@Override
	public void clearBreakdowns(
			String spreadsheetId, Map<Integer, Set<BreakdownType>> enabledByTactic, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			Integer sheetId = tabSheetIds.get(BREAKDOWN_TAB);
			if (sheetId == null) {
				log.warn("[sheets] clearBreakdowns: no \"{}\" tab in {} — skipping", BREAKDOWN_TAB, spreadsheetId);
				return;
			}
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, BREAKDOWN_TAB);
			List<Request> requests = breakdownClearRequests(grid, sheetId, enabledByTactic);
			if (requests.isEmpty()) {
				return;
			}
			executeInChunks(sheetsClient, spreadsheetId, requests, "clearBreakdowns batchUpdate for " + spreadsheetId);
		} catch (IOException ex) {
			log.error("[sheets] clearBreakdowns failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets clearBreakdowns failed: " + ex.getMessage());
		}
	}

	@Override
	public Map<Integer, List<PublisherRow>> readPublisherTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		if (tacticNums == null || tacticNums.isEmpty()) {
			return Map.of();
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (!tabSheetIds.containsKey(BREAKDOWN_TAB)) {
				log.warn("[sheets] readPublisherTables: no \"{}\" tab in {} — skipping", BREAKDOWN_TAB, spreadsheetId);
				return Map.of();
			}
			// One read of the whole tab serves every requested tactic — the blocks all live on it.
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, BREAKDOWN_TAB);
			return publisherTables(grid, tacticNums);
		} catch (IOException ex) {
			log.error("[sheets] readPublisherTables failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets readPublisherTables failed: " + ex.getMessage());
		}
	}

	@Override
	public Map<Integer, CreativeTable> readCreativeTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		if (tacticNums == null || tacticNums.isEmpty()) {
			return Map.of();
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (!tabSheetIds.containsKey(BREAKDOWN_TAB)) {
				log.warn("[sheets] readCreativeTables: no \"{}\" tab in {} — skipping", BREAKDOWN_TAB, spreadsheetId);
				return Map.of();
			}
			// One read of the whole tab serves every requested tactic — the blocks all live on it.
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, BREAKDOWN_TAB);
			return creativeTables(grid, tacticNums);
		} catch (IOException ex) {
			log.error("[sheets] readCreativeTables failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets readCreativeTables failed: " + ex.getMessage());
		}
	}

	@Override
	public Map<Integer, GeoTable> readGeoTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		if (tacticNums == null || tacticNums.isEmpty()) {
			return Map.of();
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (!tabSheetIds.containsKey(BREAKDOWN_TAB)) {
				log.warn("[sheets] readGeoTables: no \"{}\" tab in {} — skipping", BREAKDOWN_TAB, spreadsheetId);
				return Map.of();
			}
			// One read of the whole tab serves every requested tactic — the blocks all live on it.
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, BREAKDOWN_TAB);
			return geoTables(grid, tacticNums);
		} catch (IOException ex) {
			log.error("[sheets] readGeoTables failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets readGeoTables failed: " + ex.getMessage());
		}
	}

	@Override
	public Map<Integer, AudienceTable> readAudienceTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		if (tacticNums == null || tacticNums.isEmpty()) {
			return Map.of();
		}
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : sheets;
		try {
			Map<String, Integer> tabSheetIds = fetchSheetIds(sheetsClient, spreadsheetId);
			if (!tabSheetIds.containsKey(BREAKDOWN_TAB)) {
				log.warn("[sheets] readAudienceTables: no \"{}\" tab in {} — skipping", BREAKDOWN_TAB, spreadsheetId);
				return Map.of();
			}
			// One read of the whole tab serves every requested tactic — the blocks all live on it.
			List<List<String>> grid = readGrid(sheetsClient, spreadsheetId, BREAKDOWN_TAB);
			return audienceTables(grid, tacticNums);
		} catch (IOException ex) {
			log.error("[sheets] readAudienceTables failed for {}", spreadsheetId, ex);
			throw new AppException(ErrorReason.C000,
					"Google Sheets readAudienceTables failed: " + ex.getMessage());
		}
	}

	/**
	 * Extracts the requested tactics' "Geo analysis" blocks from an already-read {@code "Breakdowns"} tab.
	 * Blocks are bounded exactly as {@link #creativeTables} and {@link #publisherTables} bound theirs —
	 * anchor cell, inferred block height, next anchor on the header row — so the read, the clear and the
	 * other breakdown reads can never disagree about where a block ends.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param tacticNums 1-based tactic numbers whose blocks are wanted
	 * @return tactic number → its geo block; tactics with no anchor map to {@link GeoTable#empty()}
	 */
	Map<Integer, GeoTable> geoTables(List<List<String>> grid, Set<Integer> tacticNums) {
		Map<Integer, GeoTable> tables = new LinkedHashMap<>();
		List<int[]> anchors = findBreakdownAnchors(grid);
		if (anchors.isEmpty()) {
			log.warn("[sheets] readGeoTables: no breakdown anchors on \"{}\" tab — nothing to read", BREAKDOWN_TAB);
			return tables;
		}
		int blockHeight = breakdownBlockHeight(anchors);
		int geoOrdinal = BreakdownType.GEO.ordinal();
		for (int[] anchor : anchors) {
			if (anchor[0] != geoOrdinal || !tacticNums.contains(anchor[1])) {
				continue;
			}
			int row = anchor[2];
			int col = anchor[3];
			int endRow = Math.min(row + blockHeight, grid.size());
			int endCol = nextAnchorColOnRow(anchors, row, col, blockRightEdge(grid, row, endRow));
			tables.put(anchor[1], geoBlock(grid, row, endRow, col, endCol, anchor[1]));
		}
		for (Integer tacticNum : tacticNums) {
			tables.putIfAbsent(tacticNum, GeoTable.empty());
		}
		return tables;
	}

	/**
	 * Reads one "Geo analysis" block: its three summary cells and its filled table rows. The summary cells
	 * and the table are independent — a user who filled only the stat tiles still gets them on the slide,
	 * and vice versa — so a missing table header costs only the rows.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the block's summary cells and rows, blank/empty where the user typed nothing
	 */
	GeoTable geoBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		return new GeoTable(
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, GEO_MARKETS_LABEL),
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, GEO_TOP_GEO_LABEL),
				geoSummaryValueByPrefix(grid, startRow, endRowExcl, startCol, endColExcl, GEO_TOP_KPI_LABEL_PREFIX),
				geoRowsInBlock(grid, startRow, endRowExcl, startCol, endColExcl, tacticNum));
	}

	/**
	 * Reads one "Geo analysis" block's filled table rows. The {@code Geo} name and {@code IMPS} columns are
	 * resolved by header text; the KPI-value column — whose header is the tactic's own
	 * {@code {{tactic n KPI type}}} token/value and so has no stable text — is taken as the next populated
	 * header column to the right of {@code IMPS}. Rows whose geo name is blank are skipped, so a partially
	 * filled table returns only the rows the user actually typed.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the filled geo rows in sheet order, or an empty list when the header is missing
	 */
	List<GeoRow> geoRowsInBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		int[] header = findGeoHeader(grid, startRow, endRowExcl, startCol, endColExcl);
		if (header == null) {
			log.warn("[sheets] readGeoTables: tactic {} block has no \"{}\" header — skipping rows",
					tacticNum, GEO_NAME_HEADER);
			return List.of();
		}
		List<GeoRow> rows = new ArrayList<>();
		for (int r = header[0] + 1; r < endRowExcl; r++) {
			String name = cellAt(grid, r, header[1]);
			if (name.isEmpty()) {
				continue;
			}
			rows.add(new GeoRow(name, cellAt(grid, r, header[2]), cellAt(grid, r, header[3])));
		}
		return rows;
	}

	/**
	 * Finds the "Geo analysis" table's header row and the columns of its three data columns. A row
	 * qualifies only when it carries the geo-name header; the impressions column is then taken from that
	 * same row by header text, and the KPI column as the next populated header cell to its right — the
	 * KPI header is the tactic's {@code {{tactic n KPI type}}} value, which has no fixed text to match on.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return {@code [headerRow, nameCol, impsCol, kpiCol]}, or {@code null} when the block carries no
	 *         geo-name header
	 */
	int[] findGeoHeader(List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl) {
		for (int r = startRow; r < endRowExcl; r++) {
			int nameCol = columnOfHeader(grid, r, startCol, endColExcl, GEO_NAME_HEADER);
			if (nameCol < 0) {
				continue;
			}
			int impsCol = columnOfHeader(grid, r, startCol, endColExcl, GEO_IMPRESSIONS_HEADER);
			return new int[] {r, nameCol, impsCol, nextPopulatedCol(grid, r, impsCol, endColExcl)};
		}
		return null;
	}

	/**
	 * Finds the first populated cell to the right of {@code afterCol} on a row, bounded by the block's
	 * right edge — used to locate the geo table's KPI-value column, whose header carries no fixed text.
	 * Returns {@code -1} when {@code afterCol} is itself absent or no populated cell follows it, so the KPI
	 * column resolves to blank rather than to a neighbouring section's cell.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param row        zero-based header row to scan
	 * @param afterCol   the column to start scanning after (typically the {@code IMPS} column)
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return the zero-based column of the next populated cell, or {@code -1} when there is none
	 */
	int nextPopulatedCol(List<List<String>> grid, int row, int afterCol, int endColExcl) {
		if (afterCol < 0) {
			return -1;
		}
		for (int c = afterCol + 1; c < endColExcl; c++) {
			if (!cellAt(grid, row, c).isEmpty()) {
				return c;
			}
		}
		return -1;
	}

	/**
	 * Reads the value paired with a summary label matched by prefix rather than whole cell: the first
	 * populated cell to the label's right, bounded by the block's own right edge. Used for the geo "most
	 * efficient" stat tile, whose label cell carries the KPI type after the fixed prefix (typed as
	 * {@code "MOST EFFICIENT {{tactic n KPI type}}"}), so a whole-cell match would never find it.
	 *
	 * @param grid        the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow    inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl  exclusive zero-based end row of the block
	 * @param startCol    inclusive zero-based column of the block's anchor cell
	 * @param endColExcl  exclusive zero-based end column of the block
	 * @param labelPrefix the case-insensitive label prefix to find (e.g. {@code "MOST EFFICIENT"})
	 * @return the label's value, or an empty string when the label or its value is absent
	 */
	String geoSummaryValueByPrefix(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, String labelPrefix) {
		for (int r = startRow; r < endRowExcl; r++) {
			int labelCol = columnOfHeaderPrefix(grid, r, startCol, endColExcl, labelPrefix);
			if (labelCol < 0) {
				continue;
			}
			for (int c = labelCol + 1; c < endColExcl; c++) {
				String value = cellAt(grid, r, c);
				if (!value.isEmpty()) {
					return value;
				}
			}
			return "";
		}
		return "";
	}

	/**
	 * Finds the column within a row whose cell starts with the given prefix, ignoring case. The prefix
	 * counterpart of {@link #columnOfHeader}, used for the geo "most efficient" label whose trailing KPI
	 * type makes a whole-cell match impossible.
	 *
	 * @param grid       the tab, read as trimmed cell strings
	 * @param row        zero-based row to scan
	 * @param startCol   inclusive zero-based start column
	 * @param endColExcl exclusive zero-based end column
	 * @param prefix     the header prefix to match
	 * @return the zero-based column, or {@code -1} when the row has no cell starting with the prefix
	 */
	int columnOfHeaderPrefix(List<List<String>> grid, int row, int startCol, int endColExcl, String prefix) {
		if (row < 0 || row >= grid.size()) {
			return -1;
		}
		List<String> cells = grid.get(row);
		int limit = Math.min(endColExcl, cells.size());
		String lower = prefix.toLowerCase();
		for (int c = Math.max(startCol, 0); c < limit; c++) {
			if (cells.get(c).toLowerCase().startsWith(lower)) {
				return c;
			}
		}
		return -1;
	}

	/**
	 * Extracts the requested tactics' "Audience analysis" blocks from an already-read {@code "Breakdowns"}
	 * tab. Blocks are bounded exactly as {@link #geoTables} bounds theirs — anchor cell, inferred block
	 * height, next anchor on the header row — so the read, the clear and the other breakdown reads can
	 * never disagree about where a block ends.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param tacticNums 1-based tactic numbers whose blocks are wanted
	 * @return tactic number → its audience block; tactics with no anchor map to {@link AudienceTable#empty()}
	 */
	Map<Integer, AudienceTable> audienceTables(List<List<String>> grid, Set<Integer> tacticNums) {
		Map<Integer, AudienceTable> tables = new LinkedHashMap<>();
		List<int[]> anchors = findBreakdownAnchors(grid);
		if (anchors.isEmpty()) {
			log.warn("[sheets] readAudienceTables: no breakdown anchors on \"{}\" tab — nothing to read",
					BREAKDOWN_TAB);
			return tables;
		}
		int blockHeight = breakdownBlockHeight(anchors);
		int audienceOrdinal = BreakdownType.AUDIENCE.ordinal();
		for (int[] anchor : anchors) {
			if (anchor[0] != audienceOrdinal || !tacticNums.contains(anchor[1])) {
				continue;
			}
			int row = anchor[2];
			int col = anchor[3];
			int endRow = Math.min(row + blockHeight, grid.size());
			int endCol = nextAnchorColOnRow(anchors, row, col, blockRightEdge(grid, row, endRow));
			tables.put(anchor[1], audienceBlock(grid, row, endRow, col, endCol, anchor[1]));
		}
		for (Integer tacticNum : tacticNums) {
			tables.putIfAbsent(tacticNum, AudienceTable.empty());
		}
		return tables;
	}

	/**
	 * Reads one "Audience analysis" block: its two stat tiles and its two side-by-side sub-tables. The
	 * stat tiles and each sub-table are independent — a user who filled only the tiles still gets them on
	 * the slide, and vice versa — so a missing sub-table header costs only that sub-table's rows.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the block's stat tiles and rows, blank/empty where the user typed nothing
	 */
	AudienceTable audienceBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		return new AudienceTable(
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, AUDIENCE_AGE_LABEL),
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, AUDIENCE_GENDER_LABEL),
				audienceAgeRowsInBlock(grid, startRow, endRowExcl, startCol, endColExcl, tacticNum),
				audienceSegmentRowsInBlock(grid, startRow, endRowExcl, startCol, endColExcl, tacticNum));
	}

	/**
	 * Reads one "Audience analysis" block's age-distribution rows. The bucket labels are pre-filled by the
	 * template, so a row is kept only where the user typed an impressions value — that value, not the
	 * label, is what marks a row as filled.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the filled age rows in sheet order, or an empty list when the header is missing
	 */
	List<AudienceAgeRow> audienceAgeRowsInBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		int[] header = findAudienceAgeHeader(grid, startRow, endRowExcl, startCol, endColExcl);
		if (header == null) {
			log.warn("[sheets] readAudienceTables: tactic {} block has no \"{}\" header — skipping age rows",
					tacticNum, AUDIENCE_AGE_HEADER);
			return List.of();
		}
		List<AudienceAgeRow> rows = new ArrayList<>();
		for (int r = header[0] + 1; r < endRowExcl; r++) {
			String impressions = cellAt(grid, r, header[2]);
			if (impressions.isEmpty()) {
				continue;
			}
			rows.add(new AudienceAgeRow(cellAt(grid, r, header[1]), impressions));
		}
		return rows;
	}

	/**
	 * Reads one "Audience analysis" block's top-audience-segment rows. Both the segment name and the
	 * affinity index are user-entered, so a row is kept only where the segment name is present.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the filled segment rows in sheet order, or an empty list when the header is missing
	 */
	List<AudienceSegmentRow> audienceSegmentRowsInBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		int[] header = findAudienceSegmentHeader(grid, startRow, endRowExcl, startCol, endColExcl);
		if (header == null) {
			log.warn("[sheets] readAudienceTables: tactic {} block has no \"{}\" header — skipping segment rows",
					tacticNum, AUDIENCE_SEGMENT_HEADER);
			return List.of();
		}
		List<AudienceSegmentRow> rows = new ArrayList<>();
		for (int r = header[0] + 1; r < endRowExcl; r++) {
			String segment = cellAt(grid, r, header[1]);
			if (segment.isEmpty()) {
				continue;
			}
			rows.add(new AudienceSegmentRow(segment, cellAt(grid, r, header[2])));
		}
		return rows;
	}

	/**
	 * Finds the age-distribution sub-table's header row and the columns of its two data columns, resolving
	 * both by header text so a template edit that shifts the block cannot read a neighbouring column.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return {@code [headerRow, ageCol, impsCol]}, or {@code null} when the block carries no {@code age}
	 *         header
	 */
	int[] findAudienceAgeHeader(List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl) {
		for (int r = startRow; r < endRowExcl; r++) {
			int ageCol = columnOfHeader(grid, r, startCol, endColExcl, AUDIENCE_AGE_HEADER);
			if (ageCol < 0) {
				continue;
			}
			return new int[] {
					r, ageCol, columnOfHeader(grid, r, startCol, endColExcl, AUDIENCE_AGE_IMPRESSIONS_HEADER)};
		}
		return null;
	}

	/**
	 * Finds the top-audience-segments sub-table's header row and the columns of its two data columns,
	 * resolving both by header text so a template edit that shifts the block cannot read a neighbouring
	 * column.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return {@code [headerRow, segmentCol, indexCol]}, or {@code null} when the block carries no
	 *         {@code Segment} header
	 */
	int[] findAudienceSegmentHeader(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl) {
		for (int r = startRow; r < endRowExcl; r++) {
			int segmentCol = columnOfHeader(grid, r, startCol, endColExcl, AUDIENCE_SEGMENT_HEADER);
			if (segmentCol < 0) {
				continue;
			}
			return new int[] {
					r, segmentCol, columnOfHeader(grid, r, startCol, endColExcl, AUDIENCE_SEGMENT_INDEX_HEADER)};
		}
		return null;
	}

	/**
	 * Extracts the requested tactics' "Creative analysis" blocks from an already-read
	 * {@code "Breakdowns"} tab. Blocks are bounded exactly as {@link #publisherTables} bounds theirs —
	 * anchor cell, inferred block height, next anchor on the header row — so the read, the clear and
	 * the publisher read can never disagree about where a block ends.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param tacticNums 1-based tactic numbers whose blocks are wanted
	 * @return tactic number → its creative block; tactics with no anchor map to {@link CreativeTable#empty()}
	 */
	Map<Integer, CreativeTable> creativeTables(List<List<String>> grid, Set<Integer> tacticNums) {
		Map<Integer, CreativeTable> tables = new LinkedHashMap<>();
		List<int[]> anchors = findBreakdownAnchors(grid);
		if (anchors.isEmpty()) {
			log.warn("[sheets] readCreativeTables: no breakdown anchors on \"{}\" tab — nothing to read",
					BREAKDOWN_TAB);
			return tables;
		}
		int blockHeight = breakdownBlockHeight(anchors);
		int creativeOrdinal = BreakdownType.CREATIVE.ordinal();
		for (int[] anchor : anchors) {
			if (anchor[0] != creativeOrdinal || !tacticNums.contains(anchor[1])) {
				continue;
			}
			int row = anchor[2];
			int col = anchor[3];
			int endRow = Math.min(row + blockHeight, grid.size());
			int endCol = nextAnchorColOnRow(anchors, row, col, blockRightEdge(grid, row, endRow));
			tables.put(anchor[1], creativeBlock(grid, row, endRow, col, endCol, anchor[1]));
		}
		for (Integer tacticNum : tacticNums) {
			tables.putIfAbsent(tacticNum, CreativeTable.empty());
		}
		return tables;
	}

	/**
	 * Reads one "Creative analysis" block: its four summary cells and its filled table rows. The four
	 * summary cells and the table are independent — a user who filled only the stat tiles still gets
	 * them on the slide, and vice versa — so a missing table header costs only the rows.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the block's summary cells and rows, blank/empty where the user typed nothing
	 */
	CreativeTable creativeBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		return new CreativeTable(
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, CREATIVES_LIVE_LABEL),
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, CREATIVE_BEST_KPI_LABEL),
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, CREATIVE_AVG_KPI_LABEL),
				summaryValue(grid, startRow, endRowExcl, startCol, endColExcl, CREATIVE_TOP_LABEL),
				creativeRowsInBlock(grid, startRow, endRowExcl, startCol, endColExcl, tacticNum));
	}

	/**
	 * Reads one "Creative analysis" block's filled table rows, resolving the {@code Creative} /
	 * {@code Impressions} / {@code CTR} / {@code VCR} / {@code Spend} columns by header text rather than
	 * fixed offsets. Rows whose creative name is blank are skipped, so a partially filled table returns
	 * only the rows the user actually typed.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the filled creative rows in sheet order, or an empty list when the header is missing
	 */
	List<CreativeRow> creativeRowsInBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		int[] header = findCreativeHeader(grid, startRow, endRowExcl, startCol, endColExcl);
		if (header == null) {
			log.warn("[sheets] readCreativeTables: tactic {} block has no \"{}\" header — skipping rows",
					tacticNum, CREATIVE_NAME_HEADER);
			return List.of();
		}
		List<CreativeRow> rows = new ArrayList<>();
		for (int r = header[0] + 1; r < endRowExcl; r++) {
			String name = cellAt(grid, r, header[1]);
			if (name.isEmpty()) {
				continue;
			}
			rows.add(new CreativeRow(
					name,
					cellAt(grid, r, header[2]),
					cellAt(grid, r, header[3]),
					cellAt(grid, r, header[4]),
					cellAt(grid, r, header[5])));
		}
		return rows;
	}

	/**
	 * Finds the "Creative analysis" table's header row and the columns of its five data columns. A row
	 * qualifies only when it carries the creative-name header; the metric columns are then taken from that
	 * same row, defaulting to {@code -1} when absent so a template missing one column yields blanks rather
	 * than reading a neighbouring column's values.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return {@code [headerRow, nameCol, impsCol, ctrCol, vcrCol, spendCol]}, or {@code null} when the
	 *         block carries no creative-name header
	 */
	int[] findCreativeHeader(List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl) {
		for (int r = startRow; r < endRowExcl; r++) {
			int nameCol = columnOfHeader(grid, r, startCol, endColExcl, CREATIVE_NAME_HEADER);
			if (nameCol < 0) {
				continue;
			}
			return new int[] {
					r,
					nameCol,
					columnOfHeader(grid, r, startCol, endColExcl, CREATIVE_IMPRESSIONS_HEADER),
					columnOfHeader(grid, r, startCol, endColExcl, CREATIVE_CTR_HEADER),
					columnOfHeader(grid, r, startCol, endColExcl, CREATIVE_VCR_HEADER),
					columnOfHeader(grid, r, startCol, endColExcl, CREATIVE_SPEND_HEADER)};
		}
		return null;
	}

	/**
	 * Reads the value paired with a summary label inside a block: the first populated cell to the label's
	 * right, bounded by the block's own right edge so a blank cell can never pull in the neighbouring
	 * section's text. Resolving by label rather than a fixed cell is what lets the template gain a column
	 * between the label and its value without silently blanking the slide's stat tile.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param label      the whole-cell summary label to find (e.g. {@code "CREATIVES LIVE"})
	 * @return the label's value, or an empty string when the label or its value is absent
	 */
	String summaryValue(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, String label) {
		for (int r = startRow; r < endRowExcl; r++) {
			int labelCol = columnOfHeader(grid, r, startCol, endColExcl, label);
			if (labelCol < 0) {
				continue;
			}
			for (int c = labelCol + 1; c < endColExcl; c++) {
				String value = cellAt(grid, r, c);
				if (!value.isEmpty()) {
					return value;
				}
			}
			return "";
		}
		return "";
	}

	/**
	 * Extracts the requested tactics' "Top Publishers" rows from an already-read {@code "Breakdowns"}
	 * tab. Each block is bounded exactly as {@link #breakdownClearRequests} bounds it — anchor cell,
	 * inferred block height, next anchor on the header row — so the read and the clear can never
	 * disagree about where a block ends.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param tacticNums 1-based tactic numbers whose tables are wanted
	 * @return tactic number → its filled publisher rows, in sheet order; tactics with no anchor or no
	 *         filled rows map to an empty list
	 */
	Map<Integer, List<PublisherRow>> publisherTables(List<List<String>> grid, Set<Integer> tacticNums) {
		Map<Integer, List<PublisherRow>> tables = new LinkedHashMap<>();
		List<int[]> anchors = findBreakdownAnchors(grid);
		if (anchors.isEmpty()) {
			log.warn("[sheets] readPublisherTables: no breakdown anchors on \"{}\" tab — nothing to read",
					BREAKDOWN_TAB);
			return tables;
		}
		int blockHeight = breakdownBlockHeight(anchors);
		int publisherOrdinal = BreakdownType.TOP_PUBLISHERS.ordinal();
		for (int[] anchor : anchors) {
			if (anchor[0] != publisherOrdinal || !tacticNums.contains(anchor[1])) {
				continue;
			}
			int row = anchor[2];
			int col = anchor[3];
			int endRow = Math.min(row + blockHeight, grid.size());
			int endCol = nextAnchorColOnRow(anchors, row, col, blockRightEdge(grid, row, endRow));
			tables.put(anchor[1], publisherRowsInBlock(grid, row, endRow, col, endCol, anchor[1]));
		}
		for (Integer tacticNum : tacticNums) {
			tables.putIfAbsent(tacticNum, List.of());
		}
		return tables;
	}

	/**
	 * Reads one "Top Publishers" block's filled rows. The block's header row is located inside the
	 * block window and its {@code Publisher} / {@code Impressions} / {@code Share of voice} columns
	 * are resolved by header text — never by fixed offsets — so a column shift in the template cannot
	 * silently shift the data. Rows whose publisher name is blank are skipped, so a partially filled
	 * table returns only the rows the user actually typed.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @param tacticNum  the block's tactic number, used only for logging
	 * @return the filled publisher rows in sheet order, or an empty list when the header is missing
	 */
	List<PublisherRow> publisherRowsInBlock(
			List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl, int tacticNum) {
		int[] header = findPublisherHeader(grid, startRow, endRowExcl, startCol, endColExcl);
		if (header == null) {
			log.warn("[sheets] readPublisherTables: tactic {} block has no \"{}\" header — skipping",
					tacticNum, PUBLISHER_NAME_HEADER);
			return List.of();
		}
		List<PublisherRow> rows = new ArrayList<>();
		for (int r = header[0] + 1; r < endRowExcl; r++) {
			String name = cellAt(grid, r, header[1]);
			if (name.isEmpty()) {
				continue;
			}
			rows.add(new PublisherRow(name, cellAt(grid, r, header[2]), cellAt(grid, r, header[3])));
		}
		return rows;
	}

	/**
	 * Finds the "Top Publishers" block's header row and the columns of its three data columns.
	 * A row qualifies only when it carries the publisher-name header; the impressions and
	 * share-of-voice columns are then taken from that same row, defaulting to {@code -1} when absent
	 * so a template missing one column yields blanks rather than reading a neighbouring column's values.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based row of the block's anchor cell
	 * @param endRowExcl exclusive zero-based end row of the block
	 * @param startCol   inclusive zero-based column of the block's anchor cell
	 * @param endColExcl exclusive zero-based end column of the block
	 * @return {@code [headerRow, nameCol, impressionsCol, shareOfVoiceCol]}, or {@code null} when the
	 *         block carries no publisher-name header
	 */
	int[] findPublisherHeader(List<List<String>> grid, int startRow, int endRowExcl, int startCol, int endColExcl) {
		for (int r = startRow; r < endRowExcl; r++) {
			int nameCol = columnOfHeader(grid, r, startCol, endColExcl, PUBLISHER_NAME_HEADER);
			if (nameCol < 0) {
				continue;
			}
			return new int[] {
					r,
					nameCol,
					columnOfHeader(grid, r, startCol, endColExcl, PUBLISHER_IMPRESSIONS_HEADER),
					columnOfHeader(grid, r, startCol, endColExcl, PUBLISHER_SOV_HEADER)};
		}
		return null;
	}

	/**
	 * Finds the column within a row whose whole cell equals the given header text, ignoring case.
	 * Whole-cell (not {@code contains}) matching is what keeps {@code "Publisher"} from matching the
	 * block's {@code "Top Publishers N"} anchor cell.
	 *
	 * @param grid       the tab, read as trimmed cell strings
	 * @param row        zero-based row to scan
	 * @param startCol   inclusive zero-based start column
	 * @param endColExcl exclusive zero-based end column
	 * @param header     the header text to match
	 * @return the zero-based column, or {@code -1} when the row has no such header
	 */
	int columnOfHeader(List<List<String>> grid, int row, int startCol, int endColExcl, String header) {
		if (row < 0 || row >= grid.size()) {
			return -1;
		}
		List<String> cells = grid.get(row);
		int limit = Math.min(endColExcl, cells.size());
		for (int c = Math.max(startCol, 0); c < limit; c++) {
			if (cells.get(c).equalsIgnoreCase(header)) {
				return c;
			}
		}
		return -1;
	}

	/**
	 * Reads one cell defensively from a rectangular-tolerant grid: rows are short when their trailing
	 * cells are empty, and a column the template does not carry resolves to {@code -1}.
	 *
	 * @param grid the tab, read as trimmed cell strings
	 * @param row  zero-based row
	 * @param col  zero-based column, or {@code -1} for a column that was not found
	 * @return the trimmed cell value, or an empty string when out of range
	 */
	String cellAt(List<List<String>> grid, int row, int col) {
		if (col < 0 || row < 0 || row >= grid.size()) {
			return "";
		}
		List<String> cells = grid.get(row);
		return col < cells.size() ? cells.get(col) : "";
	}

	/**
	 * Applies the given batchUpdate requests to a workbook in fixed-size chunks of at most
	 * {@link #BATCH_UPDATE_CHUNK_SIZE}, each sent as its own {@code batchUpdate} and retried
	 * independently via {@link GoogleRequestRetrier}. A single batchUpdate carrying every request
	 * (~800 for a full 28-tactic report) drew repeated 500/503 {@code backendError}s from Sheets and
	 * failed job 128 outright; chunking keeps each request small enough to succeed. Safe because the
	 * requests target disjoint tokens/ranges, so splitting them across batches never changes the result.
	 *
	 * @param sheetsClient  the authenticated Sheets client
	 * @param spreadsheetId the workbook to update
	 * @param requests      the batchUpdate requests to apply, in any order
	 * @param description   short context used in retry log lines
	 * @throws IOException when a chunk fails with a non-retryable error or exhausts all attempts
	 */
	void executeInChunks(Sheets sheetsClient, String spreadsheetId, List<Request> requests, String description)
			throws IOException {
		int total = requests.size();
		int chunks = (total + BATCH_UPDATE_CHUNK_SIZE - 1) / BATCH_UPDATE_CHUNK_SIZE;
		for (int start = 0, index = 1; start < total; start += BATCH_UPDATE_CHUNK_SIZE, index++) {
			int end = Math.min(start + BATCH_UPDATE_CHUNK_SIZE, total);
			List<Request> chunk = new ArrayList<>(requests.subList(start, end));
			retrier.execute(
					sheetsClient.spreadsheets()
							.batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(chunk)),
					description + " (chunk " + index + "/" + chunks + ")");
		}
	}

	/**
	 * Builds the requests that fill the unused rows of the per-tactic summary table with an em-dash
	 * (anchored by its {@link #SUMMARY_HEADER} row, one data row per tactic slot 1..28 directly below it).
	 * A no-op when the header cannot be located.
	 *
	 * <p>Every row and the totals row stay exactly where the template put them — nothing is deleted or
	 * relocated. Slots above {@code tacticCount} are overwritten with {@link #DASH} (replacing any leftover
	 * {@code {{tactic N …}}} token), so the totals row keeps its original position and its live
	 * {@code =SUM(...)} formulas re-sum over the full range — the dashes are text and are ignored by SUM,
	 * so the total equals the sum of the real tactic rows. This avoids the earlier scheme of moving the
	 * totals row up and pasting it as static values, which broke as soon as the underlying rows changed.
	 *
	 * @param grid        the workbook's first tab, read as trimmed cell strings
	 * @param sheetId     numeric id of that tab, used to build the {@link GridRange}s
	 * @param tacticCount number of real tactics; slots above this are dashed
	 * @return dash-fill requests for the unused summary-table rows, or an empty list
	 */
	List<Request> summaryRowDashRequests(List<List<String>> grid, int sheetId, int tacticCount) {
		int headerRow = findSummaryHeaderRow(grid);
		if (headerRow < 0) {
			log.warn("[sheets] trimTactics: summary table header {} not found — skipping row dash-fill",
					SUMMARY_HEADER);
			return List.of();
		}
		int tableWidth = tableWidth(grid.get(headerRow));
		List<Request> requests = new ArrayList<>();
		for (int t = tacticCount + 1; t <= MAX_TACTICS; t++) {
			int rowIndex = headerRow + t;
			requests.add(dashFillRequest(sheetId, rowIndex, rowIndex + 1, 0, tableWidth));
		}
		return requests;
	}

	/**
	 * Builds the clear requests for the unused per-tactic detail blocks, each anchored
	 * by a {@code "Main slide N"} cell and spanning {@link #MAIN_SLIDE_ROWS_DOWN} rows
	 * down and {@link #MAIN_SLIDE_COLS_RIGHT} columns right of that cell. The clear also
	 * covers the {@link #MAIN_SLIDE_TITLE_ROWS_UP} title row directly above the anchor,
	 * which holds the block's {@code {{tactic N}}} heading — otherwise that heading is left
	 * as a raw token now that the placeholder map is bounded to the real tactic count and no
	 * longer replaces unused-slot tokens. Slots whose anchor cell is not found are skipped
	 * rather than failing the whole trim.
	 *
	 * @param grid        the workbook's first tab, read as trimmed cell strings
	 * @param sheetId     numeric id of that tab, used to build the {@link GridRange}s
	 * @param tacticCount number of real tactics; slots above this are cleared
	 * @return clear requests for the unused detail blocks, or an empty list
	 */
	List<Request> mainSlideClearRequests(List<List<String>> grid, int sheetId, int tacticCount) {
		Map<Integer, int[]> anchors = findMainSlideAnchors(grid);
		List<Request> requests = new ArrayList<>();
		for (int t = tacticCount + 1; t <= MAX_TACTICS; t++) {
			int[] anchor = anchors.get(t);
			if (anchor == null) {
				log.warn("[sheets] trimTactics: \"Main slide {}\" anchor not found — skipping its detail block", t);
				continue;
			}
			int startRow = Math.max(0, anchor[0] - MAIN_SLIDE_TITLE_ROWS_UP);
			requests.add(clearRequest(sheetId,
					startRow, anchor[0] + MAIN_SLIDE_ROWS_DOWN + 1,
					anchor[1], anchor[1] + MAIN_SLIDE_COLS_RIGHT + 1));
		}
		return requests;
	}

	/**
	 * Builds a request that writes {@link #DASH} into every cell of a grid range, leaving formatting intact
	 * (only the entered value is set). Used to mark an unused tactic slot as empty while keeping the row in
	 * place so the totals row's {@code =SUM(...)} formulas below it stay valid.
	 *
	 * @param sheetId  numeric id of the tab to write within
	 * @param startRow inclusive zero-based start row
	 * @param endRow   exclusive zero-based end row
	 * @param startCol inclusive zero-based start column
	 * @param endCol   exclusive zero-based end column
	 * @return the {@code RepeatCell} dash-fill request
	 */
	Request dashFillRequest(int sheetId, int startRow, int endRow, int startCol, int endCol) {
		GridRange range = new GridRange()
				.setSheetId(sheetId)
				.setStartRowIndex(startRow)
				.setEndRowIndex(endRow)
				.setStartColumnIndex(startCol)
				.setEndColumnIndex(endCol);
		CellData dash = new CellData().setUserEnteredValue(new ExtendedValue().setStringValue(DASH));
		return new Request().setRepeatCell(new RepeatCellRequest()
				.setRange(range)
				.setCell(dash)
				.setFields("userEnteredValue"));
	}

	/**
	 * Builds a value-and-formatting clear request over a grid range. An empty
	 * {@link CellData} with {@code fields="*"} resets both, in place — rows and
	 * columns are never deleted.
	 *
	 * @param sheetId      numeric id of the tab to clear within
	 * @param startRow     inclusive zero-based start row
	 * @param endRow       exclusive zero-based end row
	 * @param startCol     inclusive zero-based start column
	 * @param endCol       exclusive zero-based end column
	 * @return the {@code RepeatCell} clear request
	 */
	Request clearRequest(int sheetId, int startRow, int endRow, int startCol, int endCol) {
		GridRange range = new GridRange()
				.setSheetId(sheetId)
				.setStartRowIndex(startRow)
				.setEndRowIndex(endRow)
				.setStartColumnIndex(startCol)
				.setEndColumnIndex(endCol);
		return new Request().setRepeatCell(new RepeatCellRequest()
				.setRange(range)
				.setCell(new CellData())
				.setFields("*"));
	}

	/**
	 * Finds the row whose first {@link #SUMMARY_HEADER}{@code .size()} cells match
	 * the summary table's header labels exactly.
	 *
	 * @param grid the workbook tab, read as trimmed cell strings
	 * @return the zero-based row index, or {@code -1} when no row matches
	 */
	int findSummaryHeaderRow(List<List<String>> grid) {
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			if (row.size() >= SUMMARY_HEADER.size() && row.subList(0, SUMMARY_HEADER.size()).equals(SUMMARY_HEADER)) {
				return r;
			}
		}
		return -1;
	}

	/**
	 * Counts the contiguous non-blank cells starting at column 0 of the header row,
	 * giving the summary table's column span.
	 *
	 * @param headerRow the header row's trimmed cell strings
	 * @return the table width in columns
	 */
	int tableWidth(List<String> headerRow) {
		int width = 0;
		while (width < headerRow.size() && !headerRow.get(width).isEmpty()) {
			width++;
		}
		return width;
	}

	/**
	 * Scans the whole tab for {@code "Main slide N"} anchor cells.
	 *
	 * @param grid the workbook tab, read as trimmed cell strings
	 * @return tactic number (1-based) to its anchor cell's zero-based {@code [row, col]}
	 */
	Map<Integer, int[]> findMainSlideAnchors(List<List<String>> grid) {
		Map<Integer, int[]> anchors = new LinkedHashMap<>();
		for (int r = 0; r < grid.size(); r++) {
			List<String> row = grid.get(r);
			for (int c = 0; c < row.size(); c++) {
				Matcher m = MAIN_SLIDE_LABEL.matcher(row.get(c));
				if (m.matches()) {
					anchors.put(Integer.parseInt(m.group(1)), new int[] {r, c});
				}
			}
		}
		return anchors;
	}

	/**
	 * Builds the clear requests for every breakdown section a tactic did not enable, on the
	 * {@code "Breakdowns"} tab. Each section is located by its {@code "<label> N"} header anchor; its
	 * column span runs from the anchor to the next section's anchor on the same header row (the last
	 * section extends to the block's right edge), and its row height is the spacing between consecutive
	 * block headers. Sections a tactic enabled are left untouched; a tactic absent from
	 * {@code enabledByTactic} has all of its sections cleared. A no-op returning an empty list when the
	 * tab carries no recognizable anchors.
	 *
	 * @param grid            the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param sheetId         numeric id of that tab, used to build the {@link GridRange}s
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return clear requests for the unselected sections, or an empty list
	 */
	List<Request> breakdownClearRequests(
			List<List<String>> grid, int sheetId, Map<Integer, Set<BreakdownType>> enabledByTactic) {
		List<int[]> anchors = findBreakdownAnchors(grid);
		if (anchors.isEmpty()) {
			log.warn("[sheets] clearBreakdowns: no breakdown anchors found on \"{}\" tab — nothing to clear",
					BREAKDOWN_TAB);
			return List.of();
		}
		int blockHeight = breakdownBlockHeight(anchors);
		BreakdownType[] types = BreakdownType.values();
		List<Request> requests = new ArrayList<>();
		for (int[] anchor : anchors) {
			BreakdownType type = types[anchor[0]];
			int tacticNum = anchor[1];
			int row = anchor[2];
			int col = anchor[3];
			Set<BreakdownType> enabled = enabledByTactic.get(tacticNum);
			if (enabled != null && enabled.contains(type)) {
				continue;
			}
			int endRow = row + blockHeight;
			int endCol = nextAnchorColOnRow(anchors, row, col, blockRightEdge(grid, row, endRow));
			requests.add(clearRequest(sheetId, row, endRow, col, endCol));
		}
		return requests;
	}

	/**
	 * Scans the whole tab for breakdown-section anchor cells ({@code "Top Publishers N"},
	 * {@code "Creative analysis N"}, …).
	 *
	 * @param grid the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @return one entry per anchor as {@code [type ordinal, tacticNum, row, col]} (all zero-based
	 *         except {@code tacticNum}), in row-major order
	 */
	List<int[]> findBreakdownAnchors(List<List<String>> grid) {
		List<int[]> anchors = new ArrayList<>();
		BreakdownType[] types = BreakdownType.values();
		for (int r = 0; r < grid.size(); r++) {
			List<String> rowCells = grid.get(r);
			for (int c = 0; c < rowCells.size(); c++) {
				String cell = rowCells.get(c);
				if (cell.isEmpty()) {
					continue;
				}
				for (int t = 0; t < types.length; t++) {
					Matcher m = BREAKDOWN_ANCHORS.get(types[t]).matcher(cell);
					if (m.matches()) {
						anchors.add(new int[] {t, Integer.parseInt(m.group(1)), r, c});
						break;
					}
				}
			}
		}
		return anchors;
	}

	/**
	 * Infers the height (rows) of one tactic's breakdown block from the smallest gap between
	 * consecutive block-header rows, falling back to {@link #BREAKDOWN_BLOCK_ROWS_FALLBACK} when
	 * fewer than two header rows are present.
	 *
	 * @param anchors the breakdown anchors found on the tab
	 * @return the block height in rows
	 */
	int breakdownBlockHeight(List<int[]> anchors) {
		TreeSet<Integer> headerRows = new TreeSet<>();
		for (int[] anchor : anchors) {
			headerRows.add(anchor[2]);
		}
		int minGap = Integer.MAX_VALUE;
		Integer prev = null;
		for (int row : headerRows) {
			if (prev != null) {
				minGap = Math.min(minGap, row - prev);
			}
			prev = row;
		}
		return minGap == Integer.MAX_VALUE ? BREAKDOWN_BLOCK_ROWS_FALLBACK : minGap;
	}

	/**
	 * Finds the column of the next anchor to the right of {@code col} on the given header {@code row},
	 * which bounds a section's column span; when none exists (the rightmost section) the block's right
	 * edge is used instead.
	 *
	 * @param anchors      the breakdown anchors found on the tab
	 * @param row          the header row to search within
	 * @param col          the current section's start column
	 * @param fallbackEdge the block's right edge, used for the rightmost section
	 * @return the exclusive end column for the section starting at {@code col}
	 */
	int nextAnchorColOnRow(List<int[]> anchors, int row, int col, int fallbackEdge) {
		int next = Integer.MAX_VALUE;
		for (int[] anchor : anchors) {
			if (anchor[2] == row && anchor[3] > col) {
				next = Math.min(next, anchor[3]);
			}
		}
		return next == Integer.MAX_VALUE ? fallbackEdge : next;
	}

	/**
	 * Computes the widest populated column count across the rows of a block, giving the block's
	 * right edge (exclusive end column) used to bound the rightmost section's clear range.
	 *
	 * @param grid       the {@code "Breakdowns"} tab, read as trimmed cell strings
	 * @param startRow   inclusive zero-based block start row
	 * @param endRowExcl exclusive zero-based block end row
	 * @return the block's exclusive right-edge column
	 */
	int blockRightEdge(List<List<String>> grid, int startRow, int endRowExcl) {
		int width = 0;
		int limit = Math.min(endRowExcl, grid.size());
		for (int r = startRow; r < limit; r++) {
			width = Math.max(width, grid.get(r).size());
		}
		return width;
	}

	/**
	 * Reads a whole tab as a rectangular-tolerant grid of trimmed cell strings.
	 *
	 * @param sheetsClient the authenticated Sheets client
	 * @param spreadsheetId the workbook to read from
	 * @param tabTitle      the tab to read
	 * @return the tab's rows, each cell trimmed and blank when empty
	 * @throws IOException when the read request fails
	 */
	List<List<String>> readGrid(Sheets sheetsClient, String spreadsheetId, String tabTitle) throws IOException {
		String range = "'" + tabTitle.replace("'", "''") + "'!A1:ZZ";
		// Reads a freshly copied workbook, so guard against the same post-copy propagation 404
		// the write path sees: retry the GET until Drive's replica catches up.
		ValueRange vr = retrier.execute(
				sheetsClient.spreadsheets().values().get(spreadsheetId, range),
				"readGrid values.get for " + spreadsheetId);
		List<List<Object>> raw = vr.getValues() == null ? List.of() : vr.getValues();
		List<List<String>> rows = new ArrayList<>(raw.size());
		for (List<Object> r : raw) {
			List<String> row = new ArrayList<>(r.size());
			for (Object cell : r) {
				row.add(cell == null ? "" : cell.toString().trim());
			}
			rows.add(row);
		}
		return rows;
	}

	/**
	 * Reads the workbook's {@code title → sheetId} map, preserving tab order.
	 *
	 * @param sheetsClient   the authenticated Sheets client
	 * @param spreadsheetId  the workbook to inspect
	 * @return an ordered map of tab title to its numeric sheet id
	 * @throws IOException when the metadata request fails
	 */
	Map<String, Integer> fetchSheetIds(Sheets sheetsClient, String spreadsheetId) throws IOException {
		// Step (b) of createSheet — the first touch on the just-copied workbook. Drive's read replica
		// can lag the copy by a few seconds, so this GET is subject to the same transient 404 as the
		// batchUpdate that follows; retry it rather than failing the whole job on a propagation blip.
		Spreadsheet meta = retrier.execute(
				sheetsClient.spreadsheets().get(spreadsheetId)
						.setFields("sheets.properties(sheetId,title)"),
				"fetchSheetIds get for " + spreadsheetId);
		Map<String, Integer> ids = new LinkedHashMap<>();
		if (meta.getSheets() != null) {
			for (Sheet s : meta.getSheets()) {
				SheetProperties props = s.getProperties();
				if (props != null && props.getTitle() != null && props.getSheetId() != null) {
					ids.put(props.getTitle(), props.getSheetId());
				}
			}
		}
		return ids;
	}

	/**
	 * Builds a Drive client authenticated as the signed-in user via their short-lived
	 * Google OAuth access token (Clerk-brokered). Mirrors {@link RealSlidesProvider#buildDrive}.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return a Drive client bound to that user's credentials
	 */
	Drive buildDrive(String accessToken) {

		return new Drive.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	/**
	 * Builds a Sheets client authenticated as the signed-in user.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return a Sheets client bound to that user's credentials
	 */
	Sheets buildSheets(String accessToken) {

		return new Sheets.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	/**
	 * Wraps a raw Google OAuth access token as an HTTP request initializer that
	 * authenticates each request as the token's owner.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return an initializer bound to that user's credentials
	 */
	HttpRequestInitializer userInitializer(String accessToken) {

		return creds.withTimeout(new HttpCredentialsAdapter(
				GoogleCredentials.create(new AccessToken(accessToken, null))));
	}
}
