package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

			// All EOC placeholders live on the workbook's first tab, so scope the find/replace
			// to that one sheet. The former setAllSheets(true) re-scanned every tab for every
			// token — with 28 tactic slots (~800 tokens) times the template's many tabs that
			// blew past the Sheets read timeout even for tiny campaigns. Fall back to all-sheets
			// only when the tab id can't be resolved.
			Integer placeholderSheetId = firstSheetId(sheetsClient, newId);
			List<Request> requests = new ArrayList<>(placeholderMap.size());
			for (Map.Entry<String, String> e : placeholderMap.entrySet()) {
				// Template tokens are double-brace {{...}} — the key is the full token.
				FindReplaceRequest findReplace = new FindReplaceRequest()
						.setFind(e.getKey())
						.setReplacement(e.getValue() == null ? "" : e.getValue())
						.setMatchCase(true);
				if (placeholderSheetId != null) {
					findReplace.setSheetId(placeholderSheetId);
				} else {
					findReplace.setAllSheets(true);
				}
				requests.add(new Request().setFindReplace(findReplace));
			}
			if (!requests.isEmpty()) {
				retrier.execute(
						sheetsClient.spreadsheets()
								.batchUpdate(newId, new BatchUpdateSpreadsheetRequest().setRequests(requests)),
						"createSheet batchUpdate for " + newId);
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
			retrier.execute(
					sheetsClient.spreadsheets()
							.batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(requests)),
					"trimTactics batchUpdate for " + spreadsheetId);
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
		ValueRange vr = sheetsClient.spreadsheets().values().get(spreadsheetId, range).execute();
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
	 * Resolves the numeric id of the workbook's first tab — the tab that carries every
	 * EOC placeholder — so a find/replace can be scoped to it instead of scanning all tabs.
	 *
	 * @param sheetsClient   the authenticated Sheets client
	 * @param spreadsheetId  the workbook to inspect
	 * @return the first tab's numeric sheet id, or {@code null} when the workbook has no tabs
	 * @throws IOException when the metadata request fails
	 */
	Integer firstSheetId(Sheets sheetsClient, String spreadsheetId) throws IOException {
		Map<String, Integer> ids = fetchSheetIds(sheetsClient, spreadsheetId);
		return ids.isEmpty() ? null : ids.values().iterator().next();
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
		Spreadsheet meta = sheetsClient.spreadsheets().get(spreadsheetId)
				.setFields("sheets.properties(sheetId,title)")
				.execute();
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
