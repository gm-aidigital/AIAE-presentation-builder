package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealClaudeClientTest {

	@Mock
	private AnthropicMessagesClient messagesClient;

	@Mock
	private ClaudeCompressionService compressionService;

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void batchResultsParsesFourOptimizationRecommendationsTest() throws Exception {
		// Given: a real prompt builder/normalizer over a campaign with context, an identity compression pass,
		// and a Batch C response carrying four optimization recommendations
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults);

		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "Display, CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);
		String brief = "Drive awareness for the Spring Launch.";
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);
		String expectedPrompt = promptBuilder.buildBatchCPrompt(data, brief, frequencies).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "results_overviews": {"1": "Overall the campaign delivered strong results."},
				  "thoughts_on_performance": "T1. | T2. | T3. | T4.",
				  "tactic_overviews": {},
				  "optimization_recommendations": [
				    {"title": "Scale CTV", "text": "Shift budget to CTV evenings to extend reach."},
				    {"title": "Refresh Creative", "text": "Rotate display creative monthly to fight fatigue."},
				    {"title": "Expand Audience", "text": "Layer lookalikes onto top segments to grow scale."},
				    {"title": "Add Measurement", "text": "Introduce a brand-lift study to prove impact."}
				  ]
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("results_overview_1", "Overall the campaign delivered strong results.", 380),
				new ClaudeCompressionField("thought_0", "T1.", 220),
				new ClaudeCompressionField("thought_1", "T2.", 220),
				new ClaudeCompressionField("thought_2", "T3.", 220),
				new ClaudeCompressionField("thought_3", "T4.", 220),
				new ClaudeCompressionField("rec_title_0", "Scale CTV", 30),
				new ClaudeCompressionField("rec_text_0", "Shift budget to CTV evenings to extend reach.", 130),
				new ClaudeCompressionField("rec_title_1", "Refresh Creative", 30),
				new ClaudeCompressionField("rec_text_1", "Rotate display creative monthly to fight fatigue.", 130),
				new ClaudeCompressionField("rec_title_2", "Expand Audience", 30),
				new ClaudeCompressionField("rec_text_2", "Layer lookalikes onto top segments to grow scale.", 130),
				new ClaudeCompressionField("rec_title_3", "Add Measurement", 30),
				new ClaudeCompressionField("rec_text_3", "Introduce a brand-lift study to prove impact.", 130));
		// No tactics in this fixture → the Batch C budget is the fixed base (2500) with the base 60s timeout.
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2500), eq(60), eq("BatchC"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchD-Results")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		ClaudeResults results = client.batchResults(data, brief, frequencies);

		// Then:
		assertThat(results.resultsOverviews())
				.containsEntry(1, "Overall the campaign delivered strong results.");
		assertThat(results.recommendations()).hasSize(4);
		assertThat(results.recommendations())
				.extracting(Recommendation::title)
				.containsExactly("Scale CTV", "Refresh Creative", "Expand Audience", "Add Measurement");
		assertThat(results.recommendations().get(0).text()).isEqualTo("Shift budget to CTV evenings to extend reach.");
		assertThat(results.recommendations())
				.allSatisfy(rec -> {
					assertThat(rec.title().length()).isLessThanOrEqualTo(30);
					assertThat(rec.text().length()).isLessThanOrEqualTo(130);
				});
	}

	@Test
	void parseNumberedTextMapRecoversNonNumericAndDriftedKeysTest() throws Exception {
		// Given: a client and a results_overviews object whose keys drifted the way the model produces them —
		// the schema's literal "G" placeholder echoed verbatim, plus "group 2" / "Group 3" prose keys
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults);

		JsonNode node = json.readTree("""
				{"G": "First group narrative.", "group 2": "Second.", "Group 3": "Third."}
				""");

		// When:
		Map<Integer, String> parsed = client.parseNumberedTextMap(node);

		// Then: the letter-only "G" recovers to its 1-based position (1); the prose keys recover their digit
		assertThat(parsed)
				.containsEntry(1, "First group narrative.")
				.containsEntry(2, "Second.")
				.containsEntry(3, "Third.");
	}

	@Test
	void parseNumberedTextMapReturnsEmptyForAbsentOrNonObjectTest() throws Exception {
		// Given: a client
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults);

		// When-Then: a null node and a non-object node both yield an empty map
		assertThat(client.parseNumberedTextMap(null)).isEmpty();
		assertThat(client.parseNumberedTextMap(json.readTree("\"not an object\""))).isEmpty();
	}

	@Test
	void batchStrategicNarrativeParsesProposalAndInsightsWithoutAudienceTest() throws Exception {
		// Given: a real prompt builder/normalizer over a campaign with context, an identity compression pass,
		// and a strategic-narrative response carrying a proposal and four insights but no audience fields
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults);

		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "Display, CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);
		String brief = "Drive awareness for the Spring Launch.";
		String expectedPrompt = promptBuilder.buildBatchStrategicNarrativePrompt(data, brief).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "The campaign drove awareness for auto intenders. It ran across Display and CTV nationally.",
				  "strategic_insights": [
				    {"point": "Precision", "overview": "Targeted auto intenders."},
				    {"point": "Reach", "overview": "Scaled via CTV."},
				    {"point": "Timing", "overview": "Evening dayparts."},
				    {"point": "Efficiency", "overview": "Strong CPM outcome."}
				  ]
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("point_0", "Precision", 22),
				new ClaudeCompressionField("overview_0", "Targeted auto intenders.", 240),
				new ClaudeCompressionField("point_1", "Reach", 22),
				new ClaudeCompressionField("overview_1", "Scaled via CTV.", 240),
				new ClaudeCompressionField("point_2", "Timing", 22),
				new ClaudeCompressionField("overview_2", "Evening dayparts.", 240),
				new ClaudeCompressionField("point_3", "Efficiency", 22),
				new ClaudeCompressionField("overview_3", "Strong CPM outcome.", 240));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2000), eq(60), eq("BatchAStrategic"), eq(false)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchD-Strategic")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		ClaudeStrategic strategic = client.batchStrategicNarrative(data, brief);

		// Then: audience fields stay null (they come from the sheet), while proposal and 4 insights parse
		assertThat(strategic.audienceAge()).isNull();
		assertThat(strategic.audienceSegments()).isNull();
		assertThat(strategic.proposalOverview()).isNotBlank();
		assertThat(strategic.strategicInsights())
				.extracting(StrategicInsight::point)
				.containsExactly("Precision", "Reach", "Timing", "Efficiency");
	}

	@Test
	void batchCreativeTakeawaysGivesTheRecommendationBulletTheWiderBudgetTest() throws Exception {
		// Given: a real prompt builder/normalizer, an identity compression pass, and a reply whose four
		// bullets all run past 100 characters — the budget of the three reads, but not of the fourth bullet
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults());

		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s",
						List.of(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"))));
		String expectedPrompt = promptBuilder
				.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140).orElseThrow();
		String long120 = "Hero 15s carried 71% of impressions and posted the campaign's strongest completion "
				+ "rate at 82.9% overall now.";
		JsonNode response = json.readTree(json.writeValueAsString(Map.of(
				"tactic_1", List.of(long120, long120, long120, long120))));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1500), eq(60), eq("BatchCreatives"), eq(false)))
				.thenReturn(response);
		when(compressionService.compress(any(), eq("BatchD-Creatives")))
				.thenAnswer(call -> {
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : call.<List<ClaudeCompressionField>>getArgument(0)) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		Map<Integer, List<String>> takeaways = client.batchCreativeTakeaways(List.of(input), "brief");

		// Then: the three read bullets are cut to their 100-character budget
		List<String> bullets = takeaways.get(1);
		assertThat(bullets).hasSize(4);
		assertThat(bullets.get(0)).hasSizeLessThanOrEqualTo(100);
		assertThat(bullets.get(2)).hasSizeLessThanOrEqualTo(100);

		// Then: the recommendation bullet keeps the wider 140-character budget, so it is not cut at all
		assertThat(bullets.get(3)).isEqualTo(long120);
	}

	@Test
	void batchCreativeTakeawaysReturnsNothingWhenTheCallFailsTest() {
		// Given: a chunk whose call comes back unusable (timeout / unparseable JSON)
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults());
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s", List.of()));
		when(messagesClient.callJsonObject(any(), eq(1500), eq(60), eq("BatchCreatives"), eq(false)))
				.thenReturn(null);

		// When:
		Map<Integer, List<String>> takeaways = client.batchCreativeTakeaways(List.of(input), "brief");

		// Then: the tactic is absent rather than carrying invented copy — the caller blanks its bullets
		assertThat(takeaways).isEmpty();
	}

	@Test
	void batchCreativeTakeawaysSurvivesClaudeSpellingTheTacticKeyDifferentlyTest() throws Exception {
		// Given: a reply that answers the right tactics but spells the keys its own way — "tactic 1" with a
		// space and a capitalised "Tactic_2" instead of the "tactic_<n>" the prompt asked for. Looked up by
		// exact key, this blanks every bullet on both slides and looks exactly like an unfilled sheet.
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults());

		List<CreativeTakeawayInput> inputs = List.of(
				new CreativeTakeawayInput(1, "Display", "CTR",
						new CreativeTable("6", "11.04", "2.09", "CH-Ad-320-50-1B", List.of(
								new CreativeRow("CH-Ad-320-50-1B", "144,070", "1.77%", "", "$861.81")))),
				new CreativeTakeawayInput(2, "Video", "VCR",
						new CreativeTable("4", "0.9", "0.5", "Hero 15s", List.of(
								new CreativeRow("Hero 15s", "600,000", "0.9%", "82.9%", "$2,100")))));
		JsonNode response = json.readTree("""
				{
				  "tactic 1": ["d1", "d2", "d3", "d4"],
				  "Tactic_2": ["v1", "v2", "v3", "v4"]
				}
				""");
		when(messagesClient.callJsonObject(any(), eq(1500), eq(60), eq("BatchCreatives"), eq(false)))
				.thenReturn(response);
		when(compressionService.compress(any(), eq("BatchD-Creatives")))
				.thenAnswer(call -> {
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : call.<List<ClaudeCompressionField>>getArgument(0)) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		Map<Integer, List<String>> takeaways = client.batchCreativeTakeaways(inputs, "brief");

		// Then: the tactic number is recovered from the key's digits, so both slides get their copy
		assertThat(takeaways.get(1)).containsExactly("d1", "d2", "d3", "d4");
		assertThat(takeaways.get(2)).containsExactly("v1", "v2", "v3", "v4");
	}

	@Test
	void bulletsByTacticIgnoresKeysThatClaimNoTacticOrCarryNoBulletsTest() throws Exception {
		// Given: a reply padded with the commentary keys Claude sometimes adds beside the real answers
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, new ClaudeBatchPromptBuilder(normalizer, new Fmt()), normalizer,
				compressionService, new ReportClaudeDefaults());
		JsonNode parsed = json.readTree("""
				{
				  "notes": "Here are the takeaways you asked for.",
				  "tactic_3": ["a", "b", "c", "d"],
				  "tactic_4": "not an array"
				}
				""");

		// When:
		Map<Integer, JsonNode> byTactic = client.bulletsByTactic(parsed, "BatchCreatives");

		// Then: only the key that names a tactic AND carries an array is kept — a prose key must never be
		// read as tactic copy, and a non-array must not blow up the chunk
		assertThat(byTactic).containsOnlyKeys(3);
		assertThat(byTactic.get(3).size()).isEqualTo(4);
	}
}
