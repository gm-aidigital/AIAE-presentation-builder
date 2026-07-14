package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

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
	void buildBatchCPrompt_labelsAudioCompletionAsAcrTest() {
		// Given: a campaign whose single tactic is an audio format with an achieved completion rate
		Tactic audio = new Tactic(
				"Programmatic Audio", "Audio", null,
				1000.0, 50000.0, 0.0, 40000.0, null, 85.0, null, null,
				null, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "Audio", "25-44", "Podcast listeners",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, audio), null);
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);

		// When:
		Optional<String> prompt = builder.buildBatchCPrompt(data, "Drive audio reach.", frequencies);

		// Then: the tactic's completion figure is presented to Claude as ACR, not the video-specific VCR
		assertThat(prompt).isPresent();
		assertThat(prompt.get()).contains("ACR 85.00");
		assertThat(prompt.get()).doesNotContain("VCR 85.00");
	}
}
