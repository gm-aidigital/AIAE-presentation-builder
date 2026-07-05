package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Header row and columns located for one tactic's Daily pacing (or Monthly pacing) block when
 * reading a filled EOC workbook back, relative to its {@code "Daily pacing N"} /
 * {@code "Monthly pacing N"} anchor cell. The pacing writer overwrites the block's
 * {@code {{tactic n ...}}} marker cells with the data, so the surviving {@code Date} /
 * {@code Impressions} / {@code Amount} header labels — not the markers — anchor the read-back.
 * A column value of {@code -1} means that column could not be located within the search window.
 *
 * @param headerRow zero-based row carrying the {@code Date}/{@code Impressions}/{@code Amount} headers
 * @param dateCol   column carrying the {@code Date} header (data labels sit directly below it)
 * @param impsCol   column carrying the {@code Impressions} header, or {@code -1} when not found
 * @param metricCol column carrying the {@code Amount}/clicks/completions header, or {@code -1} when not found
 */
public record PacingHeader(
		int headerRow,
		int dateCol,
		int impsCol,
		int metricCol) {

}
