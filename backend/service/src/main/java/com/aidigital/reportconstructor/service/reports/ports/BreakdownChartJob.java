package com.aidigital.reportconstructor.service.reports.ports;

import java.util.List;

/**
 * One per-tactic breakdown chart to render: which breakdown section and tactic it belongs to, and the
 * impression slices that feed it.
 *
 * <p>The target slide and its embedded chart are not named here: the real provider derives the
 * duplicated breakdown slide's deterministic object id from {@code breakdownCode} + {@code tacticNum}
 * (the same scheme {@code addBreakdownSlides} used to create it) and finds the linked chart element on
 * that slide. The source-template spreadsheet id and the chart id within it are provider configuration
 * keyed by {@code breakdownCode}, so they stay out of the service layer.
 *
 * @param breakdownCode the breakdown section's wire code — only {@code "aud"} and {@code "dev"} carry a
 *                      chart; other codes are ignored by the provider
 * @param tacticNum     the 1-based tactic number whose breakdown slide this chart lands on
 * @param slices        the chart's category slices (label &rarr; impressions), in sheet order; a job with
 *                      no positive slice is skipped by the caller so no empty chart is drawn
 */
public record BreakdownChartJob(String breakdownCode, int tacticNum, List<BreakdownChartSlice> slices) {
}
