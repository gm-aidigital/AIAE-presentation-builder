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
 * @param eomMonthNumber       EOM-only: purely informational calendar-month count {@code flightTs} spans
 *                            (an EOM report always covers a single reporting month); {@code null} for EOC
 * @param eomFlightMonthsTotal EOM-only: the same figure as {@code eomMonthNumber} — kept as a separate
 *                            field for the {@code {{eom_flight_months_total}}} token, but plays no part in
 *                            any plan math: the monthly budget entered while matching already is the
 *                            spend target for the reporting month; {@code null} for EOC
 * @param campaignFlightDates  EOM-only: the whole booked flight as a formatted range, read off the media
 *                            plan's flight-date columns — this is what {@code {{flight_dates}}} shows, while
 *                            {@code flightDates} above stays the reporting window the user selected and keeps
 *                            feeding the prompts; {@code null} for EOC
 * @param campaignMonthNumber  EOM-only: the reporting month's 1-based position inside the whole booked
 *                            flight (e.g. {@code 2} for a November report on an October–December flight);
 *                            unlike {@code eomMonthNumber} this counts against the media plan's flight, not
 *                            against the selected reporting window; {@code null} for EOC
 * @param campaignMonthsTotal  EOM-only: the number of calendar months the whole booked flight spans, taken
 *                            from the media plan's flight-date columns and falling back to the raw-data
 *                            range; {@code null} for EOC
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
		String campaignFlightDates,
		Integer campaignMonthNumber,
		Integer campaignMonthsTotal,
		String audienceTab
) {

	/**
	 * Backward-compatible constructor for callers that predate the campaign-flight cadence fields; leaves
	 * them {@code null} so their behaviour is unchanged.
	 *
	 * @param client               display name of the advertiser/client the campaign runs for
	 * @param campaign             campaign name as shown in placeholders and report headings
	 * @param geo                  geographic targeting label (e.g. market or region) for the campaign
	 * @param goal                 stated campaign objective/goal description
	 * @param flightDates          human-readable flight date range as rendered in the report
	 * @param flightTs             parsed start/end boundaries of the flight window for date math
	 * @param budget               formatted budget string for display in the report
	 * @param primaryKpis          description of the campaign's primary KPIs
	 * @param tacticsList          comma-/newline-separated summary listing of the tactics used
	 * @param audienceAge          audience age-range targeting description
	 * @param audienceSegs         audience segment targeting description
	 * @param totals               aggregated delivery and performance metrics across all tactics
	 * @param tactics              per-tactic data keyed by the tactic's ordinal index in the report
	 * @param eomMonthNumber       EOM-only calendar-month count the reporting window spans
	 * @param eomFlightMonthsTotal EOM-only twin of {@code eomMonthNumber}
	 * @param audienceTab          raw audience-tab source content used to build audience copy
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
			Integer eomMonthNumber,
			Integer eomFlightMonthsTotal,
			String audienceTab
	) {
		this(client, campaign, geo, goal, flightDates, flightTs, budget, primaryKpis, tacticsList, audienceAge,
				audienceSegs, totals, tactics, eomMonthNumber, eomFlightMonthsTotal, null, null, null, audienceTab);
	}

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
				audienceSegs, totals, tactics, null, null, null, null, null, audienceTab);
	}
}
