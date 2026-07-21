package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownThoughtsGate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spring bean implementation of {@link BreakdownThoughtsGate}. The threshold lives here as a single constant
 * so the slide-insertion and Step-3 call sites can never drift apart on what "&gt; 2 breakdowns" means.
 */
@Component
public class BreakdownThoughtsGateImpl implements BreakdownThoughtsGate {

	/** A tactic qualifies only when it has strictly more than this many breakdown sections enabled. */
	private static final int BREAKDOWN_THRESHOLD = 2;

	@Override
	public boolean qualifies(Set<BreakdownType> enabledSections) {
		return enabledSections != null && enabledSections.size() > BREAKDOWN_THRESHOLD;
	}

	@Override
	public Set<Integer> qualifyingTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> qualifying = new LinkedHashSet<>();
		if (enabledByTactic == null) {
			return qualifying;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && qualifies(enabled)) {
				qualifying.add(tacticNum);
			}
		});
		return qualifying;
	}
}
