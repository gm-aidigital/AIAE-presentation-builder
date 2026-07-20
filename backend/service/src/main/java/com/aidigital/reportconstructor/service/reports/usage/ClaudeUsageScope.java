package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Who a stretch of Claude work belongs to, and the tokens it has run up so far.
 *
 * <p>One scope is created per unit of work and handed to the worker threads it fans out to (the
 * five breakdown builders run concurrently), so all of them add into the same counters and the work
 * ends with a single total. Every counter is atomic because those threads add concurrently.
 *
 * <p>{@link #getJobId()} is {@code null} for Claude work that belongs to no report — the line-item
 * match runs inside a web request — which is exactly why the per-call event table exists alongside
 * the per-job totals.
 */
@Getter
public class ClaudeUsageScope {

	/** Report job this work belongs to, or {@code null} when it belongs to none. */
	private final Long jobId;

	private final String ownerUserId;

	private final String ownerEmail;

	private final AtomicLong inputTokens = new AtomicLong();
	private final AtomicLong outputTokens = new AtomicLong();
	private final AtomicLong cacheWriteTokens = new AtomicLong();
	private final AtomicLong cacheReadTokens = new AtomicLong();
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<String> model = new AtomicReference<>();

	/**
	 * Creates a scope for one unit of Claude work.
	 *
	 * @param jobId       report job the work belongs to, or {@code null}
	 * @param ownerUserId internal id of the user the work is for, or {@code null}
	 * @param ownerEmail  email of that user, or {@code null}
	 */
	public ClaudeUsageScope(Long jobId, String ownerUserId, String ownerEmail) {
		this.jobId = jobId;
		this.ownerUserId = ownerUserId;
		this.ownerEmail = ownerEmail;
	}

	/**
	 * Adds one Messages API reply's usage block to the running totals.
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
	 * Takes the current totals as an immutable snapshot, for persisting when the work ends.
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
