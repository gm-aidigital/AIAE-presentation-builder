package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured single-pass aggregation of a campaign, consumed by the resolvers and
 * the Claude batch prompts. Numeric metrics use {@code double}; {@code null} boxed
 * values mean "no data".
 *
 * @param client              display name of the advertiser/client the campaign runs for
 * @param campaign            campaign name as shown in placeholders and report headings
 * @param geo                 geographic targeting label (e.g. market or region) for the campaign
 * @param goal                stated campaign objective/goal description
 * @param flightDates         human-readable flight date range as rendered in the report
 * @param flightTs            parsed start/end boundaries of the flight window for date math; for EOM
 *                            this is the currently selected Flight-dates window (campaign start through
 *                            the reporting cutoff), used both to gate actuals and to derive
 *                            {@code eomMonthNumber}
 * @param budget              formatted budget string for display in the report
 * @param primaryKpis         description of the campaign's primary KPIs
 * @param tacticsList         comma-/newline-separated summary listing of the tactics used
 * @param audienceAge         audience age-range targeting description
 * @param audienceSegs        audience segment targeting description
 * @param totals              aggregated delivery and performance metrics across all tactics
 * @param tactics              per-tactic data keyed by the tactic's ordinal index in the report
 * @param eomMonthNumber       EOM-only: 1-based index of the reporting month within the flight, derived
 *                            from the calendar months {@code flightTs} spans; {@code null} for EOC
 * @param eomFlightMonthsTotal EOM-only: total months the flight spans; the same {@code flightTs}-derived
 *                            figure as {@code eomMonthNumber} — this report always covers the whole
 *                            period its plan figures are measured against, so there is no separate
 *                            to-date/full-flight distinction; {@code null} for EOC
 * @param audienceTab         raw audience-tab source content used to build audience copy
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
		Integer eomMonthNumber,
		Integer eomFlightMonthsTotal,
		String audienceTab
) {

	/**
	 * Backward-compatible constructor for callers that predate the EOM month-cadence fields; leaves
	 * them {@code null} so their behaviour is unchanged.
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
				audienceSegs, totals, tactics, null, null, audienceTab);
	}
}
