package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaudeCompressionServiceTest {

	@Mock
	private AnthropicMessagesClient messagesClient;

	@Mock
	private ClaudeBatchPromptBuilder promptBuilder;

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void shouldSkipClaudeCallWhenAllFieldsFitWithinBudgetTest() {
		// Given:
		ClaudeCompressionService service = new ClaudeCompressionService(messagesClient, promptBuilder);
		List<ClaudeCompressionField> fields = List.of(new ClaudeCompressionField("point_0", "short", 22));

		// When:
		Map<String, String> result = service.compress(fields, "BatchD-Test");

		// Then:
		assertThat(result).containsEntry("point_0", "short");
		verifyNoInteractions(messagesClient, promptBuilder);
	}

	@Test
	void shouldReplaceOnlyOversizedFieldWithCompressedTextTest() throws Exception {
		// Given:
		ClaudeCompressionService service = new ClaudeCompressionService(messagesClient, promptBuilder);
		String longOverview = "x".repeat(300);
		List<ClaudeCompressionField> fields = List.of(
				new ClaudeCompressionField("point_0", "short", 22),
				new ClaudeCompressionField("overview_0", longOverview, 240)
		);
		when(promptBuilder.buildCompressionPrompt(List.of(fields.get(1)))).thenReturn(Optional.of("prompt"));
		JsonNode response = json.readTree("{\"overview_0\": \"shrunk but meaningful\"}");
		when(messagesClient.callJsonObject(eq("prompt"), eq(1200), eq(30), eq("BatchD-Test"), eq(false)))
				.thenReturn(response);

		// When:
		Map<String, String> result = service.compress(fields, "BatchD-Test");

		// Then:
		assertThat(result).containsEntry("point_0", "short");
		assertThat(result).containsEntry("overview_0", "shrunk but meaningful");
	}

	@Test
	void shouldKeepOriginalTextWhenClaudeCallFailsTest() {
		// Given:
		ClaudeCompressionService service = new ClaudeCompressionService(messagesClient, promptBuilder);
		String longOverview = "y".repeat(300);
		List<ClaudeCompressionField> fields = List.of(new ClaudeCompressionField("overview_0", longOverview, 240));
		when(promptBuilder.buildCompressionPrompt(fields)).thenReturn(Optional.of("prompt"));
		when(messagesClient.callJsonObject(eq("prompt"), eq(1200), eq(30), eq("BatchD-Test"), eq(false)))
				.thenReturn(null);

		// When:
		Map<String, String> result = service.compress(fields, "BatchD-Test");

		// Then:
		assertThat(result).containsEntry("overview_0", longOverview);
	}
}
