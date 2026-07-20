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
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());

		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s",
						List.of(new CreativeRow("Hero 15s", "1,200,000", "0.58%", "82.9%", "$4,800"))));
		String expectedPrompt = promptBuilder
				.buildCreativeTakeawaysPrompt(List.of(input), "brief", 100, 140).orElseThrow();
		String long120 = "Hero 15s carried 71% of impressions and posted the campaign's strongest completion "
				+ "rate at 82.9% overall now.";
		JsonNode response = json.readTree(json.writeValueAsString(Map.of(
				"tactic_1", List.of(long120, long120, long120, long120))));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
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
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());
		CreativeTakeawayInput input = new CreativeTakeawayInput(1, "CTV", "VCR",
				new CreativeTable("12", "0.58", "0.42", "Hero 15s", List.of()));
		when(messagesClient.callJsonObject(any(), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
				.thenReturn(null);

		// When:
		Map<Integer, List<String>> takeaways = client.batchCreativeTakeaways(List.of(input), "brief");

		// Then: the tactic is absent rather than carrying invented copy — the caller blanks its bullets
		assertThat(takeaways).isEmpty();

		// Then: it was not given up on after the first failure — these failures are usually transient
		verify(messagesClient, times(2))
				.callJsonObject(any(), eq(4000), eq(60), eq("BatchCreatives"), eq(true));
	}

	@Test
	void batchCreativeTakeawaysRetriesAFailedChunkOneTacticAtATimeTest() throws Exception {
		// Given: a two-tactic chunk whose combined call comes back unusable, while each tactic answers fine
		// on its own — one bad reply used to blank every slide in the chunk
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());

		CreativeTakeawayInput display = new CreativeTakeawayInput(1, "Display", "CTR",
				new CreativeTable("6", "11.04", "2.09", "CH-Ad-320-50-1B", List.of(
						new CreativeRow("CH-Ad-320-50-1B", "144,070", "1.77%", "", "$861.81"))));
		CreativeTakeawayInput video = new CreativeTakeawayInput(2, "Video", "VCR",
				new CreativeTable("4", "0.9", "0.5", "Hero 15s", List.of(
						new CreativeRow("Hero 15s", "600,000", "0.9%", "82.9%", "$2,100"))));
		String chunkPrompt = promptBuilder
				.buildCreativeTakeawaysPrompt(List.of(display, video), "brief", 100, 140).orElseThrow();
		String displayPrompt = promptBuilder
				.buildCreativeTakeawaysPrompt(List.of(display), "brief", 100, 140).orElseThrow();
		String videoPrompt = promptBuilder
				.buildCreativeTakeawaysPrompt(List.of(video), "brief", 100, 140).orElseThrow();
		when(messagesClient.callJsonObject(eq(chunkPrompt), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
				.thenReturn(null);
		when(messagesClient.callJsonObject(eq(displayPrompt), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
				.thenReturn(json.readTree(json.writeValueAsString(Map.of("tactic_1", List.of("d1", "d2", "d3", "d4")))));
		when(messagesClient.callJsonObject(eq(videoPrompt), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
				.thenReturn(json.readTree(json.writeValueAsString(Map.of("tactic_2", List.of("v1", "v2", "v3", "v4")))));
		when(compressionService.compress(any(), eq("BatchD-Creatives")))
				.thenAnswer(call -> {
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : call.<List<ClaudeCompressionField>>getArgument(0)) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		Map<Integer, List<String>> takeaways = client.batchCreativeTakeaways(List.of(display, video), "brief");

		// Then: both tactics are recovered by the per-tactic retry rather than shipping blank
		assertThat(takeaways.get(1)).containsExactly("d1", "d2", "d3", "d4");
		assertThat(takeaways.get(2)).containsExactly("v1", "v2", "v3", "v4");
	}

	@Test
	void batchPublisherObservationsRetriesAFailedChunkOneTacticAtATimeTest() throws Exception {
		// Given: a two-tactic chunk whose combined call fails while the single-tactic calls succeed
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());

		PublisherObservationInput ctv = new PublisherObservationInput(1, "CTV",
				List.of(new PublisherRow("hulu.com", "1,200,000", "42.1%")));
		PublisherObservationInput video = new PublisherObservationInput(2, "Video",
				List.of(new PublisherRow("modrinth.com", "19,674", "15.71%")));
		String chunkPrompt = promptBuilder
				.buildPublisherObservationsPrompt(List.of(ctv, video), "brief", 155).orElseThrow();
		String ctvPrompt = promptBuilder
				.buildPublisherObservationsPrompt(List.of(ctv), "brief", 155).orElseThrow();
		String videoPrompt = promptBuilder
				.buildPublisherObservationsPrompt(List.of(video), "brief", 155).orElseThrow();
		when(messagesClient.callJsonObject(eq(chunkPrompt), eq(4000), eq(60), eq("BatchPublishers"), eq(true)))
				.thenReturn(null);
		when(messagesClient.callJsonObject(eq(ctvPrompt), eq(4000), eq(60), eq("BatchPublishers"), eq(true)))
				.thenReturn(json.readTree(json.writeValueAsString(Map.of("tactic_1", List.of("c1", "c2", "c3", "c4")))));
		when(messagesClient.callJsonObject(eq(videoPrompt), eq(4000), eq(60), eq("BatchPublishers"), eq(true)))
				.thenReturn(json.readTree(json.writeValueAsString(Map.of("tactic_2", List.of("p1", "p2", "p3", "p4")))));
		when(compressionService.compress(any(), eq("BatchD-Publishers")))
				.thenAnswer(call -> {
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : call.<List<ClaudeCompressionField>>getArgument(0)) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		Map<Integer, List<String>> observations = client.batchPublisherObservations(List.of(ctv, video), "brief");

		// Then: both tactics keep their KEY OBSERVATIONS
		assertThat(observations.get(1)).containsExactly("c1", "c2", "c3", "c4");
		assertThat(observations.get(2)).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	void batchCreativeTakeawaysSurvivesClaudeSpellingTheTacticKeyDifferentlyTest() throws Exception {
		// Given: a reply that answers the right tactics but spells the keys its own way — "tactic 1" with a
		// space and a capitalised "Tactic_2" instead of the "tactic_<n>" the prompt asked for. Looked up by
		// exact key, this blanks every bullet on both slides and looks exactly like an unfilled sheet.
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
		when(messagesClient.callJsonObject(any(), eq(4000), eq(60), eq("BatchCreatives"), eq(true)))
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
				compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator());
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

	@Test
	void batchAlignNarrativeRewritesCampaignCopyAndCarriesUntouchedFieldsThroughTest() throws Exception {
		// Given: a real prompt builder/normalizer, an identity compression pass, and independently-written
		// Batch A/C copy whose audience, tactic overviews and recommendations must survive the alignment
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator());
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
