package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable token counter shared by every thread working on one report-generation run.
 *
 * <p>One scope is created per job and handed to the worker threads the pipeline fans out to (the
 * five breakdown builders run concurrently), so all of them add into the same counters and the run
 * ends with a single total. Every field is atomic because those threads add concurrently.
 */
public class ClaudeUsageScope {

	private final AtomicLong inputTokens = new AtomicLong();
	private final AtomicLong outputTokens = new AtomicLong();
	private final AtomicLong cacheWriteTokens = new AtomicLong();
	private final AtomicLong cacheReadTokens = new AtomicLong();
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<String> model = new AtomicReference<>();

	/**
	 * Adds one Messages API reply's usage block to the run's running totals.
	 *
	 * @param input      plain input tokens the reply billed
	 * @param output     output tokens the reply billed
	 * @param cacheWrite input tokens written into the prompt cache
	 * @param cacheRead  input tokens served from the prompt cache
	 * @param usedModel  model that answered, or {@code null} when the reply did not say
	 */
	public void add(long input, long output, long cacheWrite, long cacheRead, String usedModel) {
		inputTokens.addAndGet(input);
		outputTokens.addAndGet(output);
		cacheWriteTokens.addAndGet(cacheWrite);
		cacheReadTokens.addAndGet(cacheRead);
		calls.incrementAndGet();
		if (usedModel != null && !usedModel.isBlank()) {
			model.set(usedModel);
		}
	}

	/**
	 * Takes the current totals as an immutable snapshot, for persisting at the end of the run.
	 *
	 * @return the accumulated usage
	 */
	public ClaudeUsage snapshot() {
		return new ClaudeUsage(
				inputTokens.get(),
				outputTokens.get(),
				cacheWriteTokens.get(),
				cacheReadTokens.get(),
				calls.get(),
				model.get());
	}
}
