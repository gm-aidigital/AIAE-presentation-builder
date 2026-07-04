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
	 * @param tacticCount      number of active tactics (clamped 1..7)
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
	 * @param tacticCount     number of active tactics (clamped 1..7)
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, int tacticCount, String userGoogleToken);
}
