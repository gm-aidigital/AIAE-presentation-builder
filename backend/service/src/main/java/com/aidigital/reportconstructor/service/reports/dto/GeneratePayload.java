package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Inbound request describing a single marketing report to generate, carrying the
 * free-text campaign brief plus the raw Google Sheets grids and BigQuery linkage
 * the engine resolves placeholders and builds charts from.
 *
 * @param brief           free-text campaign brief used as the prompt for Claude-generated narrative copy
 * @param reportType      report template code selecting which slide layout and sections to render
 * @param marketVolume    maximum addressable audience volume entered in the UI (DV360 estimate); parsed and rendered
 *                        compact (e.g. 74k, 1.2M) into {@code {{market volume}}}
 * @param sheetRows       raw Media Plan grid rows (label/value cells) searched for tactic spend, impressions and
 *                        benchmarks
 * @param adjRows         manual Adjustments grid rows whose labelled values override the corresponding Media Plan
 *                        values
 * @param audienceRows    raw audience-breakdown grid rows used to build audience tables and charts
 * @param estimatesRows   raw per-tactic estimates grid rows parsed into expected impression/spend figures
 * @param geoRows         every workbook tab flattened into one grid (each tab prefixed by a {@code "### TAB: <name>
 *                        ###"} marker row), scanned by Claude to extract the geo targeting wherever it lives
 * @param lineItemMapping mapping from media-plan tactics to their BigQuery line-item IDs, driving chart data queries
 * @param breakdownSelections per-tactic Step-3 breakdown toggle state (SHEET flow only); the "Breakdowns" tab clears
 *                        every section a tactic did not enable. {@code null} leaves the tab untouched; an empty list
 *                        clears every section for every tactic
 * @param estimateDaypartGender whether Claude may estimate the per-tactic dayparting (weekdays/weekends) and gender
 *                        split (male/female) when no manual value exists. {@code null} or {@code TRUE} keeps the AI
 *                        estimate (the default); {@code FALSE} forces the {@code {{tactic N weekdays|weekends|male|
 *                        female}}} tokens to an em-dash regardless of any manual value, because these metrics are not
 *                        always tracked reliably on the DSP side
 * @param bqSheetId       Google Sheet ID backing the BigQuery export; when blank, chart generation is skipped
 * @param dateFilter      user-confirmed raw-data date window (ALL or an inclusive RANGE); when {@code null} or ALL the
 *                        full date range present in the raw data ("Basic" tab) is used. This is the sole source of the
 *                        report flight window: it gates which delivery rows contribute and fills {@code {{flight_dates}}}.
 *                        The media plan is never consulted for dates.
 * @param sheetUrl        URL of a previously generated (and user-edited) Google Sheet; the sole input when the target is
 *                        {@code SLIDES_FROM_SHEET}, where the deck is filled from this sheet's values instead of the raw
 *                        grids. {@code null}/blank for the SLIDES and SHEET flows.
 * @param changeLog       optional free-text log of mid-flight changes/optimizations applied to the campaign (budget
 *                        shifts, audience weight changes, delayed launches). Written into the sheet's {@code {{change
 *                        log}}} placeholder in step 1 and fed to Claude as extra context so the narrative can attribute
 *                        results to the actual actions taken. {@code null}/blank when the user leaves it empty.
 */
public record GeneratePayload(
		String brief,
		String reportType,
		String marketVolume,
		List<List<String>> sheetRows,
		List<List<String>> adjRows,
		List<List<String>> audienceRows,
		List<List<String>> estimatesRows,
		List<List<String>> geoRows,
		List<LineItemMapping> lineItemMapping,
		List<BreakdownSelection> breakdownSelections,
		String bqSheetId,
		DateFilter dateFilter,
		String sheetUrl,
		String changeLog,
		Boolean estimateDaypartGender
) {

	/**
	 * Backward-compatible constructor for callers that predate the dayparting/gender toggle; keeps the AI
	 * estimate on by default so their behaviour is unchanged.
	 *
	 * @param brief               free-text campaign brief
	 * @param reportType          report template code
	 * @param marketVolume        maximum addressable audience volume entered in the UI
	 * @param sheetRows           raw Media Plan grid rows
	 * @param adjRows             manual Adjustments grid rows
	 * @param audienceRows        raw audience-breakdown grid rows
	 * @param estimatesRows       raw per-tactic estimates grid rows
	 * @param geoRows             every workbook tab flattened into one grid
	 * @param lineItemMapping     media-plan tactic to BigQuery line-item mapping
	 * @param breakdownSelections per-tactic Step-3 breakdown toggle state
	 * @param bqSheetId           Google Sheet ID backing the BigQuery export
	 * @param dateFilter          user-confirmed raw-data date window
	 * @param sheetUrl            URL of a previously generated sheet (slides-from-sheet only)
	 * @param changeLog           optional free-text log of mid-flight changes
	 */
	public GeneratePayload(
			String brief,
			String reportType,
			String marketVolume,
			List<List<String>> sheetRows,
			List<List<String>> adjRows,
			List<List<String>> audienceRows,
			List<List<String>> estimatesRows,
			List<List<String>> geoRows,
			List<LineItemMapping> lineItemMapping,
			List<BreakdownSelection> breakdownSelections,
			String bqSheetId,
			DateFilter dateFilter,
			String sheetUrl,
			String changeLog
	) {
		this(brief, reportType, marketVolume, sheetRows, adjRows, audienceRows, estimatesRows, geoRows,
				lineItemMapping, breakdownSelections, bqSheetId, dateFilter, sheetUrl, changeLog, Boolean.TRUE);
	}
}
