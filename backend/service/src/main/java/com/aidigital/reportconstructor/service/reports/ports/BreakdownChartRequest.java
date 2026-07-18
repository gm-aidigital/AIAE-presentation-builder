package com.aidigital.reportconstructor.service.reports.ports;

import java.util.List;

/**
 * Inputs for {@link ChartProvider#buildBreakdownCharts(BreakdownChartRequest)} — the per-tactic
 * audience/device breakdown charts that are linked onto the duplicated breakdown slides after the deck
 * and its breakdown slides have been built.
 *
 * @param presentationId        id of the deck whose breakdown-slide charts get their own source sheet and
 *                              a fresh linked chart
 * @param campaignTitle         deck title, used to name the per-tactic chart-source spreadsheet copies and
 *                              their Drive folder
 * @param jobs                  the breakdown charts to render, one per (tactic, breakdown section) pair
 * @param userGoogleAccessToken optional signed-in user token; when present the source-sheet copies are
 *                              made under that user's Drive, matching where the deck was created
 */
public record BreakdownChartRequest(
		String presentationId,
		String campaignTitle,
		List<BreakdownChartJob> jobs,
		String userGoogleAccessToken) {
}
