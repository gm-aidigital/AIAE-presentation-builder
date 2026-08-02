package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownBullets;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TacticConclusionAssemblerImplTest {

	private final TacticConclusionAssemblerImpl assembler = new TacticConclusionAssemblerImpl();

	@Test
	void shouldBuildThoughtsInputsOnlyForQualifyingTacticsTest() {
		// Given: conclusions for tactics 1 and 2, names for both, per-section bullets, only tactic 2 qualifies
		TacticConclusion one = new TacticConclusion(1, "Overview 1");
		TacticConclusion two = new TacticConclusion(2, "Overview 2");
		Map<Integer, String> names = Map.of(1, "CTV", 2, "Display");
		BreakdownBullets bullets = new BreakdownBullets(
				Map.of(1, List.of("pub 1"), 2, List.of("pub 2")),
				Map.of(2, List.of("cre 2")),
				Map.of(2, List.of("geo 2")),
				Map.of(),
				Map.of());

		// When: thoughts inputs are assembled for the qualifying set {2}
		List<TacticThoughtsInput> inputs =
				assembler.toThoughtsInputs(List.of(one, two), names, Set.of(2), bullets);

		// Then: only tactic 2's input is produced, carrying its name, overview and its own section bullets
		assertThat(inputs).hasSize(1);
		TacticThoughtsInput input = inputs.getFirst();
		assertThat(input.tacticNum()).isEqualTo(2);
		assertThat(input.tacticName()).isEqualTo("Display");
		assertThat(input.overview()).isEqualTo("Overview 2");
		assertThat(input.publisherBullets()).containsExactly("pub 2");
		assertThat(input.geoBullets()).containsExactly("geo 2");
		assertThat(input.deviceFields()).isNull();
	}

	@Test
	void shouldReturnEmptyThoughtsInputsWhenConclusionsNullTest() {
		// Given: no conclusions and no breakdown bullets
		BreakdownBullets bullets = new BreakdownBullets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

		// When: thoughts inputs are assembled from null
		List<TacticThoughtsInput> inputs = assembler.toThoughtsInputs(null, Map.of(), Set.of(1), bullets);

		// Then: the result is empty
		assertThat(inputs).isEmpty();
	}

	@Test
	void shouldCarryStep3ThoughtsInDigestWhenPresentTest() {
		// Given: a conclusion for tactic 3, its section bullets and Step-3 thoughts for it
		TacticConclusion three = new TacticConclusion(3, "Overview 3");
		TacticThoughts thoughts = new TacticThoughts(3, List.of("thought a", "thought b"));
		BreakdownBullets bullets = new BreakdownBullets(
				Map.of(3, List.of("pub 3")), Map.of(), Map.of(3, List.of("geo 3")), Map.of(), Map.of());

		// When: campaign digests are assembled
		List<TacticNarrativeDigest> digests =
				assembler.toCampaignDigests(List.of(three), Map.of(3, "CTV"), List.of(thoughts), bullets);

		// Then: the digest carries the thoughts and no breakdown fallback lines
		assertThat(digests).hasSize(1);
		TacticNarrativeDigest digest = digests.getFirst();
		assertThat(digest.tacticNum()).isEqualTo(3);
		assertThat(digest.tacticName()).isEqualTo("CTV");
		assertThat(digest.overview()).isEqualTo("Overview 3");
		assertThat(digest.thoughts()).containsExactly("thought a", "thought b");
		assertThat(digest.breakdownDigestLines()).isEmpty();
	}

	@Test
	void shouldFallBackToBreakdownDigestLinesWhenNoThoughtsTest() {
		// Given: a conclusion whose sections carry blanks and nulls, with no Step-3 thoughts
		TacticConclusion four = new TacticConclusion(4, "Overview 4");
		BreakdownBullets bullets = new BreakdownBullets(
				Map.of(4, Arrays.asList("pub a", "  ", null)),
				Map.of(),
				Map.of(4, List.of("geo a")),
				Map.of(),
				Map.of(4, List.of("dev a")));

		// When: campaign digests are assembled with an empty thoughts list
		List<TacticNarrativeDigest> digests =
				assembler.toCampaignDigests(List.of(four), Map.of(), List.of(), bullets);

		// Then: thoughts are null and the digest flattens non-blank section lines in section order
		assertThat(digests).hasSize(1);
		TacticNarrativeDigest digest = digests.getFirst();
		assertThat(digest.tacticName()).isNull();
		assertThat(digest.thoughts()).isNull();
		assertThat(digest.breakdownDigestLines()).containsExactly("pub a", "geo a", "dev a");
	}

	@Test
	void shouldFallBackToAnEmptyDigestWhenNoBreakdownBulletsRanTest() {
		// Given: a conclusion with no Step-3 thoughts and no breakdown bullets at all
		TacticConclusion five = new TacticConclusion(5, "Overview 5");

		// When: campaign digests are assembled with a null bullets bundle
		List<TacticNarrativeDigest> digests =
				assembler.toCampaignDigests(List.of(five), Map.of(), List.of(), null);

		// Then: the digest still carries the overview and simply has no breakdown lines
		assertThat(digests).hasSize(1);
		assertThat(digests.getFirst().overview()).isEqualTo("Overview 5");
		assertThat(digests.getFirst().breakdownDigestLines()).isEmpty();
	}
}
