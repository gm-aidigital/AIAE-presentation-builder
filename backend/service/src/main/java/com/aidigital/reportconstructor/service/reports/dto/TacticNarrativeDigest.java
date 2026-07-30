package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's compact contribution to the Step-4 campaign-level results call. For a tactic that passed the
 * "> 2 breakdowns" gate this carries its Step-3 {@code thoughts}; for a tactic that did not, {@code thoughts}
 * is {@code null} and the campaign call falls back to the tactic's {@code overview} plus a short
 * {@code breakdownDigestLines} summary of whatever breakdown conclusions it does have. Keeping this per-tactic
 * digest small is deliberate: the campaign call reasons over conclusions, never raw grids.
 *
 * @param tacticNum            the 1-based tactic number this digest belongs to
 * @param tacticName           the tactic's display name (channel), or {@code null} when the sheet has none;
 *                             carried so the campaign copy can name the channel instead of "Tactic N"
 * @param overview             the tactic's {@code {{tactic n overview}}} narrative, or {@code null}
 * @param thoughts             the tactic's Step-3 thoughts when it qualified, otherwise {@code null}
 * @param breakdownDigestLines one short line per available breakdown conclusion, used as read-only context
 */
public record TacticNarrativeDigest(
		int tacticNum,
		String tacticName,
		String overview,
		List<String> thoughts,
		List<String> breakdownDigestLines
) {
}
