package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The data-reading half of one breakdown section, split out so the restructured slides-from-sheet flow can
 * gather every section's per-tactic Claude inputs first and then make that section's per-tactic Claude
 * call, instead of each section calling Claude on its own. The section's slide tokens that need no model
 * (the tables and stat tiles) are already filled in {@link #dataValues()}; the Claude-written tokens are
 * filled later from that call's result.
 *
 * @param <T>         the section's per-tactic Claude input type (e.g. {@code PublisherObservationInput})
 * @param tactics     every tactic that enabled this section (used to write every Claude token, blank included)
 * @param inputs      per-tactic Claude input, only for tactics whose data block is non-empty (what gets sent)
 * @param dataValues  the section's data-only slide tokens, already resolved (tables, stat tiles)
 * @param warnings    data-read warnings for the report job, in order; empty when the section read cleanly
 */
public record BreakdownSectionInputs<T>(
		Set<Integer> tactics,
		Map<Integer, T> inputs,
		Map<String, String> dataValues,
		List<String> warnings
) {
}
