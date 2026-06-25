package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
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
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null);
		String expectedPrompt = promptBuilder.buildBatchCPrompt(data, brief, frequencies).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "results_overview": "Overall the campaign delivered strong results.",
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
				new ClaudeCompressionField("results_overview", "Overall the campaign delivered strong results.", 380),
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
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3500), eq(60), eq("BatchC"), eq(true)))
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
}
