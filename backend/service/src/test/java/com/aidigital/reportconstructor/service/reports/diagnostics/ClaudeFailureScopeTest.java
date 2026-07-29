package com.aidigital.reportconstructor.service.reports.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeFailureScopeTest {

	@Test
	void shouldKeepTheReasonsInTheOrderTheyHappenedTest() {
		// Given: a run whose first section was rejected, then gave up
		ClaudeFailureScope scope = new ClaudeFailureScope();

		// When:
		scope.add("Claude PublisherSection: tactic 1 — the reply held 1 item(s), expected 4.");
		scope.add("Claude PublisherSection: tactic 1 gave up after 2 attempt(s).");

		// Then: the card reads the cause before the consequence
		assertThat(scope.snapshot()).containsExactly(
				"Claude PublisherSection: tactic 1 — the reply held 1 item(s), expected 4.",
				"Claude PublisherSection: tactic 1 gave up after 2 attempt(s).");
	}

	@Test
	void shouldDropBlankReasonsTest() {
		// Given: a scope asked to record nothing useful
		ClaudeFailureScope scope = new ClaudeFailureScope();

		// When:
		scope.add(null);
		scope.add("   ");

		// Then: no empty bullet ever reaches the report card
		assertThat(scope.snapshot()).isEmpty();
	}

	@Test
	void shouldCapWhatOneRunCanPutOnTheCardTest() {
		// Given: a deck where every section of every tactic was rejected, and one very long reply head —
		// the warnings are serialized onto the job and rendered as a bullet list, so neither may run away
		ClaudeFailureScope scope = new ClaudeFailureScope();

		// When:
		for (int i = 0; i < 100; i++) {
			scope.add("reason " + i);
		}
		scope.add("x".repeat(5000));

		// Then: the entry count is capped, and nothing past the cap — including the long one — is kept
		List<String> entries = scope.snapshot();
		assertThat(entries).hasSize(40);
		assertThat(entries.getFirst()).isEqualTo("reason 0");
		assertThat(entries.getLast()).isEqualTo("reason 39");
	}

	@Test
	void shouldTrimAnOverlongReasonTest() {
		// Given: a scope carrying one rejection whose reply head is longer than a card bullet should be
		ClaudeFailureScope scope = new ClaudeFailureScope();

		// When:
		scope.add("y".repeat(5000));

		// Then: the reason is kept, shortened, and marked as shortened
		assertThat(scope.snapshot()).hasSize(1);
		assertThat(scope.snapshot().getFirst()).hasSize(601).endsWith("…");
	}
}
