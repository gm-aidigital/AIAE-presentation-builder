package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BreakdownSelectionResolverImplTest {

	private final BreakdownSelectionResolverImpl resolver = new BreakdownSelectionResolverImpl();

	@Test
	void shouldReduceSelectionsToEnabledSectionsDroppingUnknownCodesTest() {
		// Given: tactic 1 enables Top Publishers + Geo plus an unknown code; tactic 2 enables nothing
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("tp", "geo", "bogus")),
				new BreakdownSelection(2, List.of()));

		// When: the selections are resolved
		Map<Integer, Set<BreakdownType>> resolved = resolver.resolve(selections);

		// Then: unknown codes are dropped and an empty selection maps to an empty set
		assertThat(resolved)
				.containsEntry(1, Set.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.GEO))
				.containsEntry(2, Set.of());
	}

	@Test
	void shouldReturnEmptyMapWhenSelectionsNullTest() {
		// Given: no selections

		// When: null is resolved
		Map<Integer, Set<BreakdownType>> resolved = resolver.resolve(null);

		// Then: the result is empty
		assertThat(resolved).isEmpty();
	}

	@Test
	void shouldSkipEntriesWithNullTacticNumberTest() {
		// Given: one selection with a null tactic number and one valid selection
		List<BreakdownSelection> selections = Arrays.asList(
				new BreakdownSelection(null, List.of("tp")),
				new BreakdownSelection(3, List.of("dev")));

		// When: the selections are resolved
		Map<Integer, Set<BreakdownType>> resolved = resolver.resolve(selections);

		// Then: only the valid entry survives
		assertThat(resolved).containsOnlyKeys(3);
		assertThat(resolved.get(3)).containsExactly(BreakdownType.DEVICE);
	}
}
