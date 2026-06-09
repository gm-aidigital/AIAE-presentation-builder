package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured single-pass aggregation of a campaign, consumed by the resolvers and
 * the Claude batch prompts. Numeric metrics use {@code double}; {@code null} boxed
 * values mean "no data".
 *
 * @param client display name of the advertiser/client the campaign runs for
 * @param campaign campaign name as shown in placeholders and report headings
 * @param geo geographic targeting label (e.g. market or region) for the campaign
 * @param goal stated campaign objective/goal description
 * @param flightDates human-readable flight date range as rendered in the report
 * @param flightTs parsed start/end boundaries of the flight window for date math
 * @param budget formatted budget string for display in the report
 * @param primaryKpis description of the campaign's primary KPIs
 * @param tacticsList comma-/newline-separated summary listing of the tactics used
 * @param audienceAge audience age-range targeting description
 * @param audienceSegs audience segment targeting description
 * @param totals aggregated delivery and performance metrics across all tactics
 * @param tactics per-tactic data keyed by the tactic's ordinal index in the report
 * @param audienceTab raw audience-tab source content used to build audience copy
 */
public record CampaignData(
    String client,
    String campaign,
    String geo,
    String goal,
    String flightDates,
    FlightDates flightTs,
    String budget,
    String primaryKpis,
    String tacticsList,
    String audienceAge,
    String audienceSegs,
    Totals totals,
    Map<Integer, Tactic> tactics,
    String audienceTab
) {

    /**
     * Campaign-level rollup of delivery and performance metrics summed across every tactic.
     *
     * @param spend total media spend across all tactics
     * @param imps total impressions delivered
     * @param clicks total clicks recorded
     * @param completions total video/ad completions recorded
     * @param ctr blended click-through rate ({@code null} when not computable)
     * @param vcr blended video completion rate ({@code null} when not computable)
     */
    public record Totals(
        double spend,
        double imps,
        double clicks,
        double completions,
        Double ctr,
        Double vcr
    ) {
        // required
    }

    /**
     * Per-tactic delivery metrics paired with their planned targets and top-creative breakdown.
     *
     * @param name human-readable tactic name as shown in the report
     * @param channel media channel/format the tactic ran on (e.g. display, video)
     * @param lineItemId identifier of the source line item this tactic maps to
     * @param spend actual media spend for this tactic
     * @param imps actual impressions delivered by this tactic
     * @param clicks actual clicks recorded for this tactic
     * @param completions actual video/ad completions recorded for this tactic
     * @param ctr actual click-through rate ({@code null} when not computable)
     * @param vcr actual video completion rate ({@code null} when not computable)
     * @param weekdays count of weekday days the tactic was active ({@code null} if unknown)
     * @param weekends count of weekend days the tactic was active ({@code null} if unknown)
     * @param planSpend planned/budgeted spend target for the tactic ({@code null} if not planned)
     * @param planImps planned impressions target for the tactic ({@code null} if not planned)
     * @param planCtr planned click-through-rate target ({@code null} if not planned)
     * @param planVcr planned video-completion-rate target ({@code null} if not planned)
     * @param planMaxFreq planned maximum frequency cap for the tactic ({@code null} if not set)
     * @param topCreativeName name of the best-performing creative for this tactic ({@code null} if none)
     * @param topCreativeImps impressions delivered by the top creative ({@code null} if none)
     * @param topCreativeClicks clicks recorded by the top creative ({@code null} if none)
     */
    public record Tactic(
        String name,
        String channel,
        String lineItemId,
        double spend,
        double imps,
        double clicks,
        double completions,
        Double ctr,
        Double vcr,
        Integer weekdays,
        Integer weekends,
        Double planSpend,
        Double planImps,
        Double planCtr,
        Double planVcr,
        Double planMaxFreq,
        String topCreativeName,
        Double topCreativeImps,
        Double topCreativeClicks
    ) {
        // required
    }
}
