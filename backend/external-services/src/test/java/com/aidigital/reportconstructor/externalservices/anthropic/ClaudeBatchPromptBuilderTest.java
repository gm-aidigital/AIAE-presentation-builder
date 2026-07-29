package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
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

}
