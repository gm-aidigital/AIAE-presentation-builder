package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnthropicMessagesClientTest {

	@Test
	void shouldKeepTheCompleteBulletsOfATruncatedPerTacticReplyTest() {
		// Given: a per-tactic bullet reply the model stopped writing part-way through the fourth bullet —
		// the shape that used to blank a whole breakdown slide
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());
		String truncated = "{\"tactic_1\": [\"First bullet.\", \"Second bullet.\", \"Third bullet.\", \"Fourth bul";

		// When:
		String repaired = client.repairTruncatedJson(truncated);

		// Then: the three finished bullets survive and the unfinished one is dropped
		assertThat(repaired)
				.isEqualTo("{\"tactic_1\": [\"First bullet.\", \"Second bullet.\", \"Third bullet.\"]}");
	}

	@Test
	void shouldNotCloseAKeyThatNeverGotItsValueTest() {
		// Given: a reply truncated right after a key — closing it as-is would produce {"a":1,"b"}, which
		// does not parse
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Done.\"], \"tactic_2\"");

		// Then: the dangling key is cut away with its comma, leaving the complete entry
		assertThat(repaired).isEqualTo("{\"tactic_1\": [\"Done.\"]}");
	}

	@Test
	void shouldCloseNestedStructuresOpenedAfterTheCutOnlyOnceTest() {
		// Given: a reply truncated inside a nested object, so the brackets open at the end of the text are
		// not the brackets open at the point the document is cut
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When:
		String repaired = client.repairTruncatedJson("{\"a\": [1,2], \"b\": {\"c\": 1");

		// Then: only the outer object is closed — the discarded inner one is not
		assertThat(repaired).isEqualTo("{\"a\": [1,2]}");
	}

	@Test
	void shouldTreatBracesInsideAStringAsTextTest() {
		// Given: a bullet quoting a placeholder token, whose braces must not be read as JSON structure
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Cell still reads {{cr_live_n}}.\", \"Sec");

		// Then: the finished bullet survives with its braces intact
		assertThat(repaired).isEqualTo("{\"tactic_1\": [\"Cell still reads {{cr_live_n}}.\"]}");
	}

	@Test
	void shouldReturnNothingWhenNoEntryEverCompletedTest() {
		// Given: a reply that was cut before its first entry closed — there is nothing to salvage
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Unfinished bul");

		// Then: the caller's parse is left to reject it rather than being handed a lie
		assertThat(repaired).isEmpty();
	}

	@Test
	void shouldRecoverAnObjectWrappedInProseTest() {
		// Given: a reply that put a preamble and trailing commentary around the object — the BatchPublishers
		// failure mode, where "Return ONLY a JSON object" was ignored and a plain parse rejected the whole
		// reply, blanking the slide and triggering the retry storm.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());
		String wrapped = "Here are the observations:\n\n{\"tactic_1\": [\"First.\", \"Second.\"]}\n\nThese show the mix.";

		// When:
		JsonNode node = client.parseJsonObject(wrapped, true);

		// Then: the object is salvaged from inside the prose.
		assertThat(node).isNotNull();
		assertThat(node.get("tactic_1").get(0).asText()).isEqualTo("First.");
	}

	@Test
	void shouldRecoverAProseWrappedObjectWhoseTailWasTruncatedTest() {
		// Given: a preamble, then an object the model stopped writing part-way through its last bullet.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());
		String wrapped = "Sure! {\"tactic_1\": [\"First.\", \"Second.\", \"Third.\", \"Fourth bul";

		// When:
		JsonNode node = client.parseJsonObject(wrapped, true);

		// Then: the three finished bullets survive.
		assertThat(node).isNotNull();
		assertThat(node.get("tactic_1")).hasSize(3);
	}

	@Test
	void shouldRejectABareArrayReplyTest() {
		// Given: a reply that is a JSON array rather than the keyed object the caller needs.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When-Then: a non-object never masquerades as a usable reply.
		assertThat(client.parseJsonObject("[\"a\", \"b\"]", true)).isNull();
	}

	@Test
	void shouldReturnNullForFreeTextWithNoObjectTest() {
		// Given: a reply with no JSON object at all — a refusal or a plain-prose answer.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When-Then:
		assertThat(client.parseJsonObject("I can't help with that request.", true)).isNull();
	}

	@Test
	void shouldFlattenAndCapTheReplySnippetTest() {
		// Given: a multi-line reply longer than the snippet cap.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());
		String reply = "line one\n\n   line two".repeat(60);

		// When:
		String snippet = client.snippet(reply);

		// Then: whitespace is collapsed to single spaces and the head is capped with an ellipsis.
		assertThat(snippet).doesNotContain("\n");
		assertThat(snippet).endsWith("…");
		assertThat(snippet.length()).isEqualTo(401);
	}

	@Test
	void shouldRetryTheTransientStatusesThatBlankedABatchTest() {
		// Given: the client. The 522 that wiped the PrimaryKpis fact columns is a Cloudflare edge timeout,
		// alongside the other transient upstream conditions worth another send.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When-Then: every transient upstream status is retried.
		assertThat(client.isTransientStatus(522)).isTrue();
		assertThat(client.isTransientStatus(529)).isTrue();
		assertThat(client.isTransientStatus(429)).isTrue();
		assertThat(client.isTransientStatus(500)).isTrue();
		assertThat(client.isTransientStatus(503)).isTrue();
		assertThat(client.isTransientStatus(504)).isTrue();
	}

	@Test
	void shouldNotRetryPermanentClientErrorsTest() {
		// Given: the client. A bad request or auth failure would only fail again, so it must fail fast.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When-Then: permanent statuses are never retried.
		assertThat(client.isTransientStatus(400)).isFalse();
		assertThat(client.isTransientStatus(401)).isFalse();
		assertThat(client.isTransientStatus(403)).isFalse();
		assertThat(client.isTransientStatus(404)).isFalse();
	}

	@Test
	void shouldSkipTheBackoffSleepWhenTheBaseDelayIsZeroTest() {
		// Given: a client configured with a zero base backoff, so a retry incurs no wait.
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		props.setRetryBackoffMillis(0);
		AnthropicMessagesClient client = new AnthropicMessagesClient(
				props, new ClaudeResponseNormalizer(), new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class)), new PromptTokenEstimator());

		// When: the backoff for a failed attempt is applied.
		long start = System.nanoTime();
		client.backoffBeforeRetry(3, "PrimaryKpis");
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		// Then: it returns immediately rather than sleeping.
		assertThat(elapsedMillis).isLessThan(50);
	}

	@Test
	void shouldRecordTheUsageBlockOfAReplyAgainstTheRunTest() throws Exception {
		// Given: a run's accounting scope, and a Messages API reply carrying a usage block
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class));
		var scope = tracker.begin(7L, null, null);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(props, new ClaudeResponseNormalizer(), tracker, new PromptTokenEstimator());
		JsonNode response = new ObjectMapper().readTree("""
				{"model": "claude-sonnet-4-6", "usage": {"input_tokens": 1200, "output_tokens": 340,
				"cache_creation_input_tokens": 90, "cache_read_input_tokens": 8000}}""");

		// When:
		client.recordUsage(response, "BatchC");

		// Then: every token class lands in the run's totals, kept apart for pricing
		var usage = scope.snapshot();
		assertThat(usage.inputTokens()).isEqualTo(1200);
		assertThat(usage.outputTokens()).isEqualTo(340);
		assertThat(usage.cacheWriteTokens()).isEqualTo(90);
		assertThat(usage.cacheReadTokens()).isEqualTo(8000);
		assertThat(usage.calls()).isEqualTo(1);
		assertThat(usage.model()).isEqualTo("claude-sonnet-4-6");
	}

	@Test
	void shouldIgnoreAReplyThatCarriesNoUsageBlockTest() throws Exception {
		// Given: a reply with no usage block — accounting must never break a request
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(mock(ClaudeUsageEventService.class));
		var scope = tracker.begin(7L, null, null);
		AnthropicMessagesClient client =
				new AnthropicMessagesClient(props, new ClaudeResponseNormalizer(), tracker, new PromptTokenEstimator());
		JsonNode response = new ObjectMapper().readTree("{\"content\": []}");

		// When:
		client.recordUsage(response, "BatchC");

		// Then:
		assertThat(scope.snapshot().calls()).isZero();
	}
}
