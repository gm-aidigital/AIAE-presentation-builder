package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the Claude token counts stamped on a report job.
 *
 * <p>The columns are nullable — a job generated before token accounting existed, or one that made
 * no Claude call, carries nulls rather than zeros — so every reader goes through here instead of
 * dereferencing the entity directly. {@link #hasUsage} is what keeps those jobs out of averages,
 * where treating them as zero-token reports would understate the real per-report cost.
 */
@Component
@RequiredArgsConstructor
public class JobTokenUsage {

	private final ClaudeCostCalculator costCalculator;

	/**
	 * Tells whether a job carries recorded token counts.
	 *
	 * @param job the persisted report job
	 * @return true when the job took part in token accounting
	 */
	public boolean hasUsage(ReportJobEntity job) {
		return job.getClaudeCalls() != null && job.getClaudeCalls() > 0;
	}

	/**
	 * Plain (uncached) input tokens of one job.
	 *
	 * @param job the persisted report job
	 * @return the count, or 0 when unrecorded
	 */
	public long inputTokens(ReportJobEntity job) {
		return job.getInputTokens() == null ? 0L : job.getInputTokens();
	}

	/**
	 * Output tokens of one job.
	 *
	 * @param job the persisted report job
	 * @return the count, or 0 when unrecorded
	 */
	public long outputTokens(ReportJobEntity job) {
		return job.getOutputTokens() == null ? 0L : job.getOutputTokens();
	}

	/**
	 * Prompt-cache write tokens of one job.
	 *
	 * @param job the persisted report job
	 * @return the count, or 0 when unrecorded
	 */
	public long cacheWriteTokens(ReportJobEntity job) {
		return job.getCacheWriteTokens() == null ? 0L : job.getCacheWriteTokens();
	}

	/**
	 * Prompt-cache read tokens of one job.
	 *
	 * @param job the persisted report job
	 * @return the count, or 0 when unrecorded
	 */
	public long cacheReadTokens(ReportJobEntity job) {
		return job.getCacheReadTokens() == null ? 0L : job.getCacheReadTokens();
	}

	/**
	 * Every input-side token of one job — plain input plus both prompt-cache classes.
	 *
	 * @param job the persisted report job
	 * @return the total, or 0 when unrecorded
	 */
	public long allInputTokens(ReportJobEntity job) {
		return inputTokens(job) + cacheWriteTokens(job) + cacheReadTokens(job);
	}

	/**
	 * Every token one job consumed — input, output and both cache classes.
	 *
	 * @param job the persisted report job
	 * @return the total, or 0 when unrecorded
	 */
	public long totalTokens(ReportJobEntity job) {
		return allInputTokens(job) + outputTokens(job);
	}

	/**
	 * Number of Anthropic API calls the job's run made.
	 *
	 * @param job the persisted report job
	 * @return the count, or 0 when unrecorded
	 */
	public int calls(ReportJobEntity job) {
		return job.getClaudeCalls() == null ? 0 : job.getClaudeCalls();
	}

	/**
	 * Estimated cost of one job at the configured list prices for the model it billed against.
	 *
	 * @param job the persisted report job
	 * @return the cost in USD, or 0 when the job carries no counts
	 */
	public double costUsd(ReportJobEntity job) {
		return costCalculator.costUsd(
				inputTokens(job), outputTokens(job), cacheWriteTokens(job), cacheReadTokens(job),
				job.getClaudeModel());
	}
}
