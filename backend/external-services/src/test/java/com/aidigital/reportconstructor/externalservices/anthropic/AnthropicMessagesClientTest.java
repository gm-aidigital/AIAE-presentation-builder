package com.aidigital.reportconstructor.externalservices.anthropic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicMessagesClientTest {

	@Test
	void shouldKeepTheCompleteBulletsOfATruncatedPerTacticReplyTest() {
		// Given: a per-tactic bullet reply the model stopped writing part-way through the fourth bullet —
		// the shape that used to blank a whole breakdown slide
		AnthropicProperties props = new AnthropicProperties();
		props.setApiKey("key");
		AnthropicMessagesClient client = new AnthropicMessagesClient(props, new ClaudeResponseNormalizer());
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
		AnthropicMessagesClient client = new AnthropicMessagesClient(props, new ClaudeResponseNormalizer());

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(props, new ClaudeResponseNormalizer());

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(props, new ClaudeResponseNormalizer());

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
		AnthropicMessagesClient client = new AnthropicMessagesClient(props, new ClaudeResponseNormalizer());

		// When:
		String repaired = client.repairTruncatedJson("{\"tactic_1\": [\"Unfinished bul");

		// Then: the caller's parse is left to reject it rather than being handed a lie
		assertThat(repaired).isEmpty();
	}
}
