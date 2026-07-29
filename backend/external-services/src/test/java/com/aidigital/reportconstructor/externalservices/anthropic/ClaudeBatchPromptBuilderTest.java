package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusionInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

class ClaudeBatchPromptBuilderTest {

	private final ClaudeBatchPromptBuilder builder =
			new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt());

	@Test
	void completionRateLabel_audioIsAcrOthersAreVcrTest() {
		assertThat(builder.completionRateLabel("Programmatic Audio")).isEqualTo("ACR");
		assertThat(builder.completionRateLabel("Amazon Podcast Ads")).isEqualTo("ACR");
		assertThat(builder.completionRateLabel("Programmatic CTV")).isEqualTo("VCR");
		assertThat(builder.completionRateLabel(null)).isEqualTo("VCR");
	}

	@Test
	void shouldAskForPublisherObservationsAsFourNumberedSlotsWithTheRealCoverageShareTest() {
		// Given: one tactic whose 2 listed publishers carry 600k of its 1M impressions
		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 1_000_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		PublisherObservationInput input = new PublisherObservationInput(
				1, "CTV",
				List.of(new PublisherRow("Hulu", "400,000", "40%"), new PublisherRow("Roku", "200,000", "20%")),
				600_000, 1_000_000);

		// When:
		String prompt = builder.buildPublisherSectionPrompt(input, data, "Drive awareness.", 160).orElseThrow();

		// Then: the four observations are asked for as numbered slots, so each maps to one array position the
		// way every other section's prompt already does
		assertThat(prompt).contains(
				"1) VOLUME AND REACH", "2) AUDIENCE FIT",
				"3) PREMIUM AND BRAND SUITABILITY", "4) STEERING WEIGHT");

		// Then: the blacklisting claim and the complimentary framing are still asked for
		assertThat(prompt).contains("WE BLACKLISTED a large number of PUBLISHERS", "complimentary");

		// Then: coverage is a computed figure to cite, not a licence to estimate one
		assertThat(prompt).contains(
				"HEAD VS LONG TAIL: these 2 publishers carry 600,000 of the tactic's 1,000,000 impressions "
						+ "(60% of its delivery); the remaining 40% sits in a long tail");
		assertThat(prompt).doesNotContain("At most ~20%");
	}

	@Test
	void shouldLeaveThePublisherCoverageLineOutWhenTheTotalsCannotSupportItTest() {
		// Given: a tactic whose rows add up to more than the tactic delivered — a mistyped impressions cell
		PublisherObservationInput mistyped = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "9,000,000", "40%")), 9_000_000, 1_000_000);
		PublisherObservationInput unknown = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")), 400_000, 0);

		// When:
		String mistypedLine = builder.publisherCoverageLine(mistyped);
		String unknownLine = builder.publisherCoverageLine(unknown);

		// Then: neither states a share, so the copy is never told to cite arithmetic the sheet cannot back
		assertThat(mistypedLine).isEmpty();
		assertThat(unknownLine).isEmpty();
	}

	@Test
	void shouldHeadEachDigestWithItsTacticNameWhenPresentTest() {
		// Given: one digest with a display name and one without
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "Programmatic CTV", null, List.of("CTV held reach."), List.of()),
				new TacticNarrativeDigest(2, "  ", "Display overview.", null, List.of("Top publishers held.")));

		// When: the digest block is rendered
		String block = builder.perTacticDigestBlock(digests);

		// Then: the named tactic carries its channel in the header and the unnamed one stays bare
		assertThat(block).contains("Tactic 1 — Programmatic CTV:\n  - CTV held reach.");
		assertThat(block).contains("Tactic 2:\n  Overview: Display overview.");
	}

	@Test
	void shouldLabelAnAudioTacticsCompletionRateAsAcrInTheCreativeBlockTest() {
		// Given: the same creative block on an audio tactic and on a CTV one
		CreativeTable table = new CreativeTable(
				"6", "1.85%", "0.42%", "Spot 30s",
				List.of(new CreativeRow("Spot 30s", "400,000", "0.12%", "94.1%", "$2,400")));
		CreativeTakeawayInput audio = new CreativeTakeawayInput(1, "Programmatic Audio", "ACR", table);
		CreativeTakeawayInput ctv = new CreativeTakeawayInput(2, "Programmatic CTV", "VCR", table);

		// When:
		String audioBlock = builder.creativeContextBlock(audio);
		String ctvBlock = builder.creativeContextBlock(ctv);

		// Then: the audio tactic's stat labels and table header name the metric ACR, so the copy Claude writes
		// matches the deck and the sheet instead of calling an audio completion rate VCR
		assertThat(audioBlock).contains("Best CTR/ACR: 1.85%", "Avg CTR/ACR: 0.42%")
				.contains("Creative | Impressions | CTR | ACR | Spend")
				.doesNotContain("VCR");

		// Then: every other tactic still reads VCR
		assertThat(ctvBlock).contains("Best CTR/VCR: 1.85%", "Creative | Impressions | CTR | VCR | Spend");
	}

	@Test
	void shouldLicenceTheCreativeOptimisationStringToBeReconstructedTest() {
		// Given: one tactic with a creative table — the data a "Creative analysis" slide is written from, which
		// carries performance but no record of what was changed mid-flight
		Tactic display = new Tactic(
				"Display", "Display", null,
				5000.0, 1_000_000.0, 0.0, 1_000_000.0, null, null, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "Display", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, display), null);
		CreativeTakeawayInput input = new CreativeTakeawayInput(
				1, "Display", "CTR",
				new CreativeTable("6", "1.85%", "0.42%", "300x250_Summer",
						List.of(new CreativeRow("300x250_Summer", "400,000", "1.85%", "", "$2,400"))));

		// When:
		String prompt = builder.buildCreativeSectionPrompt(input, data, "Drive awareness.", 160, 160).orElseThrow();

		// Then: the fourth string is explicitly licenced to reconstruct the action, so the model stops choosing
		// between the ask and the surrounding "do not invent" rules by hedging the slot
		assertThat(prompt).contains(
				"The data carries NO change log, so this ONE string is EXPECTED to be reconstructed",
				"state it in past tense as something WE DID",
				"never say the change log is missing");

		// Then: the licence stops at the action — names and numbers still come from the table
		assertThat(prompt).contains("must come from the table (never invent a metric)");

		// Then: both budgets reach the model buffered to 80% of the 160 characters actually enforced
		assertThat(prompt).contains("at most 128 characters").doesNotContain("at most 160 characters");
	}

	@Test
	void shouldQuoteBufferedLimitsAndSendTheCampaignPromptUncachedTest() {
		// Given: a minimal campaign with one tactic digest and a frequency pair, so every limited field is asked for
		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Summer", "US", "Awareness", "Jun 1 - Aug 31",
				null, "$100,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		CampaignFrequencies frequencies = new CampaignFrequencies("5.2", "4.8", null, null);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "Programmatic CTV", "CTV overview.", List.of("CTV held reach."), List.of()));

		// When:
		String prompt = builder.buildCampaignResultsPrompt(data, "Brief.", frequencies, digests).orElseThrow();

		// Then: every character budget is quoted at 80% of the limit actually enforced on the reply
		assertThat(prompt).contains("≤304 chars");   // results overview, 380
		assertThat(prompt).contains("≤560 chars");   // thoughts total, 700
		assertThat(prompt).contains("≤22 chars");    // recommendation title, 28
		assertThat(prompt).contains("≤100 chars");   // recommendation text, 125
		assertThat(prompt).contains("≤144 chars");   // f_opportunity, 180
		assertThat(prompt).contains("≤112 chars");   // f_fact, 140
		assertThat(prompt).contains("≤256 chars");   // f_storytelling, 320
		assertThat(prompt).doesNotContain("380").doesNotContain("700");

		// And: the prompt is sent as one uncached block — its prefix is unique to this once-per-report call
		assertThat(prompt).doesNotContain(AnthropicMessagesClient.CACHE_BREAKPOINT);
	}

	@Test
	void shouldTellBothGeoPromptsTheTableIsOnlyTheTopMarketsTest() {
		// Given: one tactic whose geo table lists 2 markets while the stat above it says 14 ran — the row count
		// and the real footprint disagree, which is the case the copy must not read off the rows
		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 1_000_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		GeoInsightInput geo = new GeoInsightInput(
				1, "CTV", "VCR",
				new GeoTable("14", "New York", "97.1%",
						List.of(new GeoRow("New York", "400,000", "97.1%"),
								new GeoRow("Los Angeles", "200,000", "96.4%"))));

		// When: both paths that write geo copy build their prompt — the dedicated per-section call and the
		// combined conclusions call
		String sectionPrompt = builder.buildGeoSectionPrompt(geo, data, "Drive awareness.", 140).orElseThrow();
		String combinedPrompt = builder.buildTacticConclusionsPrompt(
						data, List.of(new TacticConclusionInput(1, null, null, geo, null, null)),
						"Drive awareness.", 160, 160, 160, 140, 200, 140, 200, 140)
				.orElseThrow();

		// Then: each one states that the table is a top list and names the stat that does carry total coverage,
		// so a two-row table never ships as "delivery ran across two markets"
		assertThat(sectionPrompt).contains(
				"The table lists only this tactic's TOP markets, NOT every market it ran in",
				"never state or imply a total market count from the number of rows",
				"The 'Markets activated' stat above the table is the only figure for total coverage");
		assertThat(combinedPrompt).contains(
				"The table lists only this tactic's TOP markets, NOT every market it ran in",
				"The 'Markets activated' stat above the table is the only figure for total coverage");

		// And: the stat the rule points at is actually in the data block, under the label the rule quotes
		assertThat(sectionPrompt).contains("Markets activated: 14");
	}

}
