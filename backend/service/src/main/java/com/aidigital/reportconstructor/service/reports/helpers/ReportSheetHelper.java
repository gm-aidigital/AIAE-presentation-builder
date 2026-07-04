package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;

import java.util.Map;

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
	 * @param flatReplacements resolved placeholder values keyed by {@code {{token}}}
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return the public Sheets URL of the generated workbook
	 */
	String buildSheet(String jobId, Map<String, String> flatReplacements, String userGoogleToken);

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
}
