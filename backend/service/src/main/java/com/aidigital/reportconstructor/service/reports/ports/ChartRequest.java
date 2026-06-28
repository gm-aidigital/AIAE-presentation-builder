package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;

import java.util.List;
import java.util.Map;

/**
 * Inputs for {@link ChartProvider#buildCharts(ChartRequest)}.
 *
 * @param presentationId        id of the cloned deck whose placeholder charts get replaced
 * @param bqRows                raw BigQuery export rows (the Adjustments / actuals grid)
 * @param lineItemMapping       tactic-number &rarr; line-item-id mapping
 * @param flightTs              resolved flight window, or {@code null}
 * @param tacticCount           number of active tactics (1..7)
 * @param campaignTitle         deck title, used for folder / file names
 * @param distTacticNames       tactic-number &rarr; display name (from {@code {{tactic n}}})
 * @param distTacticImps        tactic-number &rarr; impressions (from {@code {{tactic n imps}}})
 * @param distTotalImps         total impressions (from {@code {{total imps}}})
 * @param tacticKpiTypes        tactic-number &rarr; KPI type ({@code "ctr"}/{@code "vcr"}, or {@code null}
 *                              when the tactic name maps to neither) derived from the channel mapping; drives
 *                              the daily/monthly chart's KPI-type header and which metric (clicks vs
 *                              completions) fills the Amount column
 * @param userGoogleAccessToken optional signed-in user token; when present the
 *                              charts are built under that user's Drive, matching
 *                              where the deck was created
 */
public record ChartRequest(
		String presentationId,
		List<List<String>> bqRows,
		List<LineItemMapping> lineItemMapping,
		FlightDates flightTs,
		int tacticCount,
		String campaignTitle,
		Map<Integer, String> distTacticNames,
		Map<Integer, Double> distTacticImps,
		double distTotalImps,
		Map<Integer, String> tacticKpiTypes,
		String userGoogleAccessToken
) {

}
