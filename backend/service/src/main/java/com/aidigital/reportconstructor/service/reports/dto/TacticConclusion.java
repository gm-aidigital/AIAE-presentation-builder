package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The Step-2 combined per-tactic conclusions produced for one tactic: its narrative overview plus the
 * bullets for each breakdown section that was enabled. A section that was off (or that produced no usable
 * reply) is carried as {@code null} rather than an empty list, so a caller can tell "not requested" apart
 * from "requested but blank". The section bullet lists mirror the fixed slide order and per-field counts of
 * the existing per-section batches, so they feed the same breakdown slide tokens unchanged.
 *
 * @param tacticNum        the 1-based tactic number this conclusion belongs to
 * @param overview         the {@code {{tactic n overview}}} narrative, length-capped, or {@code null}
 * @param publisherBullets the four "Top Publishers" bullets, or {@code null} when the section is off/blank
 * @param creativeBullets  the four "Creative analysis" bullets, or {@code null} when the section is off/blank
 * @param geoBullets       the five "Geo analysis" strings (four insights then the recommendation), or {@code null}
 * @param audienceFields   the four "Audience analysis" strings (takeaway, worked, flag, reco), or {@code null}
 * @param deviceFields     the four "Device breakdown" strings (takeaway, worked, flag, reco), or {@code null}
 */
public record TacticConclusion(
		int tacticNum,
		String overview,
		List<String> publisherBullets,
		List<String> creativeBullets,
		List<String> geoBullets,
		List<String> audienceFields,
		List<String> deviceFields
) {
}
