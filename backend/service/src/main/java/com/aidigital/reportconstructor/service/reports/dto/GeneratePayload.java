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
 * @param geoRows         raw geographic-performance grid rows summarized by Claude into the geo narrative
 * @param lineItemMapping mapping from media-plan tactics to their BigQuery line-item IDs, driving chart data queries
 * @param bqSheetId       Google Sheet ID backing the BigQuery export; when blank, chart generation is skipped
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
		String bqSheetId
) {

}
