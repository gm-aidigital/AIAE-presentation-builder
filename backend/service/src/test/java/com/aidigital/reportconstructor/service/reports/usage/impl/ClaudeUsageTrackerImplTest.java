package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeUsageTrackerImplTest {

	@Test
	void shouldAccumulateRecordedUsageWithinAScopeTest() {
		// Given:
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl();
		ClaudeUsageScope scope = tracker.begin();

		// When:
		tracker.record(100, 20, 5, 1, "claude-sonnet-4-6");
		tracker.record(200, 30, 0, 7, "claude-sonnet-4-6");

		// Then:
		ClaudeUsage usage = scope.snapshot();
		assertThat(usage.inputTokens()).isEqualTo(300);
		assertThat(usage.outputTokens()).isEqualTo(50);
		assertThat(usage.cacheWriteTokens()).isEqualTo(5);
		assertThat(usage.cacheReadTokens()).isEqualTo(8);
		assertThat(usage.calls()).isEqualTo(2);
		assertThat(usage.model()).isEqualTo("claude-sonnet-4-6");
	}

	@Test
	void shouldIgnoreUsageRecordedOutsideAnyScopeTest() {
		// Given: a tracker whose thread has no scope bound — a line-item match served in a web request.
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl();

		// When-Then: recording is a silent no-op rather than a misattribution or a failure.
		tracker.record(100, 20, 0, 0, "claude-sonnet-4-6");
		assertThat(tracker.current()).isNull();
	}

	@Test
	void shouldCarryTheScopeOntoWorkerThreadsTest() throws Exception {
		// Given: a run's scope, and five tasks that will each run on a different thread, as the five
		// breakdown builders do.
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl();
		ClaudeUsageScope scope = tracker.begin();
		var executor = Executors.newFixedThreadPool(5);

		// When:
		List<CompletableFuture<String>> futures = List.of(1, 2, 3, 4, 5).stream()
				.map(i -> {
					Supplier<String> task = () -> {
						tracker.record(10, 2, 0, 0, "claude-sonnet-4-6");
						return "done";
					};
					return CompletableFuture.supplyAsync(tracker.inScope(scope, task), executor);
				})
				.toList();
		futures.forEach(CompletableFuture::join);
		executor.shutdown();

		// Then: every worker's tokens landed in the run's single total.
		ClaudeUsage usage = scope.snapshot();
		assertThat(usage.calls()).isEqualTo(5);
		assertThat(usage.inputTokens()).isEqualTo(50);
		assertThat(usage.outputTokens()).isEqualTo(10);
	}

	@Test
	void shouldRestoreThePreviousScopeAfterAWrappedTaskTest() {
		// Given: a task wrapped for one scope, run on a thread that already carries another.
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl();
		ClaudeUsageScope outer = tracker.begin();
		ClaudeUsageScope inner = new ClaudeUsageScope();

		// When:
		tracker.inScope(inner, () -> {
			tracker.record(10, 1, 0, 0, "claude-sonnet-4-6");
			return "done";
		}).get();
		tracker.record(5, 1, 0, 0, "claude-sonnet-4-6");

		// Then: the wrapped call billed the inner scope, the following one the outer.
		assertThat(inner.snapshot().inputTokens()).isEqualTo(10);
		assertThat(outer.snapshot().inputTokens()).isEqualTo(5);
	}

	@Test
	void shouldUnbindTheScopeOnClearTest() {
		// Given:
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl();
		tracker.begin();

		// When:
		tracker.clear();

		// Then:
		assertThat(tracker.current()).isNull();
	}
}
