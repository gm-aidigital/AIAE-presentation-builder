package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;

import java.util.Map;
import java.util.Set;

/**
 * The single "&gt; 2 breakdowns" gate shared by the two features that must agree on it: inserting the
 * per-tactic "Thoughts on tactic performance" slide (slide-duplication step) and running the Step-3
 * per-tactic thoughts Claude call (orchestration step). Centralising the threshold here keeps the slide and
 * the copy in lock-step — a tactic can never get the slide without the copy, or the copy without the slide.
 */
public interface BreakdownThoughtsGate {

	/**
	 * Decides whether a tactic with the given enabled breakdown sections qualifies for its thoughts slide and
	 * its Step-3 thoughts call — true only when strictly more than two sections are enabled.
	 *
	 * @param enabledSections the tactic's enabled breakdown sections, possibly {@code null} or empty
	 * @return {@code true} when more than two sections are enabled, otherwise {@code false}
	 */
	boolean qualifies(Set<BreakdownType> enabledSections);

	/**
	 * Reduces a resolved {@code tactic number → enabled sections} map to the set of tactic numbers that pass
	 * {@link #qualifies(Set)}, in the map's iteration order.
	 *
	 * @param enabledByTactic 1-based tactic number → enabled breakdown sections, possibly {@code null}
	 * @return the qualifying tactic numbers (empty when {@code enabledByTactic} is null or none qualify)
	 */
	Set<Integer> qualifyingTactics(Map<Integer, Set<BreakdownType>> enabledByTactic);
}
