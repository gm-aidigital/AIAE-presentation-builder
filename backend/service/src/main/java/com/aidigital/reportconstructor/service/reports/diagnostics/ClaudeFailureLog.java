package com.aidigital.reportconstructor.service.reports.diagnostics;

import java.util.function.Supplier;

/**
 * Ambient collection of the reasons Claude replies were rejected, so a degraded report can explain itself
 * on its own result card instead of only in the server log.
 *
 * <p>Bound to a thread exactly like {@code ClaudeUsageTracker}, and for the same reason: the Anthropic
 * transport sits several layers below the code that knows which report is being built, and threading a
 * diagnostics sink through every batch signature would touch every prompt method for an observational
 * concern. The caller opens a scope, the transport records into whatever scope is bound, and the caller
 * reads the reasons back when the run ends.
 *
 * <p>Recording outside a scope is a no-op, so Claude work that belongs to no report — the line-item match
 * that runs inside a web request — costs nothing here.
 */
public interface ClaudeFailureLog {

	/**
	 * Opens a fresh collection scope on the calling thread, replacing any scope already bound.
	 *
	 * @return the scope now bound, so it can be handed to worker threads
	 */
	ClaudeFailureScope begin();

	/**
	 * Returns the scope bound to the calling thread.
	 *
	 * @return the bound scope, or {@code null} when this thread is not inside one
	 */
	ClaudeFailureScope current();

	/**
	 * Unbinds the calling thread's scope. Safe to call when nothing is bound.
	 */
	void clear();

	/**
	 * Records one rejected reply against the bound scope, prefixed with the call it belongs to. Does
	 * nothing when no scope is bound.
	 *
	 * @param label  batch tag the call was logged under, e.g. {@code PublisherSection}
	 * @param detail what was wrong, in words the person who ran the report can act on
	 */
	void record(String label, String detail);

	/**
	 * Wraps a task so it runs on a worker thread with {@code scope} bound, then unbinds it again. Used for
	 * the section calls and breakdown builders the pipeline fans out concurrently, whose rejections would
	 * otherwise be dropped because the scope lives on the thread that started the run.
	 *
	 * @param scope the scope to bind, or {@code null} to run the task with none
	 * @param task  the work to run
	 * @param <T>   the task's result type
	 * @return a supplier that binds the scope around {@code task}
	 */
	<T> Supplier<T> inScope(ClaudeFailureScope scope, Supplier<T> task);
}
