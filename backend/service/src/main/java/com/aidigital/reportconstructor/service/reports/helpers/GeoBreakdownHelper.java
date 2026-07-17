package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;

import java.util.List;
import java.util.Map;

/**
 * Assembles the "Geo analysis" breakdown slides' token values: the hand-entered geo rows and stat
 * tiles read back from the generated sheet, plus the Claude-written "WHAT THE MAP TELLS US" bullets
 * and the forward-looking recommendation. The geo counterpart of {@link PublisherBreakdownHelper}
 * and {@link CreativeBreakdownHelper}, and it exists for the same reason: the deck's normal
 * placeholder pass cannot fill these tokens, because the breakdown slides are duplicated from their
 * masters only after the deck has been built and every token replaced, so their values have to be
 * handed to {@link ReportGenerationChartHelper#addBreakdownSlides} instead.
 */
public interface GeoBreakdownHelper {

	/**
	 * Builds the {@code token → value} map for every tactic that enabled the Geo analysis breakdown,
	 * reading the blocks the user filled in on the sheet's "Breakdowns" tab and asking Claude for each
	 * tactic's four insight bullets plus its recommendation.
	 *
	 * <p>Values are copied from the sheet verbatim so the slide and the workbook cannot disagree. Table
	 * rows and stat tiles the user left blank are written as an em-dash rather than left as a raw token,
	 * as are cells still holding the template's own {@code {{…}}} hint text. A tactic whose block is
	 * entirely blank still gets its slide (the user did enable the toggle), but its insights are blank
	 * and Claude is never asked about it — there would be nothing to observe.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and KPI type
	 * @param brief            free-text campaign brief passed to Claude for audience/goal context
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's token values, plus a warning per tactic whose insights Claude failed to write;
	 *         empty when no tactic enabled the Geo analysis breakdown
	 */
	BreakdownValues buildGeoValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken);
}
