package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveTacticsHelperImplTest {

	private final EffectiveTacticsHelperImpl helper =
			new EffectiveTacticsHelperImpl(new MediaPlanTacticExtractorImpl(new TacticCatalog(),
					new SheetRowHelperImpl()));

	private final List<List<String>> plan = List.of(
			List.of("Media"),
			List.of("programmatic display"),
			List.of("programmatic audio"),
			List.of("programmatic ctv")
	);

	@Test
	void effectiveTacticsShouldReturnEveryPlanTacticWhenNothingWasMatchedTest() {
		// Given / When:
		List<PlanTactic> tactics = helper.effectiveTactics(plan, List.of());

		// Then:
		assertThat(tactics).extracting(PlanTactic::name)
				.containsExactly("programmatic display", "programmatic audio", "programmatic ctv");
		assertThat(helper.effectiveTacticCount(plan, null)).isEqualTo(3);
	}

	@Test
	void effectiveTacticsShouldFollowTheMappingWhenRowsWereExcludedTest() {
		// Given: the middle plan row was dropped, so the survivors arrive renumbered 1..2 while still
		// pointing at plan positions 1 and 3
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Programmatic CTV", "li-3", 2, null, null, null, 3));

		// When:
		List<PlanTactic> tactics = helper.effectiveTactics(plan, mapping);

		// Then:
		assertThat(tactics).extracting(PlanTactic::name)
				.containsExactly("programmatic display", "programmatic ctv");
		assertThat(helper.effectiveTacticCount(plan, mapping)).isEqualTo(2);
	}

	@Test
	void effectiveTacticsShouldOrderByReportSlotRatherThanPayloadOrderTest() {
		// Given: the same mapping delivered out of order
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic CTV", "li-3", 2, null, null, null, 3),
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1));

		// When:
		List<PlanTactic> tactics = helper.effectiveTactics(plan, mapping);

		// Then:
		assertThat(tactics).extracting(PlanTactic::name)
				.containsExactly("programmatic display", "programmatic ctv");
	}

	@Test
	void effectiveTacticsShouldFallBackToTheMappingNameWhenThePlanPositionIsGoneTest() {
		// Given: a mapping entry pointing past the end of the plan (plan re-read after matching)
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Hand Added Tactic", "li-9", 2, null, null, null, 42));

		// When:
		List<PlanTactic> tactics = helper.effectiveTactics(plan, mapping);

		// Then: the tactic is kept under the name the mapping carries rather than silently dropped
		assertThat(tactics).extracting(PlanTactic::name)
				.containsExactly("programmatic display", "Hand Added Tactic");
	}

	@Test
	void effectiveTacticsShouldTreatAMissingPlanNumberAsTheSlotNumberTest() {
		// Given: a payload written before row exclusion existed — no planTacticNum at all
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1),
				new LineItemMapping("Programmatic Audio", "li-2", 2));

		// When:
		List<PlanTactic> tactics = helper.effectiveTactics(plan, mapping);

		// Then:
		assertThat(tactics).extracting(PlanTactic::name)
				.containsExactly("programmatic display", "programmatic audio");
	}
}
