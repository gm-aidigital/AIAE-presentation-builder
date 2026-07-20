package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

/**
 * Thread-local {@link ClaudeUsageTracker}. The scope object itself is thread-safe, so binding the
 * same instance on several worker threads is exactly how one run's concurrent Claude calls end up
 * in one total.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeUsageTrackerImpl implements ClaudeUsageTracker {

	private final ThreadLocal<ClaudeUsageScope> bound = new ThreadLocal<>();

	private final ClaudeUsageEventService events;

	@Override
	public ClaudeUsageScope begin(Long jobId, String ownerUserId, String ownerEmail) {
		ClaudeUsageScope scope = new ClaudeUsageScope(jobId, ownerUserId, ownerEmail);
		bound.set(scope);
		return scope;
	}

	@Override
	public ClaudeUsageScope current() {
		return bound.get();
	}

	@Override
	public void clear() {
		bound.remove();
	}

	@Override
	public void record(String label, long input, long output, long cacheWrite, long cacheRead, String model) {
		ClaudeUsageScope scope = bound.get();
		if (scope != null) {
			scope.add(input, output, cacheWrite, cacheRead, model);
		}
		persist(scope, label, ClaudeUsageStatus.RECORDED, input, output, cacheWrite, cacheRead, model);
	}

	@Override
	public void recordEstimated(String label, long estimatedInput, String model) {
		persist(bound.get(), label, ClaudeUsageStatus.ESTIMATED, estimatedInput, 0, 0, 0, model);
	}

	/**
	 * Writes one usage event, tagging it with the bound scope's job and owner when there is one.
	 *
	 * <p>Accounting must never break the work it measures, so a failure to persist is logged and
	 * swallowed: the call it describes has already happened and the caller is waiting on its result.
	 *
	 * @param scope      the bound scope, or {@code null} when the call belongs to no job
	 * @param label      batch tag the call was logged under
	 * @param status     whether the counts are measured or estimated
	 * @param input      plain input tokens
	 * @param output     output tokens
	 * @param cacheWrite prompt-cache write tokens
	 * @param cacheRead  prompt-cache read tokens
	 * @param model      model the call billed against
	 */
	void persist(
			ClaudeUsageScope scope, String label, ClaudeUsageStatus status,
			long input, long output, long cacheWrite, long cacheRead, String model) {
		try {
			ClaudeUsageEventEntity event = new ClaudeUsageEventEntity();
			event.setJobId(scope == null ? null : scope.getJobId());
			event.setOwnerUserId(scope == null ? null : scope.getOwnerUserId());
			event.setOwnerEmail(scope == null ? null : scope.getOwnerEmail());
			event.setLabel(label == null || label.isBlank() ? "unknown" : label);
			event.setStatus(status.getCode());
			event.setInputTokens(input);
			event.setOutputTokens(output);
			event.setCacheWriteTokens(cacheWrite);
			event.setCacheReadTokens(cacheRead);
			event.setModel(model);
			event.setCreatedAt(OffsetDateTime.now());
			events.save(event);
		} catch (Exception ex) {
			log.warn("[claude:{}] usage event could not be recorded: {}", label, ex.getMessage());
		}
	}

	@Override
	public <T> Supplier<T> inScope(ClaudeUsageScope scope, Supplier<T> task) {
		if (scope == null) {
			return task;
		}
		return () -> {
			ClaudeUsageScope previous = bound.get();
			bound.set(scope);
			try {
				return task.get();
			} finally {
				if (previous == null) {
					bound.remove();
				} else {
					bound.set(previous);
				}
			}
		};
	}
}
