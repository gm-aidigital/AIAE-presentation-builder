package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the filled Google Sheet for the "Generate Sheet" flow and trims the
 * template's unused per-tactic cell ranges. The Slides equivalent lives in
 * {@link ReportGenerationChartHelper}; both take the same resolved placeholder
 * map and Media-Plan-derived tactic count.
 */
public interface ReportSheetHelper {

	/**
	 * Clones the Sheets template and replaces every {@code {{token}}} with its value.
	 *
	 * @param jobId            orchestration job id used as a correlation suffix
	 * @param fileName         Drive file name to give the generated workbook
	 * @param flatReplacements resolved placeholder values keyed by {@code {{token}}}
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return the public Sheets URL of the generated workbook
	 */
	String buildSheet(String jobId, String fileName, Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Clears the unused per-tactic ranges of the generated workbook when the spreadsheet
	 * id can be parsed from the URL. Non-fatal: trimming failures are logged and swallowed
	 * so a filled-but-untrimmed sheet is still returned to the user.
	 *
	 * @param sheetUrl        URL of the generated Google Sheet
	 * @param payload         generation request whose Media Plan drives tactic count
	 * @param userGoogleToken OAuth token for Google Sheets API, or null when unavailable
	 */
	void trimUnusedTactics(String sheetUrl, GeneratePayload payload, String userGoogleToken);

	/**
	 * Applies the Step-3 breakdown toggles to the generated workbook's "Breakdowns" tab: every
	 * per-tactic breakdown section a tactic did not enable is cleared in place (no rows deleted),
	 * leaving only the selected sections. A no-op when the payload carries no breakdown selections
	 * (null) or the spreadsheet id cannot be parsed from the URL. Non-fatal: failures are logged and
	 * swallowed so a filled sheet is still returned.
	 *
	 * @param sheetUrl        URL of the generated Google Sheet
	 * @param payload         generation request carrying the per-tactic breakdown selections
	 * @param userGoogleToken OAuth token for Google Sheets API, or null when unavailable
	 */
	void clearUnselectedBreakdowns(String sheetUrl, GeneratePayload payload, String userGoogleToken);

	/**
	 * Writes the Daily pacing / Monthly pacing / Channel Distribution tables for every
	 * active tactic into the generated workbook, sourced from the same BigQuery actuals
	 * used by the Slides chart pipeline. A no-op returning no warnings when the payload
	 * carries no BigQuery linkage.
	 *
	 * @param sheetUrl         URL of the generated Google Sheet
	 * @param payload          generation request carrying the BigQuery rows and line-item mapping
	 * @param data             collected campaign data, used for the resolved flight window
	 * @param flatReplacements resolved placeholder values, used to derive tactic names/impressions
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return human-readable error strings for any per-tactic failures (empty on full success)
	 */
	List<String> writePacingTables(
			String sheetUrl, GeneratePayload payload, CampaignData data,
			Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Reads the generated workbook's first tab back as a cell grid for the "Slides from Sheet"
	 * flow, resolving the spreadsheet id from its URL. The read-back counterpart of
	 * {@link #buildSheet}.
	 *
	 * @param sheetUrl        URL of the generated Google Sheet
	 * @param userGoogleToken OAuth token for Google Sheets API, or null when unavailable
	 * @return the first tab's rows as trimmed cell strings; an empty list when the URL carries
	 *         no parseable spreadsheet id
	 */
	List<List<String>> readSheetGrid(String sheetUrl, String userGoogleToken);

	/**
	 * Reads back the hand-entered "Top Publishers" tables for the given tactics from the generated
	 * workbook's "Breakdowns" tab, resolving the spreadsheet id from its URL. Non-fatal: a failed read
	 * yields an empty map so the deck still ships, just without publisher rows.
	 *
	 * @param sheetUrl        URL of the generated Google Sheet
	 * @param tacticNums      1-based tactic numbers whose publisher tables are wanted
	 * @param userGoogleToken OAuth token for Google Sheets API, or null when unavailable
	 * @return tactic number → its filled publisher rows; an empty map when the URL carries no parseable
	 *         spreadsheet id or the read failed
	 */
	Map<Integer, List<PublisherRow>> readPublisherTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken);
}
