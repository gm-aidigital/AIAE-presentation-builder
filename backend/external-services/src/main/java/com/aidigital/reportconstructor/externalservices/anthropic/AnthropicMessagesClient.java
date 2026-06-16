package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Low-level Anthropic Messages API transport and JSON response parsing for Claude batches.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class AnthropicMessagesClient {

	private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
	private static final String VERSION = "2023-06-01";
	private static final Pattern FENCE_OPEN = Pattern.compile("^```(?:json)?\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern FENCE_CLOSE = Pattern.compile("\\s*```$");

	private final String apiKey;
	private final String model;
	private final HttpClient http;
	private final ObjectMapper json = new ObjectMapper();
	private final ClaudeResponseNormalizer normalizer;

	/**
	 * Creates the client, capturing the configured API key and target Claude model and building an
	 * HTTP client with a 15-second connect timeout.
	 *
	 * @param props      Anthropic configuration supplying the API key and model identifier
	 * @param normalizer helper that extracts the assistant text content from a Messages API response
	 */
	public AnthropicMessagesClient(AnthropicProperties props, ClaudeResponseNormalizer normalizer) {
		this.apiKey = props.getApiKey();
		this.model = props.getModel();
		this.normalizer = normalizer;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	}

	/**
	 * Sends a prompt and parses the model's JSON object from the text response, stripping any
	 * Markdown code fences and optionally attempting a best-effort repair of truncated output.
	 *
	 * @param prompt       the full user prompt sent as the single message to Claude
	 * @param maxTokens    cap on tokens the model may generate in its reply
	 * @param timeoutSec   per-request HTTP timeout in seconds
	 * @param label        short tag identifying this call in log messages
	 * @param allowPartial when {@code true}, accepts {@code max_tokens}-truncated output and tries to
	 *                     repair the trailing JSON by closing open braces
	 * @return parsed object node, or {@code null} on failure
	 */
	public JsonNode callJsonObject(
			String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {
		JsonNode resp = callRaw(prompt, maxTokens, timeoutSec, label);
		if (resp == null) {
			return null;
		}
		if ("max_tokens".equals(resp.path("stop_reason").asText("")) && !allowPartial) {
			log.warn("[claude:{}] truncated by max_tokens", label);
			return null;
		}
		String text = normalizer.extractText(resp);
		if (text == null || text.isBlank()) {
			return null;
		}
		text = FENCE_OPEN.matcher(text.trim()).replaceFirst("");
		text = FENCE_CLOSE.matcher(text).replaceFirst("").trim();
		try {
			JsonNode node = json.readTree(text);
			if (node != null && node.isObject()) {
				return node;
			}
		} catch (Exception ignored) {
			// fall through to repair attempt
		}
		if (allowPartial) {
			try {
				String fixed = text.replaceAll("[,\\s]+$", "") + "}}";
				JsonNode node = json.readTree(fixed);
				if (node != null && node.isObject()) {
					return node;
				}
			} catch (Exception ignored) {
				// give up
			}
		}
		log.warn("[claude:{}] JSON parse failed", label);
		return null;
	}

	/**
	 * Sends a prompt as a single user message to the Anthropic Messages API and returns the raw
	 * parsed JSON response body, or {@code null} on a non-200 status or transport failure.
	 *
	 * @param prompt     the full user prompt sent as the single message to Claude
	 * @param maxTokens  cap on tokens the model may generate in its reply
	 * @param timeoutSec per-request HTTP timeout in seconds
	 * @param label      short tag identifying this call in log messages
	 * @return the full Messages API response as a JSON tree, or {@code null} on failure
	 */
	public JsonNode callRaw(String prompt, int maxTokens, int timeoutSec, String label) {
		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"max_tokens", maxTokens,
					"messages", List.of(Map.of("role", "user", "content", prompt))
			);
			HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create(ENDPOINT))
					.timeout(Duration.ofSeconds(timeoutSec))
					.header("x-api-key", apiKey)
					.header("anthropic-version", VERSION)
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
					.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				log.error("[claude:{}] error {} body={}", label, res.statusCode(), res.body());
				return null;
			}
			return json.readTree(res.body());
		} catch (Exception ex) {
			log.error("[claude:{}] request failed: {}", label, ex.getMessage());
			return null;
		}
	}
}
