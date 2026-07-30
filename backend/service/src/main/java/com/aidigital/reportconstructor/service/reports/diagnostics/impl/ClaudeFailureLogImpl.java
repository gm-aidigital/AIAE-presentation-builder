package com.aidigital.reportconstructor.service.reports.diagnostics.impl;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureScope;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Thread-local {@link ClaudeFailureLog}. The scope object itself is thread-safe, so binding the same
 * instance on several worker threads is how one run's concurrent section calls end up in one list.
 */
@Component
public class ClaudeFailureLogImpl implements ClaudeFailureLog {

	private final ThreadLocal<ClaudeFailureScope> bound = new ThreadLocal<>();

	@Override
	public ClaudeFailureScope begin() {
		ClaudeFailureScope scope = new ClaudeFailureScope();
		bound.set(scope);
		return scope;
	}

	@Override
	public ClaudeFailureScope current() {
		return bound.get();
	}

	@Override
	public void clear() {
		bound.remove();
	}

	@Override
	public void record(String label, String detail) {
		ClaudeFailureScope scope = bound.get();
		if (scope != null) {
			scope.add("Claude " + label + ": " + detail);
		}
	}

	@Override
	public <T> Supplier<T> inScope(ClaudeFailureScope scope, Supplier<T> task) {
		if (scope == null) {
			return task;
		}
		return () -> {
			ClaudeFailureScope previous = bound.get();
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
