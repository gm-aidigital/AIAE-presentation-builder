package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's context for the Step-3 "thoughts on tactic performance" call, assembled in-memory from the
 * tactic's Step-2 conclusions (no sheet re-read). It carries the tactic's overview and its enabled breakdown
 * conclusions so the model reasons only over copy already written for this tactic. Only tactics that pass the
 * shared "> 2 breakdowns" gate are ever built into this input, mirroring the slide that consumes the output.
 *
 * @param tacticNum        the 1-based tactic number this input belongs to
 * @param tacticName       the tactic's display name, so the thoughts talk about the right channel
 * @param overview         the tactic's {@code {{tactic n overview}}} narrative, or {@code null}
 * @param publisherBullets the tactic's "Top Publishers" bullets, or {@code null} when the section is off
 * @param creativeBullets  the tactic's "Creative analysis" bullets, or {@code null} when the section is off
 * @param geoBullets       the tactic's "Geo analysis" strings, or {@code null} when the section is off
 * @param audienceFields   the tactic's "Audience analysis" strings, or {@code null} when the section is off
 * @param deviceFields     the tactic's "Device breakdown" strings, or {@code null} when the section is off
 */
public record TacticThoughtsInput(
		int tacticNum,
		String tacticName,
		String overview,
		List<String> publisherBullets,
		List<String> creativeBullets,
		List<String> geoBullets,
		List<String> audienceFields,
		List<String> deviceFields
) {
}
