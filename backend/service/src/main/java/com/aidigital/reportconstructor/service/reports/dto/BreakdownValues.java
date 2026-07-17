package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * One breakdown section's contribution to the deck: the token values its slides are filled with, and the
 * warnings the user needs to see about them.
 *
 * <p>The warnings exist because a breakdown slide degrades silently by design — a bullet Claude did not
 * write ships as an empty box, which on the slide is indistinguishable from the user having filled nothing
 * in. Carrying the warning out to the "Report ready" card is what makes the difference visible without
 * reading the server log.
 *
 * @param values   renumbered token (e.g. {@code {{publisher_3.1}}}) → its final value; never null
 * @param warnings human-readable warnings for the report job, in the order they occurred; empty when the
 *                 section filled cleanly
 */
public record BreakdownValues(Map<String, String> values, List<String> warnings) {

	/**
	 * Returns the contribution of a section no tactic enabled: nothing to fill, nothing to warn about.
	 *
	 * @return an empty contribution
	 */
	public static BreakdownValues empty() {
		return new BreakdownValues(Map.of(), List.of());
	}
}
