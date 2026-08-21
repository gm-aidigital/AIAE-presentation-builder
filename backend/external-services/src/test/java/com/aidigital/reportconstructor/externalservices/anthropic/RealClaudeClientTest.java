package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import com.aidigital.reportconstructor.service.reports.diagnostics.impl.ClaudeFailureLogImpl;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingMetric;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.dto.WhatWeDidStep;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
	void batchTacticConclusionsParsesTheOverviewAndIgnoresAnySectionFieldsTest() throws Exception {
		// Given: a real prompt builder/normalizer, identity compression, one tactic, and a reply that also
		// volunteered publisher bullets — copy this call no longer asks for, since each section has its own call
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		List<Integer> tacticNums = List.of(1);

		String expectedPrompt =
				promptBuilder.buildTacticConclusionsPrompt(data, tacticNums, brief).orElseThrow();
		JsonNode response = json.readTree("""
				{
				  "tactic_1": {
				    "overview": "CTV delivered 1M impressions at 98% VCR, driven by premium inventory.",
				    "top_publishers": ["Hulu led delivery.", "Long tail carried reach.",
				                       "Premium brand-safe mix.", "We steered weight to strong publishers."]
				  }
				}
				""");
		// One tactic → budget is base 400 + 300 = 700, 90s timeout, allowPartial; only the overview is compressed.
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField(
						"1_overview", "CTV delivered 1M impressions at 98% VCR, driven by premium inventory.", 210));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true)))
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
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, tacticNums, brief, Map.of());

		// Then: one conclusion carrying the overview; the volunteered publisher bullets are never compressed or
		// carried, so no section copy can reach a slide from this call
		assertThat(conclusions).hasSize(1);
		TacticConclusion c = conclusions.getFirst();
		assertThat(c.tacticNum()).isEqualTo(1);
		assertThat(c.overview()).isEqualTo("CTV delivered 1M impressions at 98% VCR, driven by premium inventory.");
		verify(compressionService).compress(eq(expectedFields), eq("BatchD-Conclusions"));

		// And: the prompt never described a section field
		assertThat(expectedPrompt).doesNotContain("top_publishers");
	}

	@Test
	void batchTacticConclusionsRecoversASingleTacticReplyMissingItsTacticWrapperTest() throws Exception {
		// Given: one tactic and a reply the model returned as a bare conclusion object WITHOUT the "tactic_1"
		// wrapper key
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		List<Integer> tacticNums = List.of(1);

		String expectedPrompt =
				promptBuilder.buildTacticConclusionsPrompt(data, tacticNums, brief).orElseThrow();
		JsonNode bareResponse = json.readTree("""
				{"overview": "CTV delivered 1M impressions at 98% VCR, driven by premium inventory."}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField(
						"1_overview", "CTV delivered 1M impressions at 98% VCR, driven by premium inventory.", 210));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true)))
				.thenReturn(bareResponse);
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
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, tacticNums, brief, Map.of());

		// Then: the bare object is recovered as tactic 1 rather than dropped, so the overview survives
		assertThat(conclusions).hasSize(1);
		TacticConclusion c = conclusions.getFirst();
		assertThat(c.tacticNum()).isEqualTo(1);
		assertThat(c.overview()).isEqualTo("CTV delivered 1M impressions at 98% VCR, driven by premium inventory.");
	}

	@Test
	void publisherSectionAcceptsAWrapperObjectOnItsFirstAttemptTest() throws Exception {
		// Given: a per-section publisher call whose keyed fields arrived nested under a tactic key — the shape a
		// reply borrows from the combined conclusions call, which used to be discarded and ship the slide blank
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		PublisherObservationInput input = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")), 400_000, 1_000_000);
		JsonNode wrapped = json.readTree("""
				{"tactic_1": {"field_1": "Hulu led delivery.", "field_2": "Long tail carried reach.",
				  "field_3": "Premium brand-safe mix.", "field_4": "We steered weight to strong publishers."}}
				""");
		String expectedPrompt = promptBuilder.buildPublisherSectionPrompt(input, data, brief, 160).orElseThrow();
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("1_0", "Hulu led delivery.", 160),
				new ClaudeCompressionField("1_1", "Long tail carried reach.", 160),
				new ClaudeCompressionField("1_2", "Premium brand-safe mix.", 160),
				new ClaudeCompressionField("1_3", "We steered weight to strong publishers.", 160));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true)))
				.thenReturn(wrapped);
		when(compressionService.compress(eq(expectedFields), eq("PublisherSection")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		List<String> bullets = client.publisherSection(data, input, brief);

		// Then: the four bullets ship, and the call was made once — no retry was spent on a usable reply
		assertThat(bullets).containsExactly(
				"Hulu led delivery.", "Long tail carried reach.",
				"Premium brand-safe mix.", "We steered weight to strong publishers.");
		verify(messagesClient, times(1))
				.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true));
	}

	@Test
	void publisherSectionRetriesAnIncompleteReplyThenShipsTheFieldsItAnsweredTest() {
		// Given: a per-section publisher call whose reply carries only the first of the slide's four fields
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		PublisherObservationInput input = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")), 400_000, 1_000_000);
		String brief = "Drive awareness.";
		String expectedPrompt = promptBuilder.buildPublisherSectionPrompt(input, data, brief, 160).orElseThrow();
		String retryPrompt =
				expectedPrompt + client.sectionRetrySuffix("field(s) [2, 3, 4] of 4 were missing or blank", 4);
		JsonNode oneField = json.createObjectNode().put("field_1", "Only one bullet.");
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true)))
				.thenReturn(oneField);
		when(messagesClient.callJsonObject(eq(retryPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true)))
				.thenReturn(oneField);
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("1_0", "Only one bullet.", 160),
				new ClaudeCompressionField("1_1", "", 160),
				new ClaudeCompressionField("1_2", "", 160),
				new ClaudeCompressionField("1_3", "", 160));
		when(compressionService.compress(eq(expectedFields), eq("PublisherSection")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		List<String> bullets = client.publisherSection(data, input, brief);

		// Then: the one field the model did answer reaches its own slot; the three it never answered dash
		assertThat(bullets).containsExactly("Only one bullet.", "", "", "");
		verify(messagesClient, times(1))
				.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true));

		// Then: the retry is not the same prompt again — it names which fields the first reply left out
		verify(messagesClient, times(1))
				.callJsonObject(eq(retryPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true));
		assertThat(retryPrompt).contains("your previous reply was incomplete", "[2, 3, 4] of 4");
	}

	@Test
	void publisherSectionKeepsTheFourAskedFieldsOfAnOverLongReplyTest() throws Exception {
		// Given: a per-section publisher call whose reply carries a fifth, surplus key — four usable
		// observations under the asked keys plus commentary the slide has no slot for
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		String brief = "Drive awareness for the Spring Launch.";
		PublisherObservationInput input = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")), 400_000, 1_000_000);
		JsonNode overLong = json.readTree("""
				{"field_1": "Hulu led delivery.", "field_2": "Long tail carried reach.",
				 "field_3": "Premium brand-safe mix.", "field_4": "We steered weight to strong publishers.",
				 "field_5": "One more note on the data."}
				""");
		String expectedPrompt = promptBuilder.buildPublisherSectionPrompt(input, data, brief, 160).orElseThrow();
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("1_0", "Hulu led delivery.", 160),
				new ClaudeCompressionField("1_1", "Long tail carried reach.", 160),
				new ClaudeCompressionField("1_2", "Premium brand-safe mix.", 160),
				new ClaudeCompressionField("1_3", "We steered weight to strong publishers.", 160));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true)))
				.thenReturn(overLong);
		when(compressionService.compress(eq(expectedFields), eq("PublisherSection")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		List<String> bullets = client.publisherSection(data, input, brief);

		// Then: the slide's four slots ship from the four asked keys, the surplus one is dropped, and no
		// retry was spent on a reply that already carried the copy
		assertThat(bullets).containsExactly(
				"Hulu led delivery.", "Long tail carried reach.",
				"Premium brand-safe mix.", "We steered weight to strong publishers.");
		verify(messagesClient, times(1))
				.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true));
	}

	@Test
	void publisherSectionRecordsItsRejectionReasonOnTheRunsFailureScopeTest() {
		// Given: a run whose failure scope is open — the report card's only source for why a slide is blank,
		// since the person who ran the report cannot read the server log
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeFailureScope failures = failureLog.begin();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), failureLog,
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		PublisherObservationInput input = new PublisherObservationInput(
				1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")), 400_000, 1_000_000);
		String brief = "Drive awareness.";
		String expectedPrompt = promptBuilder.buildPublisherSectionPrompt(input, data, brief, 160).orElseThrow();
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1500), eq(90), eq("PublisherSection"), eq(true)))
				.thenReturn(json.createObjectNode());

		// When:
		client.publisherSection(data, input, brief);

		// Then: the scope carries the section, the tactic and what was wrong, plus the final give-up line
		assertThat(failures.snapshot()).isNotEmpty();
		assertThat(failures.snapshot().getFirst())
				.contains("PublisherSection", "tactic 1", "[1, 2, 3, 4] of 4");
		assertThat(failures.snapshot().getLast()).contains("gave up after 2 attempt(s)");
		failureLog.clear();
	}

	@Test
	void shouldParseFourThoughtsAndTheClosingStoryForOneTacticTest() throws Exception {
		// Given: a real prompt builder/normalizer, identity compression, and one tactic's assembled conclusions
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		String brief = "Drive awareness for the Spring Launch.";
		TacticThoughtsInput input = new TacticThoughtsInput(
				2, "CTV", "CTV delivered 1M impressions at 98% VCR.",
				null, null, List.of("West coast concentrated delivery."), null, null);

		String expectedPrompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, 220, 470).orElseThrow();
		JsonNode response = json.readTree("""
				{"thoughts": ["Headline result.", "What worked best.", "A watch-out.", "The opportunity."],
				 "story": "CTV was asked to build reach and it did, start to finish."}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("2_thought_0", "Headline result.", 220),
				new ClaudeCompressionField("2_thought_1", "What worked best.", 220),
				new ClaudeCompressionField("2_thought_2", "A watch-out.", 220),
				new ClaudeCompressionField("2_thought_3", "The opportunity.", 220),
				new ClaudeCompressionField(
						"2_story", "CTV was asked to build reach and it did, start to finish.", 470));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1800), eq(90), eq("BatchTacticThoughts"), eq(true)))
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
		TacticThoughts thoughts = client.tacticThoughts(input, brief);

		// Then: the tactic's four thoughts in order, with the story last as slot 5
		assertThat(thoughts).isNotNull();
		assertThat(thoughts.tacticNum()).isEqualTo(2);
		assertThat(thoughts.thoughts()).containsExactly(
				"Headline result.", "What worked best.", "A watch-out.", "The opportunity.",
				"CTV was asked to build reach and it did, start to finish.");
	}

	@Test
	void shouldRetryAnEmptyThoughtsArrayAndKeepTheRetrysThoughtsTest() throws Exception {
		// Given: a first reply whose "thoughts" array is well-formed but holds nothing but blanks — the shape
		// that used to pass as a success and blank the slide silently — and a complete retry behind it
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ClaudeFailureLogImpl failureLog = new ClaudeFailureLogImpl();
		ClaudeFailureScope failures = failureLog.begin();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), failureLog, new AnthropicProperties());

		String brief = "Drive awareness for the Spring Launch.";
		TacticThoughtsInput input = new TacticThoughtsInput(
				4, "Display", "Display delivered 2M impressions at a 0.12% CTR.",
				List.of("Hulu led delivery."), null, null, null, null);
		String expectedPrompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, 220, 470).orElseThrow();
		JsonNode blank = json.readTree("{\"thoughts\": [\"\", \"\", \"\", \"\"], \"story\": \"\"}");
		JsonNode complete = json.readTree("""
				{"thoughts": ["Headline result.", "What worked best.", "A watch-out.", "The opportunity."],
				 "story": "Display was asked to hold frequency and it held it."}
				""");
		List<ClaudeCompressionField> retryFields = List.of(
				new ClaudeCompressionField("4_thought_0", "Headline result.", 220),
				new ClaudeCompressionField("4_thought_1", "What worked best.", 220),
				new ClaudeCompressionField("4_thought_2", "A watch-out.", 220),
				new ClaudeCompressionField("4_thought_3", "The opportunity.", 220),
				new ClaudeCompressionField(
						"4_story", "Display was asked to hold frequency and it held it.", 470));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1800), eq(90), eq("BatchTacticThoughts"), eq(true)))
				.thenReturn(blank, complete);
		when(compressionService.compress(eq(retryFields), eq("BatchD-TacticThoughts")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		TacticThoughts thoughts = client.tacticThoughts(input, brief);

		// Then: the blank reply was rejected, the retry's four thoughts and story were kept, and the reason
		// was recorded
		assertThat(thoughts).isNotNull();
		assertThat(thoughts.thoughts()).containsExactly(
				"Headline result.", "What worked best.", "A watch-out.", "The opportunity.",
				"Display was asked to hold frequency and it held it.");
		verify(messagesClient, times(2))
				.callJsonObject(eq(expectedPrompt), eq(1800), eq(90), eq("BatchTacticThoughts"), eq(true));
		assertThat(failures.snapshot()).isNotEmpty();
		assertThat(failures.snapshot().getFirst()).contains("tactic 4", "no non-blank thought");
		failureLog.clear();
	}

	@Test
	void shouldKeepThePartialThoughtsWhenNeitherAttemptFillsAllFourTest() throws Exception {
		// Given: both attempts fill only two of the four thoughts — blanking all four would be worse than
		// shipping the two real ones
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		String brief = "Drive awareness for the Spring Launch.";
		TacticThoughtsInput input = new TacticThoughtsInput(
				7, "Audio", "Audio delivered 500k impressions at 95% ACR.",
				null, null, null, null, List.of("Mobile carried delivery."));
		String expectedPrompt = promptBuilder.buildTacticThoughtsPrompt(input, brief, 220, 470).orElseThrow();
		JsonNode partial = json.readTree("{\"thoughts\": [\"Headline result.\", \"A watch-out.\"]}");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("7_thought_0", "Headline result.", 220),
				new ClaudeCompressionField("7_thought_1", "A watch-out.", 220),
				new ClaudeCompressionField("7_thought_2", "", 220),
				new ClaudeCompressionField("7_thought_3", "", 220),
				new ClaudeCompressionField("7_story", "", 470));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1800), eq(90), eq("BatchTacticThoughts"), eq(true)))
				.thenReturn(partial);
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
		TacticThoughts thoughts = client.tacticThoughts(input, brief);

		// Then: the two filled thoughts survive, the missing two and the absent story stay null, and the call
		// was retried once
		assertThat(thoughts).isNotNull();
		assertThat(thoughts.thoughts()).containsExactly("Headline result.", "A watch-out.", null, null, null);
		verify(messagesClient, times(2))
				.callJsonObject(eq(expectedPrompt), eq(1800), eq(90), eq("BatchTacticThoughts"), eq(true));
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
				new TacticNarrativeDigest(1, "CTV", "CTV overview.", List.of("t1", "t2", "t3", "t4"), List.of()));

		String expectedPrompt =
				promptBuilder.buildCampaignResultsPrompt(data, brief, frequencies, perTactic).orElseThrow();
		JsonNode response = json.readTree("""
				{
				  "results_overviews": {"1": "Overall the campaign delivered strong results."},
				  "thoughts_on_performance": "T1. | T2. | T3. | T4.",
				  "performance_story": "The brief asked for reach and the campaign delivered it.",
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
				new ClaudeCompressionField(
						"performance_story", "The brief asked for reach and the campaign delivered it.", 470),
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
		assertThat(results.thoughtsOnPerformance()).containsExactly(
				"T1.", "T2.", "T3.", "T4.", "The brief asked for reach and the campaign delivered it.");
		assertThat(results.recommendations())
				.extracting(Recommendation::title)
				.containsExactly("Scale CTV", "Refresh Creative", "Expand Audience", "Add Measurement");
		assertThat(results.tacticOverviews()).isEmpty();
	}

	@Test
	void shouldRetryCampaignResultsOnceWhenTheFirstReplyCarriesNothingUsableTest() throws Exception {
		// Given: a first reply that parses as JSON but holds no overviews, thoughts or recommendations — the
		// failure that would otherwise dash the results overview, the thoughts and the recommendations at once
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);
		List<TacticNarrativeDigest> perTactic = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV overview.", List.of("t1", "t2", "t3", "t4"), List.of()));

		String expectedPrompt =
				promptBuilder.buildCampaignResultsPrompt(data, "Brief.", frequencies, perTactic).orElseThrow();
		JsonNode empty = json.readTree("{\"results_overviews\": {}}");
		JsonNode good = json.readTree("""
				{
				  "results_overviews": {"1": "Overall the campaign delivered strong results."},
				  "thoughts_on_performance": "T1. | T2. | T3. | T4."
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("results_overview_1", "Overall the campaign delivered strong results.", 380),
				new ClaudeCompressionField("thought_0", "T1.", 220),
				new ClaudeCompressionField("thought_1", "T2.", 220),
				new ClaudeCompressionField("thought_2", "T3.", 220),
				new ClaudeCompressionField("thought_3", "T4.", 220),
				new ClaudeCompressionField("rec_title_0", "", 30),
				new ClaudeCompressionField("rec_text_0", "", 130),
				new ClaudeCompressionField("rec_title_1", "", 30),
				new ClaudeCompressionField("rec_text_1", "", 130),
				new ClaudeCompressionField("rec_title_2", "", 30),
				new ClaudeCompressionField("rec_text_2", "", 130),
				new ClaudeCompressionField("rec_title_3", "", 30),
				new ClaudeCompressionField("rec_text_3", "", 130));
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2580), eq(60), eq("BatchCampaign"), eq(true)))
				.thenReturn(empty, good);
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
		ClaudeResults results = client.batchCampaignResults(data, "Brief.", frequencies, perTactic);

		// Then: the second send's copy ships, and the unusable first reply never reached the compression call
		assertThat(results.resultsOverviews())
				.containsEntry(1, "Overall the campaign delivered strong results.");
		// The reply carried no performance_story, so slot 5 stays null rather than borrowing a thought
		assertThat(results.thoughtsOnPerformance()).containsExactly("T1.", "T2.", "T3.", "T4.", null);
		verify(messagesClient, times(2))
				.callJsonObject(eq(expectedPrompt), eq(2580), eq(60), eq("BatchCampaign"), eq(true));
		verify(compressionService, times(1)).compress(eq(expectedFields), eq("BatchD-Campaign"));
	}

	@Test
	void shouldFallBackToEmptyResultsWhenBothCampaignAttemptsFailTest() {
		// Given: both sends come back null (timeout / non-200 / unparseable)
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 980_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Jan 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), null);
		CampaignFrequencies frequencies = new CampaignFrequencies(null, null, null, null);
		List<TacticNarrativeDigest> perTactic = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV overview.", List.of("t1"), List.of()));
		String expectedPrompt =
				promptBuilder.buildCampaignResultsPrompt(data, "Brief.", frequencies, perTactic).orElseThrow();
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(2580), eq(60), eq("BatchCampaign"), eq(true)))
				.thenReturn(null);

		// When:
		ClaudeResults results = client.batchCampaignResults(data, "Brief.", frequencies, perTactic);

		// Then: exactly two attempts, then the empty DTO the orchestrator turns into a job warning
		assertThat(results.resultsOverviews()).isEmpty();
		verify(messagesClient, times(2))
				.callJsonObject(eq(expectedPrompt), eq(2580), eq(60), eq("BatchCampaign"), eq(true));
		verifyNoInteractions(compressionService);
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3500), eq(60), eq("BatchAStrategic"), eq(false)))
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
		ClaudeStrategic strategic = client.batchStrategicNarrative(data, brief, java.util.Map.of());

		// Then: audience fields stay null (they come from the sheet), while proposal and 4 insights parse
		assertThat(strategic.audienceAge()).isNull();
		assertThat(strategic.audienceSegments()).isNull();
		assertThat(strategic.proposalOverview()).isNotBlank();
		assertThat(strategic.strategicInsights())
				.extracting(StrategicInsight::point)
				.containsExactly("Precision", "Reach", "Timing", "Efficiency");
	}

	@Test
	void batchStrategicNarrativeParsesTheEndOfMonthNorthStarFieldsTest() throws Exception {
		// Given: the end-of-month flavour of the strategic-narrative call, whose prompt asks for the three
		// north-star slide fields on top of the proposal and insights
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		EomPromptBuilder promptBuilder = new EomPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "Southern California", "Consideration", "Jun 1 - Jun 30, 2026",
				null, "$500,000", "Reach", "Display, Video", "25+", "Eco-conscious parents",
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);
		String brief = "Drive consideration for the Spring Launch.";
		String expectedPrompt = promptBuilder.strategicNarrativePrompt(data, brief, Map.of()).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "Throughout June we continued running Display and Video. The campaign builds consideration.",
				  "north_star": "Deep penetration of the eco-conscious household decision-maker.",
				  "extended_north_star": "Across Orange County and LA, against eco-conscious parents 25+, through Display and Video.",
				  "horizon": "A continuous 30-day June flight held at even weight, which is why reach-building video and always-on display sit beneath it.",
				  "strategic_insights": [
				    {"point": "Precision", "overview": "Targeted eco parents."},
				    {"point": "Reach", "overview": "Scaled via Video."},
				    {"point": "Timing", "overview": "Evening dayparts."},
				    {"point": "Efficiency", "overview": "Strong CPM outcome."}
				  ]
				}
				""");
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3500), eq(60), eq("BatchAStrategic"), eq(false)))
				.thenReturn(response);
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("point_0", "Precision", 22),
				new ClaudeCompressionField("overview_0", "Targeted eco parents.", 240),
				new ClaudeCompressionField("point_1", "Reach", 22),
				new ClaudeCompressionField("overview_1", "Scaled via Video.", 240),
				new ClaudeCompressionField("point_2", "Timing", 22),
				new ClaudeCompressionField("overview_2", "Evening dayparts.", 240),
				new ClaudeCompressionField("point_3", "Efficiency", 22),
				new ClaudeCompressionField("overview_3", "Strong CPM outcome.", 240));
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
		ClaudeStrategic strategic = client.batchStrategicNarrative(data, brief, Map.of());

		// Then: the headline comes back upper-cased and un-punctuated, and the two supporting fields survive
		assertThat(strategic.northStar())
				.isEqualTo("DEEP PENETRATION OF THE ECO-CONSCIOUS HOUSEHOLD DECISION-MAKER");
		assertThat(strategic.extendedNorthStar()).startsWith("Across Orange County and LA");
		assertThat(strategic.horizon()).startsWith("A continuous 30-day June flight");
	}

	@Test
	void batchStrategicNarrativeParsesBothDashboardTakeawayArraysTest() throws Exception {
		// Given: an end-of-month reply carrying one takeaway per dashboard slide, for both dashboards
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		EomPromptBuilder promptBuilder = new EomPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		CampaignData data = new CampaignData(
				"Acme", "Spring Launch", "Southern California", "Consideration", "Jun 1 - Jun 30, 2026",
				null, "$500,000", "Reach", "Display, Video", "25+", "Eco-conscious parents",
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);
		String brief = "Drive consideration for the Spring Launch.";
		String expectedPrompt = promptBuilder.strategicNarrativePrompt(data, brief, Map.of()).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "Throughout June we continued running Display and Video.",
				  "pacing_takeaways": ["Every channel is within 5% of its budget."],
				  "performance_takeaways": ["CTR is above goal on both channels."],
				  "updated_projection": "At this pace we close the flight at 12.4M impressions, 3% over goal.",
				  "strategic_insights": []
				}
				""");
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(3500), eq(60), eq("BatchAStrategic"), eq(false)))
				.thenReturn(response);
		when(compressionService.compress(eq(List.of(
				new ClaudeCompressionField("point_0", "", 22),
				new ClaudeCompressionField("overview_0", "", 240),
				new ClaudeCompressionField("point_1", "", 22),
				new ClaudeCompressionField("overview_1", "", 240),
				new ClaudeCompressionField("point_2", "", 22),
				new ClaudeCompressionField("overview_2", "", 240),
				new ClaudeCompressionField("point_3", "", 22),
				new ClaudeCompressionField("overview_3", "", 240))), eq("BatchD-Strategic")))
				.thenReturn(new LinkedHashMap<>());

		// When:
		ClaudeStrategic strategic = client.batchStrategicNarrative(data, brief, Map.of());

		// Then: each dashboard's takeaways land in their own field, so neither slide prints the other's copy
		assertThat(strategic.pacingTakeaways()).containsExactly("Every channel is within 5% of its budget.");
		assertThat(strategic.performanceTakeaways()).containsExactly("CTR is above goal on both channels.");
		// And: the focus slide's projection rides on this call too — it is the one that carries the tables
		assertThat(strategic.updatedProjection())
				.isEqualTo("At this pace we close the flight at 12.4M impressions, 3% over goal.");
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
		// 400 base + 220 proposal + 160 insight + 220 overview + 140 thought, no frequency copy in this draft
		int expectedBudget = 1140;
		when(messagesClient.callJsonObject(
				eq(expectedPrompt), eq(expectedBudget), eq(90), eq("AlignNarrative"), eq(true)))
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
		ClaudeNarrative aligned = client.batchAlignNarrative(strategic, results, List.of(), brief, null);

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
	void batchAlignNarrativeWritesTheEndOfMonthWhatWeDidChainsTest() throws Exception {
		// Given: the same alignment call on an end-of-month run, whose schema also asks for the three
		// observation -> action -> expected impact chains the "what we did this month" slide prints
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		EomPromptBuilder promptBuilder = new EomPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Old proposal.", List.of(), "NORTH STAR", "Extended.", "Horizon.",
				List.of("Pacing takeaway."), List.of("Performance takeaway."));
		Map<Integer, String> origOverviews = new LinkedHashMap<>();
		origOverviews.put(1, "Old results overview.");
		ClaudeResults results = new ClaudeResults(
				origOverviews, List.of(), Map.of(), List.of(), null, null, null);
		String brief = "Drive awareness for the Spring Launch.";
		String expectedPrompt = promptBuilder
				.alignPrompt(strategic, results, List.of(), brief, "Jul 1 - Jul 31, 2026").orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "Aligned proposal copy.",
				  "results_overviews": {"1": "Aligned results overview."},
				  "what_we_did": [
				    {"observation": "Audience fatigue", "observation_text": "CTR fell 18% in week 3.",
				     "action": "Refreshed creative", "action_text": "Swapped the two worn display units.",
				     "impact": "Efficiency restored", "impact_text": "Protects the rest of the display budget."},
				    {"observation": "Chicago pulling ahead", "observation_text": "Chicago runs a 0.42% CTR.",
				     "action": "Shifted budget", "action_text": "Moved 15% of display spend into Chicago.",
				     "impact": "Scale the winner", "impact_text": "Lifts CTR over the remaining flight."},
				    {"observation": "", "observation_text": "", "action": "", "action_text": "",
				     "impact": "", "impact_text": ""}
				  ],
				  "carry_forward": ["Hold weight on PHI & CLT", "Keep video on the CPM buy"],
				  "pivot": ["Cap video frequency under 9.5x"],
				  "new_test": ["Test a romantic-getaway segment"]
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("results_overview_1", "Aligned results overview.", 380),
				new ClaudeCompressionField("proposal_overview", "Aligned proposal copy.", 400));
		// 400 base + 220 proposal + 220 overview, plus the 1300 the end-of-month chains and the focus
		// columns add to the reply
		int expectedBudget = 2140;
		when(messagesClient.callJsonObject(
				eq(expectedPrompt), eq(expectedBudget), eq(90), eq("AlignNarrative"), eq(true)))
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
		ClaudeNarrative aligned = client.batchAlignNarrative(
				strategic, results, List.of(), brief, "Jul 1 - Jul 31, 2026");

		// Then: each chain lands whole and in the column it was written for, and the empty third chain is
		// dropped rather than shipping a column of blank boxes
		assertThat(aligned.strategic().whatWeDid()).hasSize(2);
		WhatWeDidStep first = aligned.strategic().whatWeDid().get(0);
		assertThat(first.observation()).isEqualTo("Audience fatigue");
		assertThat(first.observationText()).isEqualTo("CTR fell 18% in week 3.");
		assertThat(first.actionText()).isEqualTo("Swapped the two worn display units.");
		assertThat(first.impact()).isEqualTo("Efficiency restored");
		assertThat(aligned.strategic().whatWeDid().get(1).action()).isEqualTo("Shifted budget");
		// And: next month's plan is written by the same call, each column filling its own slots
		assertThat(aligned.strategic().focusNextMonth().carryForward())
				.containsExactly("Hold weight on PHI & CLT", "Keep video on the CPM buy");
		assertThat(aligned.strategic().focusNextMonth().pivots())
				.containsExactly("Cap video frequency under 9.5x");
		assertThat(aligned.strategic().focusNextMonth().tests())
				.containsExactly("Test a romantic-getaway segment");
		// And: the EOM copy the pass never asks about is still carried through untouched
		assertThat(aligned.strategic().northStar()).isEqualTo("NORTH STAR");
		assertThat(aligned.strategic().pacingTakeaways()).containsExactly("Pacing takeaway.");
	}

	@Test
	void batchAlignNarrativeRetriesOnceWithTheJsonOnlyDemandRestatedTest() throws Exception {
		// Given: a Batch D call whose first reply is unusable and whose second, re-asked, reply parses
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Old proposal.",
				List.of(new StrategicInsight("OldPoint", "Old overview.")));
		Map<Integer, String> origOverviews = new LinkedHashMap<>();
		origOverviews.put(1, "Old results overview.");
		ClaudeResults results = new ClaudeResults(
				origOverviews, List.of("Old thought."), Map.of(), List.of(), null, null, null);
		String brief = "Drive awareness for the Spring Launch.";
		String firstPrompt = promptBuilder.buildBatchDPrompt(strategic, results, List.of(), brief).orElseThrow();

		JsonNode response = json.readTree("""
				{
				  "proposal_overview": "Aligned proposal copy.",
				  "strategic_insights": [{"point": "NewPoint", "overview": "New overview copy."}],
				  "results_overviews": {"1": "Aligned results overview."},
				  "thoughts_on_performance": ["Aligned thought."]
				}
				""");
		String retrySuffix =
				"\n\nIMPORTANT: the previous reply could not be parsed. Return the JSON object and nothing else — "
						+ "the first character must be { and the last must be }, with no prose, preamble or "
						+ "backticks around it.";
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("point_0", "NewPoint", 22),
				new ClaudeCompressionField("overview_0", "New overview copy.", 240),
				new ClaudeCompressionField("results_overview_1", "Aligned results overview.", 380),
				new ClaudeCompressionField("thought_0", "Aligned thought.", 220),
				new ClaudeCompressionField("proposal_overview", "Aligned proposal copy.", 400));
		when(messagesClient.callJsonObject(eq(firstPrompt), eq(1140), eq(90), eq("AlignNarrative"), eq(true)))
				.thenReturn(null);
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		when(compressionService.compress(eq(expectedFields), eq("BatchE-Align")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When: the retried prompt — the original plus the JSON-only reminder — comes back parseable
		when(messagesClient.callJsonObject(
				eq(firstPrompt + retrySuffix), eq(1140), eq(90), eq("AlignNarrative"), eq(true)))
				.thenReturn(response);
		ClaudeNarrative aligned = client.batchAlignNarrative(strategic, results, List.of(), brief, null);

		// Then: the second attempt's copy is what ships, and exactly two sends were made
		assertThat(aligned.strategic().proposalOverview()).contains("Aligned proposal");
		assertThat(aligned.results().resultsOverviews()).containsEntry(1, "Aligned results overview.");
		verify(messagesClient, times(2)).callJsonObject(
				promptCaptor.capture(), eq(1140), eq(90), eq("AlignNarrative"), eq(true));
		assertThat(promptCaptor.getAllValues().get(0)).isEqualTo(firstPrompt);
		assertThat(promptCaptor.getAllValues().get(1)).endsWith(retrySuffix);
	}

	@Test
	void alignMaxTokensGrowsWithTheNumberOfResultsGroupsAndStaysUnderTheCapTest() {
		// Given: two drafts that differ only in how many tactic groups they carry
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, new ClaudeBatchPromptBuilder(normalizer, new Fmt()), normalizer,
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(),
				new PromptTokenEstimator(), new ClaudeFailureLogImpl(), new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Proposal.",
				List.of(new StrategicInsight("P1", "O1"), new StrategicInsight("P2", "O2")));
		Map<Integer, String> fourGroups = new LinkedHashMap<>();
		Map<Integer, String> twentyGroups = new LinkedHashMap<>();
		for (int group = 1; group <= 20; group++) {
			if (group <= 4) {
				fourGroups.put(group, "Overview " + group);
			}
			twentyGroups.put(group, "Overview " + group);
		}
		ClaudeResults small = new ClaudeResults(
				fourGroups, List.of("T1", "T2"), Map.of(), List.of(), "Opp", "Fact", "Story");
		ClaudeResults large = new ClaudeResults(
				twentyGroups, List.of("T1", "T2"), Map.of(), List.of(), "Opp", "Fact", "Story");

		// When:
		int smallBudget = client.alignMaxTokens(strategic, small);
		int largeBudget = client.alignMaxTokens(strategic, large);

		// Then: 400 base + 220 proposal + 2*160 insights + 2*140 thoughts + 3*200 frequency, plus 220 per group
		assertThat(smallBudget).isEqualTo(400 + 220 + 320 + 280 + 600 + 4 * 220);
		assertThat(largeBudget).isEqualTo(400 + 220 + 320 + 280 + 600 + 20 * 220);
		assertThat(largeBudget).isLessThanOrEqualTo(8000);
	}

	@Test
	void batchAlignNarrativeReturnsTheOriginalsVerbatimWhenTheCallFailsTest() throws Exception {
		// Given: a real prompt builder/normalizer and a Batch D call that fails (null reply)
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		ReportClaudeDefaults defaults = new ReportClaudeDefaults();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, defaults,
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		ClaudeStrategic strategic = new ClaudeStrategic(
				"25-44", "Auto intenders", "Original proposal.",
				List.of(new StrategicInsight("Point", "Overview.")));
		Map<Integer, String> origOverviews = new LinkedHashMap<>();
		origOverviews.put(1, "Original results overview.");
		ClaudeResults results = new ClaudeResults(
				origOverviews, List.of("Original thought."), Map.of(), List.of(), null, null, null);
		String brief = "Drive awareness.";
		String expectedPrompt = promptBuilder.buildBatchDPrompt(strategic, results, List.of(), brief).orElseThrow();
		// 400 base + 220 proposal + 160 insight + 220 overview + 140 thought, no frequency copy in this draft
		int expectedBudget = 1140;
		when(messagesClient.callJsonObject(
				eq(expectedPrompt), eq(expectedBudget), eq(90), eq("AlignNarrative"), eq(true)))
				.thenReturn(null);

		// When:
		ClaudeNarrative aligned = client.batchAlignNarrative(strategic, results, List.of(), brief, null);

		// Then: the un-aligned originals are returned unchanged — alignment can never blank the deck
		assertThat(aligned.strategic()).isSameAs(strategic);
		assertThat(aligned.results()).isSameAs(results);
		// And: the re-ask was spent before giving up, so a one-off unparseable reply is not the end of it
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(messagesClient, times(2)).callJsonObject(
				promptCaptor.capture(), eq(expectedBudget), eq(90), eq("AlignNarrative"), eq(true));
		assertThat(promptCaptor.getAllValues().get(0)).isEqualTo(expectedPrompt);
		assertThat(promptCaptor.getAllValues().get(1)).startsWith(expectedPrompt);
	}

	@Test
	void summarizeGeoSendsOnlyTheGeographyRowsOfTheWorkbookTest() throws Exception {
		// Given: a workbook whose bulk is non-geographic, and a geo reply
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

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
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
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

	@Test
	void digestChangeLogCondensesTheLogThroughItsOwnCallTest() throws Exception {
		// Given: a change log and its digest reply. It gets its own call rather than being appended to the brief:
		// a brief-shaped prompt reads an appended change-log section as commentary and drops it.
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		String changeLog = "Shifted 20% of Display budget to CTV on Jul 3 after CTV VCR held above 95%.";
		String expectedPrompt = promptBuilder.buildChangeLogDigestPrompt(changeLog, 1500).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(900), eq(60), eq("ChangeLogDigest")))
				.thenReturn(json.readTree(
						"{\"content\":[{\"type\":\"text\",\"text\":\"Jul 3: 20% of Display budget moved to CTV; "
								+ "CTV VCR above 95%.\"}]}"));

		// When:
		String digest = client.digestChangeLog(changeLog);
		String blank = client.digestChangeLog("   ");

		// Then: the log is condensed, and a blank log never reaches the model
		assertThat(digest).isEqualTo("Jul 3: 20% of Display budget moved to CTV; CTV VCR above 95%.");
		assertThat(blank).isNull();
	}

	@Test
	void digestChangeLogFallsBackToNullWhenTheCallFailsTest() {
		// Given: a change log whose digest call comes back with nothing
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		String changeLog = "Shifted budget mid-flight.";
		String expectedPrompt = promptBuilder.buildChangeLogDigestPrompt(changeLog, 1500).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(900), eq(60), eq("ChangeLogDigest"))).thenReturn(null);

		// When:
		String digest = client.digestChangeLog(changeLog);

		// Then: null, so the caller keeps the raw log rather than losing the mid-flight changes entirely
		assertThat(digest).isNull();
	}

	@Test
	void digestBriefIfOversizedBoundsALongBriefAndLeavesACompactOneAloneTest() throws Exception {
		// Given: a brief context far past the 2000-character digest budget — the shape the sheet's user-editable
		// {{RFP info}} cell or a pasted change log can produce — and a compact one that is already fine
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		String oversized = "Acme wants awareness among auto intenders. ".repeat(80);
		String compact = "Acme wants awareness among auto intenders in Texas over Q1.";
		String expectedPrompt = promptBuilder.buildBriefDigestPrompt(oversized, 2000).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(1200), eq(60), eq("BriefDigest")))
				.thenReturn(json.readTree(
						"{\"content\":[{\"type\":\"text\",\"text\":\"Acme. Awareness. Auto intenders.\"}]}"));

		// When:
		String bounded = client.digestBriefIfOversized(oversized);
		String untouched = client.digestBriefIfOversized(compact);

		// Then: the long text is replaced by its digest, so it is not repeated in full across the run's prompts
		assertThat(oversized.length()).isGreaterThan(2000);
		assertThat(bounded).isEqualTo("Acme. Awareness. Auto intenders.");

		// Then: a brief already inside the budget passes through without spending a call on it
		assertThat(untouched).isEqualTo(compact);
		verify(messagesClient, times(1)).callRaw(eq(expectedPrompt), eq(1200), eq(60), eq("BriefDigest"));
	}

	@Test
	void digestBriefIfOversizedKeepsTheRawTextWhenTheDigestCallFailsTest() {
		// Given: an oversized brief whose digest call comes back with nothing
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		String oversized = "Acme wants awareness among auto intenders. ".repeat(80);
		String expectedPrompt = promptBuilder.buildBriefDigestPrompt(oversized, 2000).orElseThrow();
		when(messagesClient.callRaw(eq(expectedPrompt), eq(1200), eq(60), eq("BriefDigest"))).thenReturn(null);

		// When:
		String result = client.digestBriefIfOversized(oversized);

		// Then: the copy still gets the campaign facts — losing the brief entirely would be worse than carrying it
		assertThat(result).isEqualTo(oversized);
	}

	@Test
	void batchTacticConclusionsAsksForOverviewsOnlyAtTheOverviewSizedBudgetTest() throws Exception {
		// Given: two tactics batched into one chunk by the default chunk size
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic display = new Tactic(
				"Display", "Display", null,
				1500.0, 251_633.0, 5257.0, 0.0, 2.09, null, null, null,
				1500.0, 250_000.0, 0.17, null, null, null, null, null);
		Tactic video = new Tactic(
				"Video", "Video", null,
				1500.0, 125_219.0, 760.0, 117_277.0, 0.61, 93.66, null, null,
				1500.0, 125_000.0, 0.35, 60.0, null, null, null, null);
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		tactics.put(1, display);
		tactics.put(2, video);
		CampaignData data = new CampaignData(
				"Clean Habits", "Culture Pilot", "CA, OR", "Consideration", "Jun 1 - Jun 30",
				null, "$3,000", "Imps, CTR, VCR", "Display, Video", "25+", "Eco-conscious",
				new Totals(0, 0, 0, 0, null, null), tactics, null);
		String brief = "Launch Clean Habits across Display and Video.";
		List<Integer> tacticNums = List.of(1, 2);

		String expectedPrompt =
				promptBuilder.buildTacticConclusionsPrompt(data, tacticNums, brief).orElseThrow();
		JsonNode response = json.readTree("""
				{
				  "tactic_1": {"overview": "Display beat its CTR benchmark twelvefold."},
				  "tactic_2": {"overview": "Video held a 93.66% VCR against a 60% plan."}
				}
				""");
		List<ClaudeCompressionField> expectedFields = List.of(
				new ClaudeCompressionField("1_overview", "Display beat its CTR benchmark twelvefold.", 210),
				new ClaudeCompressionField("2_overview", "Video held a 93.66% VCR against a 60% plan.", 210));
		// Two tactics → budget is base 400 + 2 × 300 = 1000, sized for two overviews and nothing else.
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(1000), eq(90), eq("BatchConclusions"), eq(true)))
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
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, tacticNums, brief, Map.of());

		// Then: both tactics come back from the single call
		assertThat(conclusions).hasSize(2);
		assertThat(conclusions.getFirst().overview()).isEqualTo("Display beat its CTR benchmark twelvefold.");
		assertThat(conclusions.get(1).overview()).isEqualTo("Video held a 93.66% VCR against a 60% plan.");

		// Then: the prompt asks only for the overview — no section rule block is sent
		assertThat(expectedPrompt).contains("carrying exactly ONE field: \"overview\"");
		assertThat(expectedPrompt).doesNotContain("=== top_publishers");
		assertThat(expectedPrompt).doesNotContain("=== creative");
		assertThat(expectedPrompt).doesNotContain("=== geo");
		assertThat(expectedPrompt).doesNotContain("=== audience");
		assertThat(expectedPrompt).doesNotContain("=== device");
		verify(messagesClient, times(1))
				.callJsonObject(eq(expectedPrompt), eq(1000), eq(90), eq("BatchConclusions"), eq(true));
	}

	@Test
	void batchTacticConclusionsCarriesTheDailyPacingSeriesIntoTheEndOfMonthPromptTest() throws Exception {
		// Given: the end-of-month builder, one tactic, and that tactic's daily pacing series off the sheet
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		EomPromptBuilder promptBuilder = new EomPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic display = new Tactic(
				"Display", "Display", null,
				1500.0, 251_633.0, 5257.0, 0.0, 2.09, null, null, null,
				1500.0, 250_000.0, 0.17, null, null, null, null, null);
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		tactics.put(1, display);
		CampaignData data = new CampaignData(
				"Clean Habits", "Culture Pilot", "CA, OR", "Consideration", "Jun 1 - Jun 30",
				null, "$3,000", "Imps, CTR", "Display", "25+", "Eco-conscious",
				new Totals(0, 0, 0, 0, null, null), tactics, null);
		LinkedHashMap<String, double[]> days = new LinkedHashMap<>();
		days.put("Jun 1", new double[] {8_100, 30, 0});
		days.put("Jun 2", new double[] {9_400, 41, 0});
		Map<Integer, Pivot> daily = Map.of(1, new Pivot(days, true, false));
		String brief = "Launch Clean Habits across Display.";

		String expectedPrompt = promptBuilder
				.tacticConclusionsPrompt(data, List.of(1), brief, daily).orElseThrow();
		JsonNode response = json.readTree("""
				{"tactic_1": {"overview": "Display paced steadily to 101% of the month's impression target."}}
				""");
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(
				eq(List.of(new ClaudeCompressionField(
						"1_overview", "Display paced steadily to 101% of the month's impression target.", 210))),
				eq("BatchD-Conclusions")))
				.thenReturn(Map.of(
						"1_overview", "Display paced steadily to 101% of the month's impression target."));

		// When:
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, List.of(1), brief, daily);

		// Then: the series survived the chunking chain and reached the prompt the call actually sent
		assertThat(expectedPrompt).contains(
				"DAILY PACING (impressions per day, oldest first): Jun 1 8,100 | Jun 2 9,400");
		assertThat(conclusions).hasSize(1);
		verify(messagesClient, times(1))
				.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true));
	}

	@Test
	void batchTacticConclusionsReAsksATacticThatParsedWithABlankOverviewTest() throws Exception {
		// Given: one tactic whose reply parses into a conclusion object but leaves the overview blank — before
		// this was shipped straight to the slide as a dash, because only a wholly empty chunk was retried
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());

		Tactic display = new Tactic(
				"Display", "Display", null,
				1500.0, 251_633.0, 5257.0, 0.0, 2.09, null, null, null,
				1500.0, 250_000.0, 0.17, null, null, null, null, null);
		CampaignData data = new CampaignData(
				"Clean Habits", "Culture Pilot", "CA, OR", "Consideration", "Jun 1 - Jun 30",
				null, "$1,500", "Imps, CTR", "Display", "25+", "Eco-conscious",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, display), null);
		String brief = "Launch Clean Habits across Display.";
		List<Integer> tacticNums = List.of(1);

		String expectedPrompt =
				promptBuilder.buildTacticConclusionsPrompt(data, tacticNums, brief).orElseThrow();
		JsonNode blank = json.readTree("{\"tactic_1\": {\"overview\": \"\"}}");
		JsonNode filled = json.readTree(
				"{\"tactic_1\": {\"overview\": \"Display beat its CTR benchmark twelvefold.\"}}");
		when(messagesClient.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true)))
				.thenReturn(blank, filled);
		when(compressionService.compress(
				eq(List.of(new ClaudeCompressionField("1_overview", "", 210))), eq("BatchD-Conclusions")))
				.thenReturn(Map.of("1_overview", ""));
		when(compressionService.compress(
				eq(List.of(new ClaudeCompressionField(
						"1_overview", "Display beat its CTR benchmark twelvefold.", 210))),
				eq("BatchD-Conclusions")))
				.thenReturn(Map.of("1_overview", "Display beat its CTR benchmark twelvefold."));

		// When:
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, tacticNums, brief, Map.of());

		// Then: the blank answer is re-asked once and the slide gets the second reply's overview
		assertThat(conclusions).hasSize(1);
		assertThat(conclusions.getFirst().overview()).isEqualTo("Display beat its CTR benchmark twelvefold.");
		verify(messagesClient, times(2))
				.callJsonObject(eq(expectedPrompt), eq(700), eq(90), eq("BatchConclusions"), eq(true));
	}

	@Test
	void batchTacticConclusionsRunsItsChunksConcurrentlyAndReturnsThemInTacticOrderTest() throws Exception {
		// Given: four tactics sliced into two chunks, each call blocking until the other has started — so the
		// test only completes if the chunks really run at the same time rather than one after the other
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		AnthropicProperties props = new AnthropicProperties();
		props.setBreakdownChunkSize(2);
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(), props);

		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		for (int n = 1; n <= 4; n++) {
			tactics.put(n, new Tactic(
					"Display " + n, "Display", null,
					1500.0, 251_633.0, 5257.0, 0.0, 2.09, null, null, null,
					1500.0, 250_000.0, 0.17, null, null, null, null, null));
		}
		CampaignData data = new CampaignData(
				"Clean Habits", "Culture Pilot", "CA, OR", "Consideration", "Jun 1 - Jun 30",
				null, "$6,000", "Imps, CTR", "Display", "25+", "Eco-conscious",
				new Totals(0, 0, 0, 0, null, null), tactics, null);
		String brief = "Launch Clean Habits across four Display line items.";
		List<Integer> tacticNums = List.of(1, 2, 3, 4);

		String firstPrompt = promptBuilder.buildTacticConclusionsPrompt(
				data, tacticNums.subList(0, 2), brief).orElseThrow();
		String secondPrompt = promptBuilder.buildTacticConclusionsPrompt(
				data, tacticNums.subList(2, 4), brief).orElseThrow();
		CountDownLatch bothInFlight = new CountDownLatch(2);
		when(messagesClient.callJsonObject(eq(firstPrompt), eq(1000), eq(90), eq("BatchConclusions"), eq(true)))
				.thenAnswer(invocation -> {
					bothInFlight.countDown();
					assertThat(bothInFlight.await(5, TimeUnit.SECONDS)).isTrue();
					return json.readTree("""
							{"tactic_1": {"overview": "Tactic 1 overview."},
							 "tactic_2": {"overview": "Tactic 2 overview."}}
							""");
				});
		when(messagesClient.callJsonObject(eq(secondPrompt), eq(1000), eq(90), eq("BatchConclusions"), eq(true)))
				.thenAnswer(invocation -> {
					bothInFlight.countDown();
					assertThat(bothInFlight.await(5, TimeUnit.SECONDS)).isTrue();
					return json.readTree("""
							{"tactic_3": {"overview": "Tactic 3 overview."},
							 "tactic_4": {"overview": "Tactic 4 overview."}}
							""");
				});
		when(compressionService.compress(
				eq(List.of(
						new ClaudeCompressionField("1_overview", "Tactic 1 overview.", 210),
						new ClaudeCompressionField("2_overview", "Tactic 2 overview.", 210))),
				eq("BatchD-Conclusions")))
				.thenReturn(Map.of("1_overview", "Tactic 1 overview.", "2_overview", "Tactic 2 overview."));
		when(compressionService.compress(
				eq(List.of(
						new ClaudeCompressionField("3_overview", "Tactic 3 overview.", 210),
						new ClaudeCompressionField("4_overview", "Tactic 4 overview.", 210))),
				eq("BatchD-Conclusions")))
				.thenReturn(Map.of("3_overview", "Tactic 3 overview.", "4_overview", "Tactic 4 overview."));

		// When:
		List<TacticConclusion> conclusions = client.batchTacticConclusions(data, tacticNums, brief, Map.of());

		// Then: every tactic comes back, in tactic order rather than in whichever order the chunks finished
		assertThat(conclusions).extracting(TacticConclusion::tacticNum).containsExactly(1, 2, 3, 4);
		assertThat(conclusions).extracting(TacticConclusion::overview).containsExactly(
				"Tactic 1 overview.", "Tactic 2 overview.", "Tactic 3 overview.", "Tactic 4 overview.");
	}

	@Test
	void tacticPacingParsesTheFourNamedFieldsTest() throws Exception {
		// Given: a reply carrying all four pacing fields, and identity compression
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		TacticPacingInput input = new TacticPacingInput(1, "CTV", "VCR", List.of(
				new TacticPacingMetric("Impressions", "1,575,000", "1,602,341", "102%", "6,300,000", "6,409,364")));
		JsonNode response = json.readTree("""
				{"what_worked": "Living-room inventory carried completion.",
				 "watch_outs": "Frequency is climbing on the top decile.",
				 "actions": "Capping frequency at three per week.",
				 "next_month": "Hold CTV weight to reach the 40% goal."}
				""");
		when(messagesClient.callJsonObject(
				eq(promptBuilder.buildTacticPacingPrompt(input, "brief", 230, 140).orElseThrow()),
				eq(900), eq(90), eq("TacticPacingNarrative"), eq(true)))
				.thenReturn(response);
		when(compressionService.compress(org.mockito.ArgumentMatchers.anyList(), eq("TacticPacingNarrative")))
				.thenAnswer(invocation -> {
					List<ClaudeCompressionField> fields = invocation.getArgument(0);
					Map<String, String> out = new LinkedHashMap<>();
					for (ClaudeCompressionField field : fields) {
						out.put(field.key(), field.text());
					}
					return out;
				});

		// When:
		TacticPacing pacing = client.tacticPacing(input, "brief");

		// Then:
		assertThat(pacing).isNotNull();
		assertThat(pacing.tacticNum()).isEqualTo(1);
		assertThat(pacing.whatWorked()).isEqualTo("Living-room inventory carried completion.");
		assertThat(pacing.watchOuts()).isEqualTo("Frequency is climbing on the top decile.");
		assertThat(pacing.actions()).isEqualTo("Capping frequency at three per week.");
		assertThat(pacing.nextMonth()).isEqualTo("Hold CTV weight to reach the 40% goal.");
	}

	@Test
	void tacticPacingRejectsAReplyThatCarriesNoneOfTheFourFieldsTest() throws Exception {
		// Given: a well-formed but empty reply — the shape that would otherwise blank the slide silently
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		ClaudeBatchPromptBuilder promptBuilder = new ClaudeBatchPromptBuilder(normalizer, new Fmt());
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, new ReportClaudeDefaults(),
				new WorkbookGeoFilter(), new PromptTokenEstimator(), new ClaudeFailureLogImpl(),
				new AnthropicProperties());
		TacticPacingInput input = new TacticPacingInput(3, "Meta", null, List.of(
				new TacticPacingMetric("Spend", "$8,000", "$7,200", "90%", "$32,000", "$28,800")));
		when(messagesClient.callJsonObject(
				org.mockito.ArgumentMatchers.anyString(), eq(900), eq(90), eq("TacticPacingNarrative"), eq(true)))
				.thenReturn(json.readTree("{\"what_worked\": \"  \"}"));

		// When / Then: nothing usable, so the caller dashes the tokens instead of writing blanks over them
		assertThat(client.tacticPacing(input, "brief")).isNull();
		verifyNoInteractions(compressionService);
	}

	@Test
	void tacticPacingIsANoopWithoutAnInputTest() {
		ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
		RealClaudeClient client = new RealClaudeClient(
				messagesClient, new ClaudeBatchPromptBuilder(normalizer, new Fmt()), normalizer,
				compressionService, new ReportClaudeDefaults(), new WorkbookGeoFilter(),
				new PromptTokenEstimator(), new ClaudeFailureLogImpl(), new AnthropicProperties());

		assertThat(client.tacticPacing(null, "brief")).isNull();
		verifyNoInteractions(messagesClient);
	}

}
