package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;

import java.util.List;
import java.util.Map;

/**
 * Inputs for {@link SheetDeckProvider#writePacingTables(String, PacingTablesRequest)}.
 * Mirrors {@link ChartRequest} minus the Slides-only {@code presentationId} and
 * {@code campaignTitle} fields, since the Sheets pacing tables are written directly
 * into the already-cloned workbook instead of a per-chart template copy.
 *
 * @param bqRows                raw BigQuery export rows (the Adjustments / actuals grid)
 * @param lineItemMapping       tactic-number &rarr; line-item-id mapping
 * @param flightTs              resolved flight window, or {@code null}
 * @param tacticCount           number of active tactics (1..28)
 * @param distTacticNames       tactic-number &rarr; display name (from {@code {{tactic n}}})
 * @param distTacticImps        tactic-number &rarr; impressions (from {@code {{tactic n imps}}})
 * @param distTotalImps         total impressions (from {@code {{total imps}}})
 * @param tacticKpiTypes        tactic-number &rarr; KPI-series token ({@code "ctr"}/{@code "vcr"}/{@code "acr"},
 *                              or {@code null} when the tactic name maps to none); {@code "acr"} (audio) behaves
 *                              like {@code "vcr"} for the metric series but labels the header "ACR". Drives the
 *                              daily/monthly pacing table's KPI-type header and which metric (clicks vs completions) fills
 *                              the Amount column
 * @param userGoogleAccessToken optional signed-in user token; when present the tables are written
 *                              under that user's Drive, matching where the workbook was created
 */
public record PacingTablesRequest(
		List<List<String>> bqRows,
		List<LineItemMapping> lineItemMapping,
		FlightDates flightTs,
		int tacticCount,
		Map<Integer, String> distTacticNames,
		Map<Integer, Double> distTacticImps,
		double distTotalImps,
		Map<Integer, String> tacticKpiTypes,
		String userGoogleAccessToken
) {

}
