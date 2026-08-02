package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the per-tactic bundles the restructured slides-from-sheet flow feeds to Claude, from the
 * in-memory Step-2 {@link TacticConclusion} overviews and the {@link BreakdownBullets} the per-section calls
 * produced — no sheet re-read. It bridges Step 2 to Step 3 (the per-tactic thoughts call) and Step 4 (the
 * campaign-level results call), so both consume the same copy the breakdown slides ship.
 */
public interface TacticConclusionAssembler {

	/**
	 * Builds the Step-3 thoughts inputs, one per tactic that passed the shared "&gt; 2 breakdowns" gate. A
	 * conclusion for a tactic that is not in {@code qualifyingTactics} is skipped, mirroring the slide that
	 * consumes the output, so a tactic never gets the thoughts call without the slide or vice versa.
	 *
	 * @param conclusions       the Step-2 per-tactic conclusions
	 * @param tacticNames       1-based tactic number → display name, so the thoughts talk about the right channel
	 * @param qualifyingTactics the tactic numbers that passed the gate (from {@link BreakdownThoughtsGate})
	 * @param bullets           the per-section slide copy the breakdown calls produced, keyed by tactic
	 * @return one {@link TacticThoughtsInput} per qualifying conclusion, in the conclusions' order
	 */
	List<TacticThoughtsInput> toThoughtsInputs(
			List<TacticConclusion> conclusions, Map<Integer, String> tacticNames, Set<Integer> qualifyingTactics,
			BreakdownBullets bullets);

	/**
	 * Builds the Step-4 campaign digests, one per tactic. A tactic that produced Step-3 {@code thoughts} carries
	 * them; a tactic that did not (it failed the gate, or the call failed) carries {@code null} thoughts plus its
	 * overview and a short digest of whatever breakdown conclusions it does have, so the campaign call always has
	 * something to reason over without ever seeing a raw grid.
	 *
	 * @param conclusions the Step-2 per-tactic conclusions
	 * @param tacticNames 1-based tactic number → display name, so the campaign copy names channels not numbers
	 * @param thoughts    the Step-3 thoughts that were produced (may be empty); matched to conclusions by number
	 * @param bullets     the per-section slide copy the breakdown calls produced, keyed by tactic
	 * @return one {@link TacticNarrativeDigest} per conclusion, in the conclusions' order
	 */
	List<TacticNarrativeDigest> toCampaignDigests(
			List<TacticConclusion> conclusions, Map<Integer, String> tacticNames, List<TacticThoughts> thoughts,
			BreakdownBullets bullets);
}
