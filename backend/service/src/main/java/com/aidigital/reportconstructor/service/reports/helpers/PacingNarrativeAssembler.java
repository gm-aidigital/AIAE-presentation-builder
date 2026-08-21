package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;

import java.util.List;
import java.util.Map;

/**
 * Assembles the input of the channel-slide pacing narrative call from the resolved placeholder map, and
 * writes the replies back onto it.
 *
 * <p>Both directions go through the placeholder map on purpose: it is the one place that holds the figures
 * the deck will actually print, so the narrative is generated from — and lands beside — the same numbers
 * the reader sees. End-of-month only; no EOC slide carries these tokens.
 */
public interface PacingNarrativeAssembler {

	/**
	 * Builds one input per tactic from the resolved placeholder map: the channel's name, the KPI it is
	 * judged on, and its METRIC table as the slide prints it.
	 *
	 * @param flat        the resolved placeholder map, read only
	 * @param tacticCount number of real tactics in the campaign
	 * @return one input per tactic in ascending tactic order, skipping tactics whose table is empty
	 */
	List<TacticPacingInput> toInputs(Map<String, String> flat, int tacticCount);

	/**
	 * Writes the narrative tokens for every tactic: the reply's copy where there is one, a dash where
	 * there is not, and whatever the map already carried where the user supplied it themselves.
	 *
	 * @param flat        the placeholder map to fill, mutated in place
	 * @param tacticCount number of real tactics in the campaign
	 * @param narratives  the replies, in any order; a tactic absent from the list is dashed
	 */
	void write(Map<String, String> flat, int tacticCount, List<TacticPacing> narratives);
}
