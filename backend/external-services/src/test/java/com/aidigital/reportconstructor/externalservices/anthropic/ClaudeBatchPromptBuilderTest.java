package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.junit.jupiter.api.Test;

import java.util.List;
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

	@Test
	void buildCreativeTakeawaysPrompt_quotesBothBudgetsAtEightyPercentOfTheSlidesRealOnesTest() {
		// Given: a tactic whose creative block the user filled in
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s",
						List.of(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"))));

		// When: asked with the slide's real budgets — 100 for the reads, 140 for the recommendation
		String prompt = builder.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140).orElseThrow();

		// Then: Claude is asked for the smaller numbers, leaving the truncation safety net headroom
		assertThat(prompt).contains("at most 80 characters");
		assertThat(prompt).contains("At most 112 characters");

		// Then: the tactic's data and lead KPI reach the prompt
		assertThat(prompt).contains("tactic_1 — CTV (lead KPI: VCR)");
		assertThat(prompt).contains("Hero 15s | 1,200,000 | 0.58% | 82.9% | $4,800");
		assertThat(prompt).contains("Creatives live: 12");
	}

	@Test
	void buildCreativeTakeawaysPrompt_omitsStatTilesTheUserLeftBlankTest() {
		// Given: a block whose stat tiles are blank, so a label with no value would read to Claude as a zero
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "Display", "",
				new CreativeTable("", "", "", "",
						List.of(new CreativeRow("Banner 300x250", "500,000", "0.11%", "—", "$900"))));

		// When:
		String prompt = builder.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140).orElseThrow();

		// Then: the blank stats are absent entirely rather than sent as empty labels
		assertThat(prompt).doesNotContain("Creatives live:");
		assertThat(prompt).doesNotContain("Top creative:");

		// Then: a blank KPI type does not leave a dangling "(lead KPI: )" on the tactic line
		assertThat(prompt).contains("tactic_1 — Display\n");
		assertThat(prompt).doesNotContain("lead KPI");
	}

	@Test
	void buildCreativeTakeawaysPrompt_carriesOnlyTheTacticsCreativeDataTest() {
		// Given: two tactics' creative blocks, sitting in the same chunk
		CreativeTakeawayInput ctv = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s", List.of(
						new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"),
						new CreativeRow("Cutdown 6s", "600,000", "0.31%", "71.2%", "$2,100"))));
		CreativeTakeawayInput display = new CreativeTakeawayInput(2, "Display", "CTR",
				new CreativeTable("4", "0.11", "0.08", "Banner 300x250", List.of(
						new CreativeRow("Banner 300x250", "500,000", "0.11%", "—", "$900"))));

		// When:
		String prompt = builder.buildCreativeTakeawaysPrompt(List.of(ctv, display), "brief", 100, 140).orElseThrow();

		// Then: the data section carries each tactic's creatives and nothing else — no publisher, geo,
		// audience or device rows can reach a prompt built purely from the creative blocks
		String data = prompt.substring(prompt.indexOf("=== CREATIVE DATA ==="));
		assertThat(data).contains("Hero 15s | 1,200,000 | 0.58% | 82.9% | $4,800");
		assertThat(data).contains("Cutdown 6s | 600,000 | 0.31% | 71.2% | $2,100");
		assertThat(data).contains("Banner 300x250 | 500,000 | 0.11% | — | $900");
		assertThat(data).contains("Creative | Impressions | CTR | VCR | Spend");
		assertThat(data).doesNotContain("Share of voice");
		assertThat(data).doesNotContain("Geo");
		assertThat(data).doesNotContain("SEGMENT");

		// Then: each tactic's block is keyed to its own tactic, so the reply routes back to the right slide
		assertThat(data).contains("tactic_1 — CTV (lead KPI: VCR)");
		assertThat(data).contains("tactic_2 — Display (lead KPI: CTR)");

		// Then: the copy asked for is about creatives specifically
		assertThat(prompt).contains("KEY TAKEAWAYS bullets for the 'Creative analysis' slide");
		assertThat(prompt).contains("write exactly 4 takeaways about its creative performance");
	}

	@Test
	void buildCreativeTakeawaysPrompt_warnsOffLowVolumeOutliersAndCapsTheBudgetLiftTest() {
		// Given: a tactic whose low-volume creative posts a rate many times the average — the case where a
		// naive read crowns it and recommends shifting budget the DSP's model cannot actually spend
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "Display", "CTR",
				new CreativeTable("12", "20.0", "1.2", "Hero 15s", List.of(
						new CreativeRow("Hero 15s", "1,200,000", "1.2%", "82.9%", "$4,800"),
						new CreativeRow("Test 320x50", "800", "20.0%", "—", "$12"))));

		// When:
		String prompt = builder.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140).orElseThrow();

		// Then: the outlier is framed as noise rather than a winner, and the budget lift is capped so the
		// DSP's learning phase is not reset
		assertThat(prompt).contains("SMALL-SAMPLE OUTLIERS");
		assertThat(prompt).contains("statistical noise on low volume");
		assertThat(prompt).contains("NEVER recommend shifting significant budget");
		assertThat(prompt).contains("at most ~20%");
		assertThat(prompt).contains("learning phase is not reset");

		// Then: the recommendation bullet is explicitly bound by those same rules
		assertThat(prompt).contains("bound by the small-sample and ~20% rules above");
	}

	@Test
	void buildCreativeTakeawaysPrompt_emptyWhenNoTacticInTheChunkHasDataTest() {
		// Given: a chunk whose only tactic left its block blank
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR", CreativeTable.empty());

		// When:
		Optional<String> prompt = builder.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140);

		// Then: no call is worth making — there is nothing to observe
		assertThat(prompt).isEmpty();
	}

	@Test
	void buildGeoInsightsPrompt_quotesTheBudgetAtEightyPercentAndCarriesTheGeoDataTest() {
		// Given: a tactic whose geo block the user filled in
		GeoInsightInput input = new GeoInsightInput(1, "CTV", "VCR",
				new GeoTable("42", "Miami", "0.48%", List.of(
						new GeoRow("Miami", "1,200,000", "0.48%"),
						new GeoRow("Atlanta", "900,000", "0.46%"))));

		// When: asked with the slide's real 140-character budget
		String prompt = builder.buildGeoInsightsPrompt(List.of(input), "brief", 140).orElseThrow();

		// Then: Claude is asked for the smaller number, leaving the truncation safety net headroom
		assertThat(prompt).contains("at most 112 characters");

		// Then: the tactic's data, stat tiles and lead KPI reach the prompt, with the KPI-named column
		assertThat(prompt).contains("tactic_1 — CTV (lead KPI: VCR)");
		assertThat(prompt).contains("Markets activated: 42");
		assertThat(prompt).contains("Top geo: Miami");
		assertThat(prompt).contains("Geo | Impressions | VCR");
		assertThat(prompt).contains("Miami | 1,200,000 | 0.48%");
	}

	@Test
	void buildGeoInsightsPrompt_asksFiveStringsWithAForwardLookingFifthRecommendationTest() {
		// Given: a filled geo block
		GeoInsightInput input = new GeoInsightInput(1, "CTV", "VCR",
				new GeoTable("42", "Miami", "0.48%", List.of(new GeoRow("Miami", "1,200,000", "0.48%"))));

		// When:
		String prompt = builder.buildGeoInsightsPrompt(List.of(input), "brief", 140).orElseThrow();

		// Then: exactly five strings are requested and the fifth is explicitly a forward-looking recommendation
		assertThat(prompt).contains("array of exactly 5 strings");
		assertThat(prompt).contains("FORWARD-LOOKING recommendation");
	}

	@Test
	void buildGeoInsightsPrompt_emptyWhenNoTacticInTheChunkHasDataTest() {
		// Given: a chunk whose only tactic left its block blank
		GeoInsightInput input = new GeoInsightInput(1, "CTV", "VCR", GeoTable.empty());

		// When:
		Optional<String> prompt = builder.buildGeoInsightsPrompt(List.of(input), "brief", 140);

		// Then: no call is worth making — there is nothing to observe
		assertThat(prompt).isEmpty();
	}

	@Test
	void buildAudienceInsightsPrompt_quotesBothBudgetsAtEightyPercentAndCarriesTheAudienceDataTest() {
		// Given: a tactic whose audience block the user filled in
		AudienceInsightInput input = new AudienceInsightInput(1, "CTV",
				new AudienceTable("25-34", "58% F / 42% M",
						List.of(new AudienceAgeRow("18-24", "800,000"), new AudienceAgeRow("25-34", "1,200,000")),
						List.of(new AudienceSegmentRow("Auto Intenders", "142"))));

		// When: asked with the slide's real 256/120-character budgets
		String prompt = builder.buildAudienceInsightsPrompt(List.of(input), "brief", 256, 120).orElseThrow();

		// Then: Claude is asked for the smaller numbers, leaving the truncation safety net headroom
		assertThat(prompt).contains("at most 204 characters");
		assertThat(prompt).contains("at most 96 characters");

		// Then: the tactic's name, stat tiles and both sub-tables reach the prompt
		assertThat(prompt).contains("tactic_1 — CTV");
		assertThat(prompt).contains("Dominant age group: 25-34");
		assertThat(prompt).contains("Gender demographics: 58% F / 42% M");
		assertThat(prompt).contains("18-24 | 800,000");
		assertThat(prompt).contains("Auto Intenders | 142");
	}

	@Test
	void buildAudienceInsightsPrompt_asksFourStringsWithAForwardLookingRecommendedActionTest() {
		// Given: a filled audience block
		AudienceInsightInput input = new AudienceInsightInput(1, "CTV",
				new AudienceTable("25-34", "58% F", List.of(),
						List.of(new AudienceSegmentRow("Auto Intenders", "142"))));

		// When:
		String prompt = builder.buildAudienceInsightsPrompt(List.of(input), "brief", 256, 120).orElseThrow();

		// Then: exactly four strings are requested and the fourth is explicitly a forward-looking action
		assertThat(prompt).contains("array of exactly 4 strings");
		assertThat(prompt).contains("FORWARD-LOOKING recommendation");
		assertThat(prompt).contains("MOST EFFECTIVE ages and segments");
	}

	@Test
	void buildAudienceInsightsPrompt_emptyWhenNoTacticInTheChunkHasDataTest() {
		// Given: a chunk whose only tactic left its block blank
		AudienceInsightInput input = new AudienceInsightInput(1, "CTV", AudienceTable.empty());

		// When:
		Optional<String> prompt = builder.buildAudienceInsightsPrompt(List.of(input), "brief", 256, 120);

		// Then: no call is worth making — there is nothing to observe
		assertThat(prompt).isEmpty();
	}

	@Test
	void buildDeviceInsightsPrompt_quotesBothBudgetsAtEightyPercentAndCarriesTheDeviceDataTest() {
		// Given: a tactic whose device block the user filled in
		DeviceInsightInput input = new DeviceInsightInput(1, "CTV",
				new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
						List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"),
								new DeviceRow("Connected TV", "900,000", "", "95%", "$6,000"))));

		// When: asked with the slide's real 256/120-character budgets
		String prompt = builder.buildDeviceInsightsPrompt(List.of(input), "brief", 256, 120).orElseThrow();

		// Then: Claude is asked for the smaller numbers, leaving the truncation safety net headroom
		assertThat(prompt).contains("at most 204 characters");
		assertThat(prompt).contains("at most 96 characters");

		// Then: the tactic's name, stat tiles and the device table reach the prompt
		assertThat(prompt).contains("tactic_1 — CTV");
		assertThat(prompt).contains("Highest CTR: 1.20%");
		assertThat(prompt).contains("Top device: Mobile");
		assertThat(prompt).contains("Device | Impressions | CTR | VCR | Spend");
		assertThat(prompt).contains("Mobile | 1,200,000 | 1.20% | 78% | $4,000");
	}

	@Test
	void buildDeviceInsightsPrompt_asksFourStringsWithAForwardLookingRecommendedActionTest() {
		// Given: a filled device block
		DeviceInsightInput input = new DeviceInsightInput(1, "CTV",
				new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
						List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"))));

		// When:
		String prompt = builder.buildDeviceInsightsPrompt(List.of(input), "brief", 256, 120).orElseThrow();

		// Then: exactly four strings are requested, the fourth forward-looking, and CTV's missing CTR flagged
		assertThat(prompt).contains("array of exactly 4 strings");
		assertThat(prompt).contains("FORWARD-LOOKING recommendation");
		assertThat(prompt).contains("MOST EFFECTIVE devices");
		assertThat(prompt).contains("CTR does not apply to Connected TV");
	}

	@Test
	void buildDeviceInsightsPrompt_emptyWhenNoTacticInTheChunkHasDataTest() {
		// Given: a chunk whose only tactic left its block blank
		DeviceInsightInput input = new DeviceInsightInput(1, "CTV", DeviceTable.empty());

		// When:
		Optional<String> prompt = builder.buildDeviceInsightsPrompt(List.of(input), "brief", 256, 120);

		// Then: no call is worth making — there is nothing to observe
		assertThat(prompt).isEmpty();
	}
}
