package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageScope;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageTracker;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Thread-local {@link ClaudeUsageTracker}. The scope object itself is thread-safe, so binding the
 * same instance on several worker threads is exactly how one run's concurrent Claude calls end up
 * in one total.
 */
@Component
public class ClaudeUsageTrackerImpl implements ClaudeUsageTracker {

	private final ThreadLocal<ClaudeUsageScope> bound = new ThreadLocal<>();

	@Override
	public ClaudeUsageScope begin() {
		ClaudeUsageScope scope = new ClaudeUsageScope();
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
	public void record(long input, long output, long cacheWrite, long cacheRead, String model) {
		ClaudeUsageScope scope = bound.get();
		if (scope != null) {
			scope.add(input, output, cacheWrite, cacheRead, model);
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
