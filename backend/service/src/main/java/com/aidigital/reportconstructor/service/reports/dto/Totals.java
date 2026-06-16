package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Campaign-level rollup of delivery and performance metrics summed across every tactic.
 *
 * @param spend       total media spend across all tactics
 * @param imps        total impressions delivered
 * @param clicks      total clicks recorded
 * @param completions total video/ad completions recorded
 * @param ctr         blended click-through rate ({@code null} when not computable)
 * @param vcr         blended video completion rate ({@code null} when not computable)
 */
public record Totals(
		double spend,
		double imps,
		double clicks,
		double completions,
		Double ctr,
		Double vcr
) {
	// required
}
