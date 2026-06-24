package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A single optimization recommendation entry for the "Optimization Recommendations" slide,
 * pairing a short actionable headline with its supporting one-line explanation.
 *
 * @param title short actionable headline of the recommendation (≤30 chars)
 * @param text  supporting explanation of how the recommendation advances the campaign goal (≤130 chars)
 */
public record Recommendation(String title, String text) {
	// required
}
