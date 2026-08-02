package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.helpers.TacticConclusionAssembler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring bean implementation of {@link TacticConclusionAssembler}. Pure in-memory transformation of the
 * Step-2 conclusions into the Step-3 and Step-4 inputs; it reads no sheet and calls no external service.
 */
@Component
public class TacticConclusionAssemblerImpl implements TacticConclusionAssembler {

	/**
	 * Upper bound on the breakdown-digest lines carried for a tactic that has no Step-3 thoughts, so a tactic
	 * with several sections cannot bloat the campaign call. A non-qualifying tactic has at most two sections, so
	 * this only ever clamps a pathological case.
	 */
	private static final int MAX_DIGEST_LINES = 12;

	@Override
	public List<TacticThoughtsInput> toThoughtsInputs(
			List<TacticConclusion> conclusions, Map<Integer, String> tacticNames, Set<Integer> qualifyingTactics,
			BreakdownBullets bullets) {
		List<TacticThoughtsInput> inputs = new ArrayList<>();
		if (conclusions == null || qualifyingTactics == null) {
			return inputs;
		}
		Map<Integer, String> names = tacticNames == null ? Map.of() : tacticNames;
		for (TacticConclusion c : conclusions) {
			if (c == null || !qualifyingTactics.contains(c.tacticNum())) {
				continue;
			}
			inputs.add(new TacticThoughtsInput(
					c.tacticNum(),
					names.get(c.tacticNum()),
					c.overview(),
					section(bullets == null ? null : bullets.publisher(), c.tacticNum()),
					section(bullets == null ? null : bullets.creative(), c.tacticNum()),
					section(bullets == null ? null : bullets.geo(), c.tacticNum()),
					section(bullets == null ? null : bullets.audience(), c.tacticNum()),
					section(bullets == null ? null : bullets.device(), c.tacticNum())));
		}
		return inputs;
	}

	@Override
	public List<TacticNarrativeDigest> toCampaignDigests(
			List<TacticConclusion> conclusions, Map<Integer, String> tacticNames, List<TacticThoughts> thoughts,
			BreakdownBullets bullets) {
		List<TacticNarrativeDigest> digests = new ArrayList<>();
		if (conclusions == null) {
			return digests;
		}
		Map<Integer, String> names = tacticNames == null ? Map.of() : tacticNames;
		Map<Integer, List<String>> thoughtsByTactic = new LinkedHashMap<>();
		if (thoughts != null) {
			for (TacticThoughts t : thoughts) {
				if (t != null && t.thoughts() != null && !t.thoughts().isEmpty()) {
					thoughtsByTactic.putIfAbsent(t.tacticNum(), t.thoughts());
				}
			}
		}
		for (TacticConclusion c : conclusions) {
			if (c == null) {
				continue;
			}
			List<String> tacticThoughts = thoughtsByTactic.get(c.tacticNum());
			String name = names.get(c.tacticNum());
			if (tacticThoughts != null) {
				digests.add(new TacticNarrativeDigest(c.tacticNum(), name, c.overview(), tacticThoughts, List.of()));
			} else {
				digests.add(new TacticNarrativeDigest(
						c.tacticNum(), name, c.overview(), null, breakdownDigestLines(c.tacticNum(), bullets)));
			}
		}
		return digests;
	}

	/**
	 * Flattens one tactic's non-blank breakdown strings into a bounded digest, in section order (publishers,
	 * creative, geo, audience, device). Each string is already a self-contained slide sentence, so the campaign
	 * call reads conclusions rather than raw grids. Used only for tactics without Step-3 thoughts.
	 *
	 * @param tacticNum the tactic's 1-based number
	 * @param bullets   the per-section slide copy the breakdown calls produced, or {@code null} when none ran
	 * @return up to {@link #MAX_DIGEST_LINES} non-blank breakdown lines, in section order
	 */
	List<String> breakdownDigestLines(int tacticNum, BreakdownBullets bullets) {
		List<String> lines = new ArrayList<>();
		if (bullets == null) {
			return lines;
		}
		addNonBlank(lines, section(bullets.publisher(), tacticNum));
		addNonBlank(lines, section(bullets.creative(), tacticNum));
		addNonBlank(lines, section(bullets.geo(), tacticNum));
		addNonBlank(lines, section(bullets.audience(), tacticNum));
		addNonBlank(lines, section(bullets.device(), tacticNum));
		return lines.size() > MAX_DIGEST_LINES ? new ArrayList<>(lines.subList(0, MAX_DIGEST_LINES)) : lines;
	}

	/**
	 * Reads one section's strings for one tactic, tolerating a null map so a run with no breakdowns at all is
	 * handled the same way as a tactic whose section simply produced nothing.
	 *
	 * @param section   the section's tactic → strings map, possibly {@code null}
	 * @param tacticNum the tactic's 1-based number
	 * @return the tactic's strings for that section, or {@code null} when it has none
	 */
	List<String> section(Map<Integer, List<String>> section, int tacticNum) {
		return section == null ? null : section.get(tacticNum);
	}

	/**
	 * Appends every non-blank, trimmed entry of {@code source} to {@code target}, skipping a null source.
	 *
	 * @param target the accumulating digest lines
	 * @param source a section's bullet list, possibly {@code null} or holding blank entries
	 */
	void addNonBlank(List<String> target, List<String> source) {
		if (source == null) {
			return;
		}
		for (String value : source) {
			if (value != null && !value.isBlank()) {
				target.add(value.trim());
			}
		}
	}
}
