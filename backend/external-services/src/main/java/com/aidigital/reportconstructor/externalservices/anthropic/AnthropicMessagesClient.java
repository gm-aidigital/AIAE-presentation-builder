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
import java.util.ArrayDeque;
import java.util.Deque;
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
				JsonNode node = json.readTree(repairTruncatedJson(text));
				if (node != null && node.isObject()) {
					log.warn("[claude:{}] reply was truncated; recovered the complete part of the JSON", label);
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
	 * Rebuilds a parseable JSON object out of a reply the model stopped writing mid-way: drops the trailing
	 * incomplete value and closes every structure still open, so the entries that did arrive survive instead
	 * of the whole reply being thrown away.
	 *
	 * <p>Walks the text once tracking string state (honouring backslash escapes, so a quote inside a value
	 * does not read as the string's end) and the stack of open {@code {}/[]}. Only a structural boundary —
	 * a comma or a closing bracket outside a string — counts as a cut point, because a closing quote alone
	 * may well be a key still waiting for its value ({@code {"a":1,"b"}} does not parse). The bracket stack
	 * is snapshotted at each cut point rather than read at the end of the text, since brackets opened after
	 * the cut are about to be discarded and must not be closed.
	 *
	 * @param text the raw, unparseable reply text with its code fences already stripped
	 * @return JSON text holding the reply's complete entries; empty when nothing survived the cut, which the
	 * caller's parse then rejects
	 */
	String repairTruncatedJson(String text) {
		Deque<Character> open = new ArrayDeque<>();
		Deque<Character> openAtCut = new ArrayDeque<>();
		boolean inString = false;
		boolean escaped = false;
		// Last index (exclusive) at which the document was structurally complete enough to close off.
		int cut = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			switch (c) {
				case '"' -> inString = true;
				case '{', '[' -> open.push(c);
				case '}', ']' -> {
					if (!open.isEmpty()) {
						open.pop();
					}
					cut = i + 1;
					openAtCut = new ArrayDeque<>(open);
				}
				case ',' -> {
					cut = i + 1;
					openAtCut = new ArrayDeque<>(open);
				}
				default -> {
					// ':', digits, literals and whitespace leave the cut point where it was.
				}
			}
		}
		StringBuilder repaired = new StringBuilder(text.substring(0, cut));
		while (!repaired.isEmpty()) {
			char last = repaired.charAt(repaired.length() - 1);
			if (last == ',' || Character.isWhitespace(last)) {
				repaired.setLength(repaired.length() - 1);
			} else {
				break;
			}
		}
		if (repaired.isEmpty()) {
			return "";
		}
		while (!openAtCut.isEmpty()) {
			repaired.append(openAtCut.pop() == '{' ? '}' : ']');
		}
		return repaired.toString();
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
