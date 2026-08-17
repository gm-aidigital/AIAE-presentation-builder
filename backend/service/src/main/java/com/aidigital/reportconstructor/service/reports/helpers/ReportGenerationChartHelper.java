package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;

import java.util.List;
import java.util.Map;

/**
 * Builds chart requests for a generated slide deck and trims unused tactic slides.
 */
public interface ReportGenerationChartHelper {

	/**
	 * Renders charts on the presentation when BQ/adjustments/mapping inputs are present.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param payload          generation request supplying sheet rows, mapping, and BQ sheet id
	 * @param data             aggregated campaign metrics used for chart date ranges
	 * @param flatReplacements resolved placeholder values keyed by token
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering, or skip/failure messages as strings
	 */
	List<String> buildCharts(
			String slideUrl,
			GeneratePayload payload,
			CampaignData data,
			Map<String, String> flatReplacements,
			String userGoogleToken
	);

	/**
	 * Renders the pacing charts for the "Slides from Sheet" flow, reading the daily/monthly pacing
	 * series straight from the (user-edited) sheet grid instead of BigQuery. The distribution charts
	 * are still driven by the resolved {@code {{tactic n imps}}} / {@code {{total imps}}} placeholders.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param grid             the filled sheet's first tab, as trimmed cell strings, carrying the pacing blocks
	 * @param flatReplacements resolved placeholder values read back from the sheet
	 * @param tacticCount      number of active tactics (clamped 1..28)
	 * @param userGoogleToken  OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering, or skip/failure messages as strings
	 */
	List<String> buildChartsFromSheet(
			String slideUrl,
			List<List<String>> grid,
			Map<String, String> flatReplacements,
			int tacticCount,
			String userGoogleToken
	);

	/**
	 * Removes unused tactic slides from the deck when the presentation id can be parsed.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param payload         generation request whose Media Plan drives tactic count
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken);

	/**
	 * Removes unused tactic slides from the deck for an explicit tactic count, for the
	 * "Slides from Sheet" flow where there is no Media Plan to derive the count from — the
	 * count comes from the sheet's filled tactic rows instead.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param tacticCount     number of active tactics (clamped 1..28)
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void trimUnusedTactics(String slideUrl, int tacticCount, String userGoogleToken);

	/**
	 * Builds the deck's main tactic slides from the template's single master tactic slide — one copy per
	 * active tactic, filled with that tactic's values. A no-op on a template with no configured master (the
	 * legacy 28-slot decks, which {@link #trimUnusedTactics} trims instead). Non-fatal: a failure is logged
	 * and the deck is delivered with whatever the master model managed to insert.
	 *
	 * <p>A failure is also returned as a job warning, not only logged. Every tactic on the sheet must get a
	 * main slide, so "no tactic slides in the deck" is a reportable outcome rather than a silent one — and the
	 * master template slide is deleted at the end of the run either way, which is what previously left a deck
	 * with the whole per-tactic block missing and no trace of why.
	 *
	 * <p>Must run before {@link #addBreakdownSlides}, which places each tactic's breakdown copies after that
	 * tactic's main slide.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param tacticCount      number of active tactics (clamped 1..28)
	 * @param flatReplacements resolved placeholder values keyed by token, used to fill each copy
	 * @param userGoogleToken  OAuth token for Google Slides API, or null when unavailable
	 * @return the warnings to attach to the job; empty when the slides were inserted (or the template has no
	 *         master and the step does not apply)
	 */
	List<String> addTacticSlides(
			String slideUrl, int tacticCount, Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Inserts the Step-3 per-tactic breakdown slides into the built deck for the "Slides from Sheet"
	 * flow. Resolves the request's breakdown selections to enabled sections, drops tactics beyond the
	 * active count, and delegates to the slides provider. Non-fatal: a failure is logged and the deck
	 * is delivered without the breakdown slides.
	 *
	 * @param slideUrl         URL of the generated Google Slides deck
	 * @param payload          generation request carrying the Step-3 breakdown selections
	 * @param tacticCount      number of active tactics (clamped 1..28); selections above this are ignored
	 * @param breakdownValues  renumbered token → value for the inserted slides; tokens absent from the map
	 *                         are only renumbered and would ship raw
	 * @param userGoogleToken  OAuth token for Google Slides API, or null when unavailable
	 */
	void addBreakdownSlides(
			String slideUrl, GeneratePayload payload, int tacticCount, Map<String, String> breakdownValues,
			String userGoogleToken);

	/**
	 * Links the per-tactic audience/device breakdown charts onto the breakdown slides inserted by
	 * {@link #addBreakdownSlides}. Reads each enabled tactic's device/age impressions back from the
	 * reviewed sheet, gives every chart its own copy of the section's source workbook filled with those
	 * impressions, and relinks the duplicated slide chart to it. Must run after {@code addBreakdownSlides}
	 * (the slides and their charts must already exist) and is non-fatal: a failure is logged and the deck
	 * is delivered with the breakdown slides' charts left as duplicated (empty) placeholders.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param payload         generation request carrying the Step-3 breakdown selections and sheet URL
	 * @param tacticCount     number of active tactics (clamped 1..28); selections above this are ignored
	 * @param flatReplacements resolved placeholder values, source of the campaign title used for copy names
	 * @param userGoogleToken OAuth token for Google APIs, or null when unavailable
	 * @return chart warnings collected during rendering (empty when every chart drew cleanly)
	 */
	List<String> buildBreakdownCharts(
			String slideUrl, GeneratePayload payload, int tacticCount, Map<String, String> flatReplacements,
			String userGoogleToken);

	/**
	 * Removes the breakdown and thoughts master template slides from the built deck. Runs unconditionally,
	 * independent of whether any breakdown slides were inserted: the masters are template scaffolding that
	 * must never ship, so they are cleaned even when Step 3 selected no breakdowns (in which case
	 * {@link #addBreakdownSlides} was a no-op that never touched them). Must run after
	 * {@link #buildBreakdownCharts} so the copies have finished duplicating from the masters. Non-fatal: a
	 * failure is logged and the deck is delivered with the masters still present.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void deleteMasterSlides(String slideUrl, String userGoogleToken);

	/**
	 * Removes the slides the given report type must never ship. The EOM deck is built from a copy of the
	 * EOC template and still carries EOC-only story slides (the frequency &amp; velocity play, the awareness
	 * / market-share slide), which are located by their title text and deleted. A no-op for every other
	 * report type. Non-fatal: a failure is logged and the deck is delivered with those slides still present.
	 *
	 * @param slideUrl        URL of the generated Google Slides deck
	 * @param reportType      report template code ({@code "EOC"}/{@code "EOM"}), may be null
	 * @param userGoogleToken OAuth token for Google Slides API, or null when unavailable
	 */
	void deleteReportTypeSlides(String slideUrl, String reportType, String userGoogleToken);
}
