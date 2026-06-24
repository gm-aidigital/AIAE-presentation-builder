package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude Batch D: a second pass that asks Claude to shrink placeholder text that came back from Batch A/B/C
 * longer than its character budget, preserving meaning, before the hard-truncation safety net in
 * {@link ClaudeResponseNormalizer} runs as a final guarantee.
 */
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class ClaudeCompressionService {

	private static final int MAX_TOKENS = 1200;
	private static final int TIMEOUT_SECONDS = 30;

	private final AnthropicMessagesClient messagesClient;
	private final ClaudeBatchPromptBuilder promptBuilder;

	/**
	 * Creates the service.
	 *
	 * @param messagesClient low-level Anthropic Messages API transport used to send the compression prompt
	 * @param promptBuilder  builds the compression prompt text from the oversized fields
	 */
	public ClaudeCompressionService(AnthropicMessagesClient messagesClient, ClaudeBatchPromptBuilder promptBuilder) {
		this.messagesClient = messagesClient;
		this.promptBuilder = promptBuilder;
	}

	/**
	 * Shrinks every field whose text exceeds its character budget, preserving meaning. Fields already within
	 * budget are returned unchanged and are never sent to Claude, keeping this pass cheap.
	 *
	 * @param fields candidate fields, each with its own raw text and character budget
	 * @param label  short tag identifying this call in log messages (e.g. {@code "BatchD-Strategic"})
	 * @return every field's key mapped to its text — compressed where Claude responded, otherwise the original
	 * text untouched, ready for the hard-truncation safety net
	 */
	public Map<String, String> compress(List<ClaudeCompressionField> fields, String label) {
		Map<String, String> result = new LinkedHashMap<>();
		List<ClaudeCompressionField> oversized = new ArrayList<>();
		for (ClaudeCompressionField field : fields) {
			result.put(field.key(), field.text());
			if (field.text() != null && field.text().length() > field.maxChars()) {
				oversized.add(field);
			}
		}
		if (oversized.isEmpty()) {
			return result;
		}

		var prompt = promptBuilder.buildCompressionPrompt(oversized);
		if (prompt.isEmpty()) {
			return result;
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), MAX_TOKENS, TIMEOUT_SECONDS, label, false);
		if (parsed == null) {
			return result;
		}
		for (ClaudeCompressionField field : oversized) {
			JsonNode value = parsed.get(field.key());
			if (value != null && value.isTextual() && !value.asText().isBlank()) {
				result.put(field.key(), value.asText().trim());
			}
		}
		return result;
	}
}
