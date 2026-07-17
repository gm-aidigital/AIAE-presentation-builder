package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;

import java.util.List;
import java.util.Map;

/**
 * Assembles the "Top Publishers" breakdown slides' token values: the hand-entered publisher rows read
 * back from the generated sheet, plus the Claude-written KEY OBSERVATIONS bullets.
 *
 * <p>Exists because the deck's normal placeholder pass cannot fill these tokens: the breakdown slides
 * are duplicated from their masters only after the deck has been built and every token replaced, so
 * their values have to be handed to {@link ReportGenerationChartHelper#addBreakdownSlides} instead.
 */
public interface PublisherBreakdownHelper {

	/**
	 * Builds the {@code token → value} map for every tactic that enabled the Top Publishers breakdown,
	 * reading the tables the user filled in on the sheet's "Breakdowns" tab and asking Claude for each
	 * tactic's four observation bullets.
	 *
	 * <p>Values are copied from the sheet verbatim so the slide and the workbook cannot disagree. Table
	 * rows the user left blank are written as an em-dash rather than left as a raw token. A tactic whose
	 * table is entirely empty still gets its slide (the user did enable the toggle), but its observations
	 * are blank and Claude is never asked about it — there would be nothing to observe.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and
	 *                         total impressions
	 * @param brief            free-text campaign brief passed to Claude for audience context
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's token values, plus a warning per tactic whose observations Claude failed to
	 *         write; empty when no tactic enabled the Top Publishers breakdown
	 */
	BreakdownValues buildPublisherValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken);
}
