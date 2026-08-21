package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingMetric;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PacingNarrativeAssemblerImplTest {

	private final PacingNarrativeAssemblerImpl assembler = new PacingNarrativeAssemblerImpl();

	/**
	 * Builds the placeholder map a one-tactic EOM run carries by the time the channel slide is filled: the
	 * channel's name, its KPI type and the two METRIC rows this test cares about.
	 *
	 * @return the placeholder map the narrative call is assembled from
	 */
	private Map<String, String> oneTacticMap() {
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{tactic 1}}", "CTV");
		flat.put("{{tactic 1 KPI type}}", "VCR");
		flat.put("{{tactic 1 planned imps}}", "1,575,000");
		flat.put("{{tactic 1 fact imps}}", "1,602,341");
		flat.put("{{tactic 1 imps pacing}}", "102%");
		flat.put("{{tactic 1 eoc planned imps}}", "6,300,000");
		flat.put("{{tactic 1 proj imps}}", "6,409,364");
		flat.put("{{tactic 1 planned budget}}", "$11,812.50");
		flat.put("{{tactic 1 fact budget}}", "$12,167.88");
		flat.put("{{tactic 1 budget pacing}}", "103%");
		flat.put("{{tactic 1 spend plan eoc}}", "$47,250");
		flat.put("{{tactic 1 spend proj}}", "$48,671");
		return flat;
	}

	@Test
	void toInputs_shouldCarryTheChannelAndItsPopulatedMetricRowsInSlideOrderTest() {
		// Given: a filled placeholder map
		Map<String, String> flat = oneTacticMap();

		// When:
		List<TacticPacingInput> inputs = assembler.toInputs(flat, 1);

		// Then: the channel is described by what the slide prints, and only its populated rows travel
		assertThat(inputs).hasSize(1);
		TacticPacingInput input = inputs.get(0);
		assertThat(input.tacticNum()).isEqualTo(1);
		assertThat(input.tacticName()).isEqualTo("CTV");
		assertThat(input.kpiType()).isEqualTo("VCR");
		assertThat(input.metrics()).extracting(TacticPacingMetric::label)
				.containsExactly("Impressions", "Spend");
		assertThat(input.metrics().get(0).vsGoal()).isEqualTo("102%");
		assertThat(input.metrics().get(1).eocGoal()).isEqualTo("$47,250");
	}

	@Test
	void toInputs_shouldSkipATacticWhoseTableCarriesNoFigureTest() {
		// Given: a second tactic whose every cell is dashed or blank
		Map<String, String> flat = oneTacticMap();
		flat.put("{{tactic 2}}", "Meta");
		flat.put("{{tactic 2 planned imps}}", "—");
		flat.put("{{tactic 2 fact imps}}", "");

		// When:
		List<TacticPacingInput> inputs = assembler.toInputs(flat, 2);

		// Then: only the tactic with figures is worth a call
		assertThat(inputs).extracting(TacticPacingInput::tacticNum).containsExactly(1);
	}

	@Test
	void write_shouldFillTheFourTokensFromTheReplyTest() {
		// Given: a reply for the one tactic
		Map<String, String> flat = oneTacticMap();

		// When:
		assembler.write(flat, 1, List.of(
				new TacticPacing(1, "Living-room inventory carried completion.", "Frequency is climbing.",
						"Capping frequency at 3.", "Hold CTV weight.")));

		// Then:
		assertThat(flat.get("{{what worked pacing 1}}")).isEqualTo("Living-room inventory carried completion.");
		assertThat(flat.get("{{watch outs pacing 1}}")).isEqualTo("Frequency is climbing.");
		assertThat(flat.get("{{actions pacing 1}}")).isEqualTo("Capping frequency at 3.");
		assertThat(flat.get("{{pacing 1 next month}}")).isEqualTo("Hold CTV weight.");
	}

	@Test
	void write_shouldDashTheTokensOfATacticWithNoReplyTest() {
		// Given: two tactics and a reply for the first only
		Map<String, String> flat = oneTacticMap();

		// When:
		assembler.write(flat, 2, List.of(new TacticPacing(1, "a", "b", "c", "d")));

		// Then: the second tactic dashes rather than shipping raw tokens
		assertThat(flat.get("{{what worked pacing 2}}")).isEqualTo("—");
		assertThat(flat.get("{{pacing 2 next month}}")).isEqualTo("—");
	}

	@Test
	void write_shouldKeepAValueTheUserAlreadySuppliedTest() {
		// Given: the workbook already carries the user's own wording for one token
		Map<String, String> flat = oneTacticMap();
		flat.put("{{actions pacing 1}}", "Our own line, hands off.");

		// When:
		assembler.write(flat, 1, List.of(new TacticPacing(1, "a", "b", "generated", "d")));

		// Then: the user's wording outranks the generated one
		assertThat(flat.get("{{actions pacing 1}}")).isEqualTo("Our own line, hands off.");
		assertThat(flat.get("{{what worked pacing 1}}")).isEqualTo("a");
	}

	@Test
	void write_shouldDashEveryTokenWhenThereIsNoReplyAtAllTest() {
		Map<String, String> flat = oneTacticMap();

		assembler.write(flat, 1, List.of());

		assertThat(flat.get("{{what worked pacing 1}}")).isEqualTo("—");
		assertThat(flat.get("{{watch outs pacing 1}}")).isEqualTo("—");
		assertThat(flat.get("{{actions pacing 1}}")).isEqualTo("—");
		assertThat(flat.get("{{pacing 1 next month}}")).isEqualTo("—");
	}
}
