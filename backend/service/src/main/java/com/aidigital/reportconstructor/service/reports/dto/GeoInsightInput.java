package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic's context for the geo-insights batch: the hand-entered "Geo analysis" block Claude must
 * reason over, plus the tactic's name and KPI type so the copy talks about the metric the tactic is
 * actually led by (CTR for display/social, VCR for video/CTV, ACR for audio).
 *
 * @param tacticNum  the 1-based tactic number, used to route the reply back to the tactic's slide
 * @param tacticName the tactic's display name (e.g. {@code "CTV"}), as it appears on the deck
 * @param kpiType    the tactic's KPI type as the deck spells it (e.g. {@code "CTR"}), or blank when unknown
 * @param table      the tactic's geo summary and table rows; never empty when passed to Claude
 */
public record GeoInsightInput(int tacticNum, String tacticName, String kpiType, GeoTable table) {
}
