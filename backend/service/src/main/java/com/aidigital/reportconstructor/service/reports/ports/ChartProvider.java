package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the per-tactic chart generation ported from PHP
 * {@code api/chart_builder.php}. The real provider copies the helper
 * chart-template spreadsheets, writes the pivoted actuals, applies the saved
 * chart spec and swaps the placeholder charts on the deck slides for live
 * linked charts; the stub provider is a no-op for offline demos.
 *
 * <p>Bean selection mirrors {@link SlidesProvider}: when
 * {@code GOOGLE_SERVICE_ACCOUNT_JSON} is present the real provider wins via
 * {@code @Primary}, otherwise the stub is the only candidate.
 */
public interface ChartProvider {

    /** @return true when the provider is talking to the real Google APIs. */
    boolean isLive();

    /**
     * Builds the three charts (daily pacing, monthly distribution, weighted
     * impression contribution) for every active tactic. Per-chart failures are
     * collected and returned without aborting the rest of the deck.
     *
     * @return human-readable error strings (empty when everything succeeded)
     */
    List<String> buildCharts(ChartRequest request);

    /**
     * Inputs for {@link #buildCharts(ChartRequest)}.
     *
     * @param presentationId         id of the cloned deck whose placeholder charts get replaced
     * @param bqRows                 raw BigQuery export rows (the Adjustments / actuals grid)
     * @param lineItemMapping        tactic-number → line-item-id mapping
     * @param flightTs               resolved flight window, or {@code null}
     * @param tacticCount            number of active tactics (1..7)
     * @param campaignTitle          deck title, used for folder / file names
     * @param distTacticNames        tactic-number → display name (from {@code {{tactic n}}})
     * @param distTacticImps         tactic-number → impressions (from {@code {{tactic n imps}}})
     * @param distTotalImps          total impressions (from {@code {{total imps}}})
     * @param userGoogleAccessToken  optional signed-in user token; when present the
     *                               charts are built under that user's Drive, matching
     *                               where the deck was created
     */
    record ChartRequest(
        String presentationId,
        List<List<String>> bqRows,
        List<LineItemMapping> lineItemMapping,
        FlightDates flightTs,
        int tacticCount,
        String campaignTitle,
        Map<Integer, String> distTacticNames,
        Map<Integer, Double> distTacticImps,
        double distTotalImps,
        String userGoogleAccessToken
    ) {}
}
