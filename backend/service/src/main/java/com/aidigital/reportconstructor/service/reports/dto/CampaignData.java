package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured single-pass aggregation of a campaign, consumed by the resolvers and
 * the Claude batch prompts. Numeric metrics use {@code double}; {@code null} boxed
 * values mean "no data".
 *
 * @param client       display name of the advertiser/client the campaign runs for
 * @param campaign     campaign name as shown in placeholders and report headings
 * @param geo          geographic targeting label (e.g. market or region) for the campaign
 * @param goal         stated campaign objective/goal description
 * @param flightDates  human-readable flight date range as rendered in the report
 * @param flightTs     parsed start/end boundaries of the flight window for date math
 * @param budget       formatted budget string for display in the report
 * @param primaryKpis  description of the campaign's primary KPIs
 * @param tacticsList  comma-/newline-separated summary listing of the tactics used
 * @param audienceAge  audience age-range targeting description
 * @param audienceSegs audience segment targeting description
 * @param totals        aggregated delivery and performance metrics across all tactics, scoped to the flight window
 * @param tactics       per-tactic data keyed by the tactic's ordinal index in the report, scoped to the flight window
 * @param reportPeriod  EOM-only reporting period, clamped into the flight window; {@code null} for EOC or an EOM
 *                      request with no period selected. When present, {@code periodTotals}/{@code periodTactics}
 *                      carry the actuals re-aggregated over this narrower window, and the pacing resolvers prorate
 *                      the (window-independent) plan figures on {@code tactics} by periodDays / flightDays
 * @param periodTotals  campaign totals re-aggregated over {@code reportPeriod}, or {@code null} when absent
 * @param periodTactics per-tactic data re-aggregated over {@code reportPeriod}, or {@code null} when absent
 * @param audienceTab  raw audience-tab source content used to build audience copy
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
		FlightDates reportPeriod,
		Totals periodTotals,
		Map<Integer, Tactic> periodTactics,
		String audienceTab
) {

	/**
	 * Backward-compatible constructor for callers that predate the EOM reporting-period fields; leaves them
	 * {@code null} (no period, plain EOC-equivalent behaviour) so their behaviour is unchanged.
	 *
	 * @param client       display name of the advertiser/client the campaign runs for
	 * @param campaign     campaign name as shown in placeholders and report headings
	 * @param geo          geographic targeting label (e.g. market or region) for the campaign
	 * @param goal         stated campaign objective/goal description
	 * @param flightDates  human-readable flight date range as rendered in the report
	 * @param flightTs     parsed start/end boundaries of the flight window for date math
	 * @param budget       formatted budget string for display in the report
	 * @param primaryKpis  description of the campaign's primary KPIs
	 * @param tacticsList  comma-/newline-separated summary listing of the tactics used
	 * @param audienceAge  audience age-range targeting description
	 * @param audienceSegs audience segment targeting description
	 * @param totals       aggregated delivery and performance metrics across all tactics
	 * @param tactics      per-tactic data keyed by the tactic's ordinal index in the report
	 * @param audienceTab  raw audience-tab source content used to build audience copy
	 */
	public CampaignData(
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
		this(client, campaign, geo, goal, flightDates, flightTs, budget, primaryKpis, tacticsList, audienceAge,
				audienceSegs, totals, tactics, null, null, null, audienceTab);
	}
}
