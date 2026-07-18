package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;

import java.util.List;
import java.util.Map;

/**
 * Builds chart requests for a generated slide deck and trims unused tactic slides.
 */
public interface ReportGenerationChartHelper {

	/**
	 * Renders charts on the presentation when BQ/adjustments/mapping inputs are present.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param payload          generation request supplying sheet rows, mapping, and BQ sheet id
	 * @param data             aggregated campaign metrics used for chart date ranges
	 * @param flatReplacements resolved placeholder values keyed by token
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering, or skip/failure messages as strings
	 */
	List<String> buildCharts(
			String slideUrl,
			GeneratePayload payload,
			CampaignData data,
			Map<String, String> flatReplacements,
			String userGoogleToken
	);

	/**
	 * Renders the pacing charts for the "Slides from Sheet" flow, reading the daily/monthly pacing
	 * series straight from the (user-edited) sheet grid instead of BigQuery. The distribution charts
	 * are still driven by the resolved {@code {{tactic n imps}}} / {@code {{total imps}}} placeholders.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param grid             the filled sheet's first tab, as trimmed cell strings, carrying the pacing blocks
	 * @param flatReplacements resolved placeholder values read back from the sheet
	 * @param tacticCount      number of active tactics (clamped 1..28)
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering, or skip/failure messages as strings
	 */
	List<String> buildChartsFromSheet(
			String slideUrl,
			List<List<String>> grid,
			Map<String, String> flatReplacements,
			int tacticCount,
			String userGoogleToken
	);

	/**
	 * Removes unused tactic slides from the deck when the presentation id can be parsed.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param payload         generation request whose Media Plan drives tactic count
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken);

	/**
	 * Removes unused tactic slides from the deck for an explicit tactic count, for the
	 * "Slides from Sheet" flow where there is no Media Plan to derive the count from — the
	 * count comes from the sheet's filled tactic rows instead.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param tacticCount     number of active tactics (clamped 1..28)
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, int tacticCount, String userGoogleToken);

	/**
	 * Inserts the Step-3 per-tactic breakdown slides into the built deck for the "Slides from Sheet"
	 * flow. Resolves the request's breakdown selections to enabled sections, drops tactics beyond the
	 * active count, and delegates to the slides provider. Non-fatal: a failure is logged and the deck
	 * is delivered without the breakdown slides.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param payload          generation request carrying the Step-3 breakdown selections
	 * @param tacticCount      number of active tactics (clamped 1..28); selections above this are ignored
	 * @param breakdownValues  renumbered token → value for the inserted slides; tokens absent from the map
	 *                         are only renumbered and would ship raw
	 * @param userGoogleToken  OAuth token for Google Slides API, or null when unavailable
	 */
	void addBreakdownSlides(
			String slideUrl, GeneratePayload payload, int tacticCount, Map<String, String> breakdownValues,
			String userGoogleToken);

	/**
	 * Links the per-tactic audience/device breakdown charts onto the breakdown slides inserted by
	 * {@link #addBreakdownSlides}. Reads each enabled tactic's device/age impressions back from the
	 * reviewed sheet, gives every chart its own copy of the section's source workbook filled with those
	 * impressions, and relinks the duplicated slide chart to it. Must run after {@code addBreakdownSlides}
	 * (the slides and their charts must already exist) and is non-fatal: a failure is logged and the deck
	 * is delivered with the breakdown slides' charts left as duplicated (empty) placeholders.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param payload         generation request carrying the Step-3 breakdown selections and sheet URL
	 * @param tacticCount     number of active tactics (clamped 1..28); selections above this are ignored
	 * @param flatReplacements resolved placeholder values, source of the campaign title used for copy names
	 * @param userGoogleToken OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering (empty when every chart drew cleanly)
	 */
	List<String> buildBreakdownCharts(
			String slideUrl, GeneratePayload payload, int tacticCount, Map<String, String> flatReplacements,
			String userGoogleToken);
}
