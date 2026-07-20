package com.aidigital.reportconstructor.service.reports.usage;

import java.util.function.Supplier;

/**
 * Ambient per-run Claude token accounting.
 *
 * <p>The Anthropic transport sits several layers below the code that knows which report job is
 * being built, and threading the job id through every batch signature would touch every prompt
 * method for a purely observational concern. Instead the pipeline opens a scope on its worker
 * thread, the transport adds each reply's usage block to whatever scope is bound, and the pipeline
 * reads the total back when the run ends. A call made outside any scope — a line-item match served
 * inside a web request, say — records nothing rather than being misattributed.
 */
public interface ClaudeUsageTracker {

	/**
	 * Opens a fresh accounting scope on the calling thread, replacing any scope already bound.
	 *
	 * @return the scope now bound, so it can be handed to worker threads
	 */
	ClaudeUsageScope begin();

	/**
	 * Returns the scope bound to the calling thread.
	 *
	 * @return the bound scope, or {@code null} when this thread is not inside a run
	 */
	ClaudeUsageScope current();

	/**
	 * Unbinds the calling thread's scope. Safe to call when nothing is bound.
	 */
	void clear();

	/**
	 * Adds one Messages API reply's usage to the calling thread's scope; a no-op outside a scope.
	 *
	 * @param input      plain input tokens the reply billed
	 * @param output     output tokens the reply billed
	 * @param cacheWrite input tokens written into the prompt cache
	 * @param cacheRead  input tokens served from the prompt cache
	 * @param model      model that answered, or {@code null} when the reply did not say
	 */
	void record(long input, long output, long cacheWrite, long cacheRead, String model);

	/**
	 * Wraps a task so it runs on a worker thread with {@code scope} bound, then unbinds it again.
	 * Used for the breakdown builders the pipeline fans out concurrently, whose Claude calls would
	 * otherwise go unaccounted because the scope lives on the thread that started the run.
	 *
	 * @param scope the run's scope, or {@code null} to run the task with no accounting
	 * @param task  the work to run
	 * @param <T>   the task's result type
	 * @return a supplier that binds the scope around {@code task}
	 */
	<T> Supplier<T> inScope(ClaudeUsageScope scope, Supplier<T> task);
}
