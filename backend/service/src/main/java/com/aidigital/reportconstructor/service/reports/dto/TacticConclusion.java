package com.aidigital.reportconstructor.service.reports.dto;

/**
 * The Step-2 per-tactic conclusion produced for one tactic: its narrative overview. Breakdown-section slide
 * copy does not travel here — each section is written by its own dedicated per-tactic call and reaches the
 * downstream steps as {@link BreakdownBullets}.
 *
 * @param tacticNum the 1-based tactic number this conclusion belongs to
 * @param overview  the {@code {{tactic n overview}}} narrative, length-capped, or {@code null}
 */
public record TacticConclusion(
		int tacticNum,
		String overview
) {
}
