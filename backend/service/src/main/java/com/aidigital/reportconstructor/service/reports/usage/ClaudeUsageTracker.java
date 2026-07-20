package com.aidigital.reportconstructor.service.reports.usage;

import java.util.function.Supplier;

/**
 * Ambient Claude token accounting.
 *
 * <p>The Anthropic transport sits several layers below the code that knows which report job is
 * being built, and threading the job id through every batch signature would touch every prompt
 * method for a purely observational concern. Instead the caller opens a scope on its thread, the
 * transport reports each call to whatever scope is bound, and the caller reads the total back when
 * the work ends.
 *
 * <p>Every call is persisted as its own event either way. Work done outside a scope — a line-item
 * match that opened none — is still recorded, just without a job or owner attached, because a call
 * that is billed and invisible is worse than one that is billed and merely unattributed.
 */
public interface ClaudeUsageTracker {

	/**
	 * Opens a fresh accounting scope on the calling thread, replacing any scope already bound.
	 *
	 * @param jobId       report job the work belongs to, or {@code null} when it belongs to none
	 * @param ownerUserId internal id of the user the work is for, or {@code null}
	 * @param ownerEmail  email of that user, or {@code null}
	 * @return the scope now bound, so it can be handed to worker threads
	 */
	ClaudeUsageScope begin(Long jobId, String ownerUserId, String ownerEmail);

	/**
	 * Returns the scope bound to the calling thread.
	 *
	 * @return the bound scope, or {@code null} when this thread is not inside one
	 */
	ClaudeUsageScope current();

	/**
	 * Unbinds the calling thread's scope. Safe to call when nothing is bound.
	 */
	void clear();

	/**
	 * Records a call whose reply arrived, with the token counts the API itself reported.
	 *
	 * @param label      batch tag the call was logged under, e.g. {@code BatchC}
	 * @param input      plain input tokens the reply billed
	 * @param output     output tokens the reply billed
	 * @param cacheWrite input tokens written into the prompt cache
	 * @param cacheRead  input tokens served from the prompt cache
	 * @param model      model that answered, or {@code null} when the reply did not say
	 */
	void record(String label, long input, long output, long cacheWrite, long cacheRead, String model);

	/**
	 * Records a call that was sent — and therefore billed — but whose reply never arrived, so its real
	 * token counts can never be known. Only the input side can be approximated, from the prompt that
	 * was sent; the output side is left at zero here and predicted at read time from what comparable
	 * calls actually returned. Such a call is deliberately not added to the scope's totals, so the
	 * figure stamped on the job stays a measured number rather than a mixture.
	 *
	 * @param label          batch tag the call was logged under
	 * @param estimatedInput locally estimated input tokens of the prompt that was sent
	 * @param model          model the call targeted
	 */
	void recordEstimated(String label, long estimatedInput, String model);

	/**
	 * Wraps a task so it runs on a worker thread with {@code scope} bound, then unbinds it again.
	 * Used for the breakdown builders the pipeline fans out concurrently, whose Claude calls would
	 * otherwise be recorded without their job because the scope lives on the thread that started the run.
	 *
	 * @param scope the scope to bind, or {@code null} to run the task with none
	 * @param task  the work to run
	 * @param <T>   the task's result type
	 * @return a supplier that binds the scope around {@code task}
	 */
	<T> Supplier<T> inScope(ClaudeUsageScope scope, Supplier<T> task);
}
