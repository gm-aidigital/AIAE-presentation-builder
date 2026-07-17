package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic's context for the device-insights batch: the hand-entered "Device breakdown" block
 * Claude must reason over, plus the tactic's name so the copy talks about the right channel.
 *
 * <p>No KPI type is carried — unlike the geo batch — because the device copy and its recommendation
 * are framed around the most and least effective devices, not the tactic's lead metric.
 *
 * @param tacticNum  the 1-based tactic number, used to route the reply back to the tactic's slide
 * @param tacticName the tactic's display name (e.g. {@code "CTV"}), as it appears on the deck
 * @param table      the tactic's device stat tiles and table rows; never empty when passed to Claude
 */
public record DeviceInsightInput(int tacticNum, String tacticName, DeviceTable table) {
}
