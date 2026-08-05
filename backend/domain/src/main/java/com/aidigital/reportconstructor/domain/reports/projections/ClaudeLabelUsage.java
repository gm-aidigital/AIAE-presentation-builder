package com.aidigital.reportconstructor.domain.reports.projections;

/**
 * Claude spend for one (pipeline stage, usage status, model) combination, aggregated in the
 * database.
 *
 * <p>Replaces reading every row of {@code claude_usage_events} to add them up in Java: the table
 * grows by a few dozen rows per report and was the fastest-growing full-table read on the dashboard.
 * {@code status} stays in the grain because measured and estimated spend must never be summed
 * together, and {@code model} stays because cost is priced at read time.
 *
 * @param label            batch tag the calls were logged under, e.g. {@code BatchC}
 * @param status           {@code recorded} for measured calls, {@code estimated} for lost replies
 * @param model            model those calls billed against
 * @param calls            how many calls the combination covers
 * @param inputTokens      plain (uncached) input tokens
 * @param outputTokens     output tokens
 * @param cacheWriteTokens input tokens written into the prompt cache
 * @param cacheReadTokens  input tokens served from the prompt cache
 */
public record ClaudeLabelUsage(
		String label,
		String status,
		String model,
		Long calls,
		Long inputTokens,
		Long outputTokens,
		Long cacheWriteTokens,
		Long cacheReadTokens) {
}
