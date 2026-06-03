package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured single-pass aggregation of a campaign, mirroring the array returned
 * by PHP {@code collectCampaignData()}. Consumed by the resolvers and the Claude
 * batch prompts. Numeric metrics use {@code double}; {@code null} boxed values
 * mean "no data" (PHP {@code null}).
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

    public record Totals(
        double spend,
        double imps,
        double clicks,
        double completions,
        Double ctr,
        Double vcr
    ) {}

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
    ) {}
}
