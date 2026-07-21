package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
	 * Reads the geo blocks, fills the data-only slide tokens, and returns each tactic's
	 * {@link GeoInsightInput} — WITHOUT calling Claude — for the combined per-tactic call. Insight/reco tokens
	 * are filled later with {@link #writeGeoInsights}.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and KPI type
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's enabled tactics, per-tactic Claude inputs (non-empty blocks only), and data tokens
	 */
	BreakdownSectionInputs<GeoInsightInput> readGeoInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Writes the four WHAT THE MAP TELLS US insight tokens and the recommendation token for every enabled
	 * tactic from the strings the combined call produced, blanking a tactic that came back with none and
	 * warning for one that had data but no strings.
	 *
	 * @param values           the accumulating token → value map to write into
	 * @param tactics          every tactic that enabled the Geo analysis breakdown
	 * @param sentTactics      the tactics whose blocks were non-empty and were actually sent to Claude
	 * @param insights         tactic number → its five strings (four insights then the recommendation)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name for warnings
	 * @return one warning per sent tactic that came back without insights; empty when all answered
	 */
	List<String> writeGeoInsights(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> insights, Map<String, String> flatReplacements);
}
