package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Inbound request describing a single marketing report to generate, carrying the
 * free-text campaign brief plus the raw Google Sheets grids and BigQuery linkage
 * the engine resolves placeholders and builds charts from.
 *
 * @param brief free-text campaign brief used as the prompt for Claude-generated narrative copy
 * @param reportType report template code selecting which slide layout and sections to render
 * @param sheetRows raw Media Plan grid rows (label/value cells) searched for tactic spend, impressions and benchmarks
 * @param adjRows manual Adjustments grid rows whose labelled values override the corresponding Media Plan values
 * @param audienceRows raw audience-breakdown grid rows used to build audience tables and charts
 * @param estimatesRows raw per-tactic estimates grid rows parsed into expected impression/spend figures
 * @param geoRows raw geographic-performance grid rows summarized by Claude into the geo narrative
 * @param lineItemMapping mapping from media-plan tactics to their BigQuery line-item IDs, driving chart data queries
 * @param bqSheetId Google Sheet ID backing the BigQuery export; when blank, chart generation is skipped
 */
public record GeneratePayload(
    String brief,
    String reportType,
    List<List<String>> sheetRows,
    List<List<String>> adjRows,
    List<List<String>> audienceRows,
    List<List<String>> estimatesRows,
    List<List<String>> geoRows,
    List<LineItemMapping> lineItemMapping,
    String bqSheetId
) {
    /**
     * Links one media-plan tactic to its BigQuery line item so chart data can be queried per tactic.
     *
     * @param tactic display name of the tactic as it appears in the Media Plan
     * @param lineItemId BigQuery line-item identifier whose export rows feed this tactic's charts
     * @param tacticNum 1-based tactic slot number (1-7) used to position the tactic on the slide
     */
    public record LineItemMapping(String tactic, String lineItemId, Integer tacticNum) {
        // required
    }
}
