package com.aidigital.reportconstructor.service.reports.ports;

import java.util.List;

/**
 * Abstraction over per-tactic chart generation. The real provider copies the helper
 * chart-template spreadsheets, writes the pivoted actuals, applies the saved
 * chart spec and swaps the placeholder charts on the deck slides for live
 * linked charts; the stub provider is a no-op for offline demos.
 *
 * <p>Bean selection mirrors {@link SlidesProvider}: when
 * {@code GOOGLE_SERVICE_ACCOUNT_JSON} is present the real provider wins via
 * {@code @Primary}, otherwise the stub is the only candidate.
 */
public interface ChartProvider {

	/**
	 * @return true when the provider is talking to the real Google APIs.
	 */
	boolean isLive();

	/**
	 * Builds the three charts (daily pacing, monthly distribution, weighted
	 * impression contribution) for every active tactic. Per-chart failures are
	 * collected and returned without aborting the rest of the deck.
	 *
	 * @param request the chart-generation inputs
	 * @return human-readable error strings (empty when everything succeeded)
	 */
	List<String> buildCharts(ChartRequest request);
}
