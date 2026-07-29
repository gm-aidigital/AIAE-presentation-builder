package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealLineItemMatchAssistantTest {

	@Mock
	private AnthropicMessagesClient messagesClient;

	@Test
	void shouldKeepEveryCandidateWhenThePromptAlreadyFitsTheBudgetTest() {
		// Given: two candidates in one channel — far inside the 2000-token prompt budget
		RealLineItemMatchAssistant assistant =
				new RealLineItemMatchAssistant(messagesClient, new PromptTokenEstimator());
		List<LineItemMatchTactic> tactics = List.of(
				new LineItemMatchTactic(1, "Programmatic Display", "Display", "Whitelist Strategy"));
		List<LineItemMatchOption> options = List.of(
				new LineItemMatchOption("616641", "Display", "Acme_Display_WhiteList"),
				new LineItemMatchOption("616642", "Display", "Acme_Display_Contextual"));

		// When:
		List<LineItemMatchOption> fitted = assistant.fitOptions(tactics, options);

		// Then:
		assertThat(fitted).containsExactlyInAnyOrderElementsOf(options);
	}

	@Test
	void shouldDropTheLeastRelevantCandidatesToFitThePromptBudgetTest() {
		// Given: one tactic and 3000 candidates — the raw list would blow the model's context window
		RealLineItemMatchAssistant assistant =
				new RealLineItemMatchAssistant(messagesClient, new PromptTokenEstimator());
		List<LineItemMatchTactic> tactics = List.of(
				new LineItemMatchTactic(1, "Programmatic Display", "Display", "Whitelist Strategy"));
		List<LineItemMatchOption> options = new ArrayList<>();
		for (int i = 0; i < 3000; i++) {
			options.add(new LineItemMatchOption("60" + i, "Display", "Acme_Display_Evergreen_Segment_" + i));
		}
		options.add(new LineItemMatchOption("999999", "Display", "Acme_Display_Whitelist_Strategy"));

		// When:
		List<LineItemMatchOption> fitted = assistant.fitOptions(tactics, options);

		// Then: the prompt fits the budget, and the candidate that actually matches the tactic survives
		assertThat(fitted).hasSizeLessThan(options.size());
		assertThat(fitted.get(0).id()).isEqualTo("999999");
		assertThat(new PromptTokenEstimator().fitsWithin(assistant.buildPrompt(tactics, fitted), 2000)).isTrue();
	}

	@Test
	void shouldSendOnlyTheFittedCandidatesToClaudeTest() throws Exception {
		// Given: more candidates than the budget allows and a reply assigning the tactic
		RealLineItemMatchAssistant assistant =
				new RealLineItemMatchAssistant(messagesClient, new PromptTokenEstimator());
		List<LineItemMatchTactic> tactics = List.of(
				new LineItemMatchTactic(1, "Programmatic Display", "Display", "Whitelist Strategy"));
		List<LineItemMatchOption> options = new ArrayList<>();
		options.add(new LineItemMatchOption("999999", "Display", "Acme_Display_Whitelist_Strategy"));
		for (int i = 0; i < 3000; i++) {
			options.add(new LineItemMatchOption("60" + i, "Display", "Acme_Display_Evergreen_Segment_" + i));
		}
		JsonNode reply = new ObjectMapper().readTree("{\"1\":\"999999\"}");
		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		when(messagesClient.callJsonObject(
				prompt.capture(), eq(1024), eq(30), eq("LineItemMatch"), eq(true))).thenReturn(reply);

		// When:
		Map<Integer, String> matched = assistant.match(tactics, options);

		// Then: the assignment is kept and the prompt that was sent stayed inside the budget
		assertThat(matched).containsExactly(Map.entry(1, "999999"));
		verify(messagesClient).callJsonObject(
				prompt.getValue(), 1024, 30, "LineItemMatch", true);
		assertThat(new PromptTokenEstimator().fitsWithin(prompt.getValue(), 2000)).isTrue();
	}
}
