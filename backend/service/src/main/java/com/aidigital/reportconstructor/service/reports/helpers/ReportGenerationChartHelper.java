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
	 * Removes unused tactic slides from the deck when the presentation id can be parsed.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param payload         generation request whose Media Plan drives tactic count
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken);
}
