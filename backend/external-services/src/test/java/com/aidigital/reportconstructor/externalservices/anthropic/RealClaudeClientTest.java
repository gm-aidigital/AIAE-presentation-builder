package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusionInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

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
	void batchTacticConclusionsParsesOverviewAndEnabledSectionOnlyTest() throws Exception {
		// Given: a real prompt builder/normalizer, identity compression, one tactic that enabled only the
		// publisher section, and a combined reply carrying its overview + four publisher bullets
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		PublisherObservationInput publisher = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")));
		List<TacticConclusionInput> inputs =
				List.of(new TacticConclusionInput(1, publisher, null, null, null, null));

		String expectedPrompt = promptBuilder.buildTacticConclusionsPrompt(
				data, inputs, brief, 155, 100, 140, 140, 256, 120, 256, 120).orElseThrow();
		JsonNode response = json.readTree("""
				{
				  "tactic_1": {
				    "overview": "CTV delivered 1M impressions at 98% VCR, driven by premium inventory.",
				    "top_publishers": ["Hulu led delivery.", "Long tail carried reach.",
				                       "Premium brand-safe mix.", "We steered weight to strong publishers."]
				  }
				}
				""");
		// One tactic → budget is base 800 + 1200 = 2000, 90s timeout, allowPartial.
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField(
						"1_overview", "CTV delivered 1M impressions at 98% VCR, driven by premium inventory.", 210),
				new ClaudeCompressionField("1_pub_0", "Hulu led delivery.", 155),
				new ClaudeCompressionField("1_pub_1", "Long tail carried reach.", 155),
				new ClaudeCompressionField("1_pub_2", "Premium brand-safe mix.", 155),
				new ClaudeCompressionField("1_pub_3", "We steered weight to strong publishers.", 155));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2000), eq(90), eq("BatchConclusions"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchD-Conclusions")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, inputs, brief);

		// Then: one conclusion with the overview and the four publisher bullets; other sections stay null
		assertThat(conclusions).hasSize(1);
		TacticConclusion c = conclusions.getFirst();
		assertThat(c.tacticNum()).isEqualTo(1);
		assertThat(c.overview()).isEqualTo("CTV delivered 1M impressions at 98% VCR, driven by premium inventory.");
		assertThat(c.publisherBullets()).containsExactly(
				"Hulu led delivery.", "Long tail carried reach.",
				"Premium brand-safe mix.", "We steered weight to strong publishers.");
		assertThat(c.creativeBullets()).isNull();
		assertThat(c.geoBullets()).isNull();
		assertThat(c.audienceFields()).isNull();
		assertThat(c.deviceFields()).isNull();
	}

	@Test
	void batchTacticThoughtsParsesFourThoughtsForOneTacticTest() throws Exception {
		// Given: a real prompt builder/normalizer, identity compression, and one tactic's assembled conclusions
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		String brief = "Drive awareness for the Spring Launch.";
		TacticThoughtsInput input = new TacticThoughtsInput(
				2, "CTV", "CTV delivered 1M impressions at 98% VCR.",
				null, null, List.of("West coast concentrated delivery."), null, null);
		List<TacticThoughtsInput> inputs = List.of(input);

		String expectedPrompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, 220).orElseThrow();
		JsonNode response = json.readTree("""
				{"thoughts": ["Headline result.", "What worked best.", "A watch-out.", "The opportunity."]}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("2_thought_0", "Headline result.", 220),
				new ClaudeCompressionField("2_thought_1", "What worked best.", 220),
				new ClaudeCompressionField("2_thought_2", "A watch-out.", 220),
				new ClaudeCompressionField("2_thought_3", "The opportunity.", 220));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(900), eq(90), eq("BatchTacticThoughts"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchD-TacticThoughts")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		List<TacticThoughts> thoughts = client.batchTacticThoughts(inputs, brief);

		// Then: one tactic's four thoughts, in order
		assertThat(thoughts).hasSize(1);
		assertThat(thoughts.getFirst().tacticNum()).isEqualTo(2);
		assertThat(thoughts.getFirst().thoughts()).containsExactly(
				"Headline result.", "What worked best.", "A watch-out.", "The opportunity.");
	}

	@Test
	void batchCampaignResultsFillsResultsThoughtsRecsAndLeavesTacticOverviewsEmptyTest() throws Exception {
		// Given: a real prompt builder/normalizer, identity compression, one tactic with a Step-3 thoughts
		// digest, and a campaign reply carrying grouped overviews, four thoughts and four recommendations
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);
		List<TacticNarrativeDigest> perTactic = List.of(
				new TacticNarrativeDigest(1, "CTV overview.", List.of("t1", "t2", "t3", "t4"), List.of()));

		String expectedPrompt =
				promptBuilder.buildCampaignResultsPrompt(data, brief, frequencies, perTactic).orElseThrow();
		JsonNode response = json.readTree("""
				{
				  "results_overviews": {"1": "Overall the campaign delivered strong results."},
				  "thoughts_on_performance": "T1. | T2. | T3. | T4.",
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
		// One tactic → budget 2500 + 80 = 2580, 60s timeout, allowPartial.
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2580), eq(60), eq("BatchCampaign"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchD-Campaign")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		ClaudeResults results = client.batchCampaignResults(data, brief, frequencies, perTactic);

		// Then: campaign copy is filled and tacticOverviews stays empty (those come from Step 2)
		assertThat(results.resultsOverviews())
				.containsEntry(1, "Overall the campaign delivered strong results.");
		assertThat(results.thoughtsOnPerformance()).containsExactly("T1.", "T2.", "T3.", "T4.");
		assertThat(results.recommendations())
				.extracting(Recommendation::title)
				.containsExactly("Scale CTV", "Refresh Creative", "Expand Audience", "Add Measurement");
		assertThat(results.tacticOverviews()).isEmpty();
	}

	@Test
	void parseNumberedTextMapRecoversNonNumericAndDriftedKeysTest() throws Exception {
		// Given: a client and a results_overviews object whose keys drifted the way the model produces them —
		// the schema's literal "G" placeholder echoed verbatim, plus "group 2" / "Group 3" prose keys
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

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
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

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
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

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
	void batchAlignNarrativeRewritesCampaignCopyAndCarriesUntouchedFieldsThroughTest() throws Exception {
		// Given: a real prompt builder/normalizer, an identity compression pass, and independently-written
		// Batch A/C copy whose audience, tactic overviews and recommendations must survive the alignment
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Old proposal.",
				List.of(new StrategicInsight("OldPoint", "Old overview.")));
		Map<Integer, String> origOverviews = new LinkedHashMap<>();
		origOverviews.put(1, "Old results overview.");
		Map<Integer, String> origTacticOverviews = new LinkedHashMap<>();
		origTacticOverviews.put(1, "Tactic 1 stays untouched.");
		ClaudeResults results = new ClaudeResults(
				origOverviews, List.of("Old thought."), origTacticOverviews,
				List.of(new Recommendation("Rec", "Rec text stays untouched.")), null, null, null);
		String brief = "Drive awareness for the Spring Launch.";
		String expectedPrompt = promptBuilder.buildBatchDPrompt(strategic, results, List.of(), brief).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "Aligned proposal copy.",
				  "strategic_insights": [{"point": "NewPoint", "overview": "New overview copy."}],
				  "results_overviews": {"1": "Aligned results overview."},
				  "thoughts_on_performance": ["Aligned thought."]
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("point_0", "NewPoint", 22),
				new ClaudeCompressionField("overview_0", "New overview copy.", 240),
				new ClaudeCompressionField("results_overview_1", "Aligned results overview.", 380),
				new ClaudeCompressionField("thought_0", "Aligned thought.", 220),
				new ClaudeCompressionField("proposal_overview", "Aligned proposal copy.", 400));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3000), eq(90), eq("AlignNarrative"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(eq(expectedFields), eq("BatchE-Align")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		ClaudeNarrative aligned = client.batchAlignNarrative(strategic, results, List.of(), brief);

		// Then: the cross-cutting story fields are rewritten from the reply
		assertThat(aligned.strategic().proposalOverview()).contains("Aligned proposal");
		assertThat(aligned.strategic().strategicInsights()).hasSize(1);
		assertThat(aligned.strategic().strategicInsights().get(0).point()).isEqualTo("NewPoint");
		assertThat(aligned.results().resultsOverviews()).containsEntry(1, "Aligned results overview.");
		assertThat(aligned.results().thoughtsOnPerformance()).containsExactly("Aligned thought.");
		// And: the fields the pass must never touch are carried through unchanged
		assertThat(aligned.strategic().audienceAge()).isEqualTo("25-44");
		assertThat(aligned.strategic().audienceSegments()).isEqualTo("Auto intenders");
		assertThat(aligned.results().tacticOverviews()).containsEntry(1, "Tactic 1 stays untouched.");
		assertThat(aligned.results().recommendations()).hasSize(1);
		assertThat(aligned.results().recommendations().get(0).text()).isEqualTo("Rec text stays untouched.");
	}

	@Test
	void batchAlignNarrativeReturnsTheOriginalsVerbatimWhenTheCallFailsTest() throws Exception {
		// Given: a real prompt builder/normalizer and a Batch D call that fails (null reply)
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Original proposal.",
				List.of(new StrategicInsight("Point", "Overview.")));
		Map<Integer, String> origOverviews = new LinkedHashMap<>();
		origOverviews.put(1, "Original results overview.");
		ClaudeResults results = new ClaudeResults(
				origOverviews, List.of("Original thought."), Map.of(), List.of(), null, null, null);
		String brief = "Drive awareness.";
		String expectedPrompt = promptBuilder.buildBatchDPrompt(strategic, results, List.of(), brief).orElseThrow();
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3000), eq(90), eq("AlignNarrative"), eq(true)))
				.thenReturn(null);

		// When:
		ClaudeNarrative aligned = client.batchAlignNarrative(strategic, results, List.of(), brief);

		// Then: the un-aligned originals are returned unchanged — alignment can never blank the deck
		assertThat(aligned.strategic()).isSameAs(strategic);
		assertThat(aligned.results()).isSameAs(results);
	}

	@Test
	void summarizeGeoSendsOnlyTheGeographyRowsOfTheWorkbookTest() throws Exception {
		// Given: a workbook whose bulk is non-geographic, and a geo reply
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());
		List<List<String>> workbook = List.of(
				List.of("### TAB: Proposal ###"),
				List.of("Budget", "$500,000"),
				List.of("Target markets", "Dallas, Austin"));
		String expectedPrompt = promptBuilder.buildGeoPrompt(
				List.of("### TAB: Proposal ###", "Target markets | Dallas, Austin"));
		when(messagesClient.callRaw(eq(expectedPrompt), eq(60), eq(30), eq("Geo")))
				.thenReturn(json.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"Dallas, Austin\"}]}"));

		// When:
		String summary = client.summarizeGeo(workbook);

		// Then: the budget row never reached the model
		assertThat(summary).isEqualTo("Dallas, Austin");
		assertThat(expectedPrompt).doesNotContain("$500,000");
	}

	@Test
	void summarizeGeoSkipsTheCallWhenTheFilteredWorkbookStillBlowsTheBudgetTest() {
		// Given: a workbook where thousands of rows mention geography, so even the filtered prompt is huge
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());
		List<List<String>> workbook = new java.util.ArrayList<>();
		for (int i = 0; i < 2000; i++) {
			workbook.add(List.of("Market " + i, "impressions", "12345"));
		}

		// When:
		String summary = client.summarizeGeo(workbook);

		// Then: no request is made at all and {{geo_locations}} falls back to a dash
		assertThat(summary).isNull();
		verifyNoInteractions(messagesClient);
	}

	@Test
	void summarizeFunnelStagesReadsThePerTacticGoalsRatherThanTheWorkbookTest() throws Exception {
		// Given: the reviewed per-tactic goals and a funnel reply
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());
		List<String> goals = List.of("Build awareness", "  ", "Drive site visits");
		String expectedPrompt = promptBuilder.buildFunnelFromGoalsPrompt(goals).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(60), eq(30), eq("Funnel")))
				.thenReturn(json.readTree(
						"{\"content\":[{\"type\":\"text\",\"text\":\"Awareness, Consideration\"}]}"));

		// When:
		String stages = client.summarizeFunnelStages(goals);

		// Then: blank goals are dropped from the prompt and the line comes back trimmed
		assertThat(stages).isEqualTo("Awareness, Consideration");
		assertThat(expectedPrompt).contains("Build awareness").contains("Drive site visits");
	}

	@Test
	void summarizeFunnelStagesSkipsTheCallWhenNoGoalCarriesTextTest() {
		// Given: only blank goals
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());

		// When:
		String stages = client.summarizeFunnelStages(List.of("", "   "));

		// Then:
		assertThat(stages).isNull();
		verifyNoInteractions(messagesClient);
	}

	@Test
	void digestBriefReturnsTheCondensedBriefAndFallsBackToNullOnFailureTest() throws Exception {
		// Given: a brief and a digest reply, plus a second client whose call fails
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new AnthropicProperties());
		String brief = "Acme wants awareness among auto intenders in Texas over Q1 on a $500,000 budget.";
		String expectedPrompt = promptBuilder.buildBriefDigestPrompt(brief, 2000).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(1200), eq(60), eq("BriefDigest")))
				.thenReturn(json.readTree(
						"{\"content\":[{\"type\":\"text\",\"text\":\"Acme. Awareness. Texas. Q1. $500,000.\"}]}"));

		// When:
		String digest = client.digestBrief(brief);
		String blank = client.digestBrief("   ");

		// Then: a blank brief never reaches the model
		assertThat(digest).isEqualTo("Acme. Awareness. Texas. Q1. $500,000.");
		assertThat(blank).isNull();
	}
}
