package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
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
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService());
		ClaudeUsageScope scope = tracker.begin(7L, "user-1", "jane@aidigital.com");

		// When:
		tracker.record("BatchC", 100, 20, 5, 1, "claude-sonnet-4-6");
		tracker.record("BatchC", 200, 30, 0, 7, "claude-sonnet-4-6");

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
	void shouldStampTheScopeJobAndOwnerOntoEachEventTest() {
		// Given:
		NoOpClaudeUsageEventService events = new NoOpClaudeUsageEventService();
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(events);
		tracker.begin(7L, "user-1", "jane@aidigital.com");

		// When:
		tracker.record("BatchGeo", 100, 20, 0, 0, "claude-sonnet-4-6");

		// Then:
		assertThat(events.listAll()).hasSize(1);
		ClaudeUsageEventEntity event = events.listAll().getFirst();
		assertThat(event.getJobId()).isEqualTo(7L);
		assertThat(event.getOwnerUserId()).isEqualTo("user-1");
		assertThat(event.getOwnerEmail()).isEqualTo("jane@aidigital.com");
		assertThat(event.getLabel()).isEqualTo("BatchGeo");
		assertThat(event.getStatus()).isEqualTo(ClaudeUsageStatus.RECORDED.getCode());
		assertThat(event.getInputTokens()).isEqualTo(100);
	}

	@Test
	void shouldStillRecordACallMadeOutsideAnyScopeTest() {
		// Given: a thread with no scope bound — Claude work that belongs to no report. Dropping it would
		// make billed traffic invisible, which is worse than leaving it unattributed.
		NoOpClaudeUsageEventService events = new NoOpClaudeUsageEventService();
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(events);

		// When:
		tracker.record("LineItemMatch", 100, 20, 0, 0, "claude-sonnet-4-6");

		// Then:
		assertThat(tracker.current()).isNull();
		assertThat(events.listAll()).hasSize(1);
		assertThat(events.listAll().getFirst().getJobId()).isNull();
		assertThat(events.listAll().getFirst().getInputTokens()).isEqualTo(100);
	}

	@Test
	void shouldBookALostCallAsEstimatedAndKeepItOutOfTheMeasuredTotalTest() {
		// Given: a run whose second call timed out after the request had gone out.
		NoOpClaudeUsageEventService events = new NoOpClaudeUsageEventService();
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(events);
		ClaudeUsageScope scope = tracker.begin(7L, "user-1", "jane@aidigital.com");

		// When:
		tracker.record("BatchC", 100, 20, 0, 0, "claude-sonnet-4-6");
		tracker.recordEstimated("BatchC", 4000, "claude-sonnet-4-6");

		// Then: the estimate is persisted as its own flagged event, but the job's stamped totals stay
		// a measured number rather than a mixture of fact and guess.
		assertThat(events.listAll()).hasSize(2);
		assertThat(events.listAll().get(1).getStatus()).isEqualTo(ClaudeUsageStatus.ESTIMATED.getCode());
		assertThat(events.listAll().get(1).getInputTokens()).isEqualTo(4000);
		assertThat(events.listAll().get(1).getOutputTokens()).isZero();
		assertThat(scope.snapshot().calls()).isEqualTo(1);
		assertThat(scope.snapshot().inputTokens()).isEqualTo(100);
	}

	@Test
	void shouldCarryTheScopeOntoWorkerThreadsTest() {
		// Given: a run's scope, and five tasks that will each run on a different thread, as the five
		// breakdown builders do.
		NoOpClaudeUsageEventService events = new NoOpClaudeUsageEventService();
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(events);
		ClaudeUsageScope scope = tracker.begin(7L, "user-1", "jane@aidigital.com");
		var executor = Executors.newFixedThreadPool(5);

		// When:
		List<CompletableFuture<String>> futures = List.of(1, 2, 3, 4, 5).stream()
				.map(i -> {
					Supplier<String> task = () -> {
						tracker.record("BatchGeo", 10, 2, 0, 0, "claude-sonnet-4-6");
						return "done";
					};
					return CompletableFuture.supplyAsync(tracker.inScope(scope, task), executor);
				})
				.toList();
		futures.forEach(CompletableFuture::join);
		executor.shutdown();

		// Then: every worker's tokens landed in the run's single total, and each event kept the job.
		ClaudeUsage usage = scope.snapshot();
		assertThat(usage.calls()).isEqualTo(5);
		assertThat(usage.inputTokens()).isEqualTo(50);
		assertThat(usage.outputTokens()).isEqualTo(10);
		assertThat(events.listAll()).allSatisfy(e -> assertThat(e.getJobId()).isEqualTo(7L));
	}

	@Test
	void shouldRestoreThePreviousScopeAfterAWrappedTaskTest() {
		// Given: a task wrapped for one scope, run on a thread that already carries another.
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService());
		ClaudeUsageScope outer = tracker.begin(1L, null, null);
		ClaudeUsageScope inner = new ClaudeUsageScope(2L, null, null);

		// When:
		tracker.inScope(inner, () -> {
			tracker.record("BatchC", 10, 1, 0, 0, "claude-sonnet-4-6");
			return "done";
		}).get();
		tracker.record("BatchC", 5, 1, 0, 0, "claude-sonnet-4-6");

		// Then: the wrapped call billed the inner scope, the following one the outer.
		assertThat(inner.snapshot().inputTokens()).isEqualTo(10);
		assertThat(outer.snapshot().inputTokens()).isEqualTo(5);
	}

	@Test
	void shouldUnbindTheScopeOnClearTest() {
		// Given:
		ClaudeUsageTrackerImpl tracker = new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService());
		tracker.begin(1L, null, null);

		// When:
		tracker.clear();

		// Then:
		assertThat(tracker.current()).isNull();
	}
}
