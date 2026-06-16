package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured output of Claude Batch B (tactical): per-tactic
 * {@code male / female / weekdays / weekends} insights, keyed by 1-based tactic number.
 *
 * @param byTactic per-tactic insights keyed by the 1-based tactic number (may be null)
 */
public record ClaudeTactical(Map<Integer, TacticInsight> byTactic) {

	/**
	 * Returns the insight for the given 1-based tactic number, or null if absent or the map is unset.
	 *
	 * @param n 1-based tactic number to look up
	 * @return the matching {@link TacticInsight}, or null when {@code byTactic} is null or has no entry for {@code n}
	 */
	public TacticInsight get(int n) {
		return byTactic == null ? null : byTactic.get(n);
	}
}
