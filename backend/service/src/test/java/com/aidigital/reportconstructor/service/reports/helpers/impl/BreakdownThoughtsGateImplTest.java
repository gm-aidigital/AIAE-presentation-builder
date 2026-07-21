package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BreakdownThoughtsGateImplTest {

	private final BreakdownThoughtsGateImpl gate = new BreakdownThoughtsGateImpl();

	@Test
	void shouldQualifyOnlyWhenMoreThanTwoSectionsEnabledTest() {
		// Given: sets of two and three enabled sections
		Set<BreakdownType> two = Set.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO);
		Set<BreakdownType> three = Set.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO, BreakdownType.DEVICE);

		// When-Then: exactly two does not qualify, three does
		assertThat(gate.qualifies(two)).isFalse();
		assertThat(gate.qualifies(three)).isTrue();
	}

	@Test
	void shouldNotQualifyNullOrEmptySectionsTest() {
		// Given: null and empty section sets

		// When-Then: neither qualifies
		assertThat(gate.qualifies(null)).isFalse();
		assertThat(gate.qualifies(Set.of())).isFalse();
	}

	@Test
	void shouldSelectQualifyingTacticsPreservingOrderTest() {
		// Given: tactic 2 has three sections, tactic 1 has two, tactic 3 has four
		Map<Integer, Set<BreakdownType>> enabledByTactic = new LinkedHashMap<>();
		enabledByTactic.put(2, Set.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO, BreakdownType.DEVICE));
		enabledByTactic.put(1, Set.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO));
		enabledByTactic.put(3, Set.of(
				BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO, BreakdownType.DEVICE, BreakdownType.AUDIENCE));

		// When: qualifying tactics are selected
		Set<Integer> qualifying = gate.qualifyingTactics(enabledByTactic);

		// Then: only the > 2 tactics survive, in iteration order
		assertThat(qualifying).containsExactly(2, 3);
	}

	@Test
	void shouldReturnEmptyWhenMapNullTest() {
		// Given: no selections

		// When: null is gated
		Set<Integer> qualifying = gate.qualifyingTactics(null);

		// Then: the result is empty
		assertThat(qualifying).isEmpty();
	}
}
