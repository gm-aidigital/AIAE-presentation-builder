package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic's context for the audience-insights batch: the hand-entered "Audience analysis" block
 * Claude must reason over, plus the tactic's name so the copy talks about the right channel.
 *
 * <p>No KPI type is carried — unlike the geo batch — because the audience copy and its recommendation
 * are framed around the most effective age groups and segments, not the tactic's lead metric.
 *
 * @param tacticNum  the 1-based tactic number, used to route the reply back to the tactic's slide
 * @param tacticName the tactic's display name (e.g. {@code "CTV"}), as it appears on the deck
 * @param table      the tactic's audience stat tiles and sub-tables; never empty when passed to Claude
 */
public record AudienceInsightInput(int tacticNum, String tacticName, AudienceTable table) {
}
