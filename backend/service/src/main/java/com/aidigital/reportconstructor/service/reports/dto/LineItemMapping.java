package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Links one media-plan tactic to its BigQuery line item so chart data can be queried per tactic.
 *
 * @param tactic     display name of the tactic as it appears in the Media Plan
 * @param lineItemId BigQuery line-item identifier whose export rows feed this tactic's charts
 * @param tacticNum  1-based tactic slot number (1-7) used to position the tactic on the slide
 */
public record LineItemMapping(String tactic, String lineItemId, Integer tacticNum) {
	// required
}
