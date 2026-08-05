package com.aidigital.reportconstructor.service.admin.dto;

/**
 * Team-wide Claude token consumption for the dashboard's "Token consumption" tab.
 *
 * <p>Everything up to {@code avgCostPerReportUsd} is measured: it comes from the token counts the
 * API itself reported. The {@code unknown*}/{@code estimated*} fields are kept separate on purpose
 * — a handful of calls whose replies were lost to timeouts must not turn the measured total into a
 * guess, so the dashboard shows the fact and the prediction side by side rather than one blurred
 * number.
 *
 * <p>Averages are taken over {@code reportsWithUsage} only. Reports generated before token
 * accounting existed carry no counts, and dividing by them would understate the real per-report
 * cost. Calls made outside any report — the line-item match runs in a web request — are excluded
 * from the per-report averages and reported in the {@code unattributed*} fields instead.
 *
 * @param reportsWithUsage    reports that carry recorded token counts
 * @param claudeCalls         measured Anthropic API calls
 * @param inputTokens         plain (uncached) input tokens
 * @param outputTokens        output tokens
 * @param cacheWriteTokens    input tokens written into the prompt cache
 * @param cacheReadTokens     input tokens served from the prompt cache
 * @param totalTokens         every measured token above, summed
 * @param costUsd             estimated cost of the measured tokens at configured list prices
 * @param avgTokensPerReport  mean total tokens per report that carries usage
 * @param avgInputPerReport   mean input-side tokens (plain + cache) per such report
 * @param avgOutputPerReport  mean output tokens per such report
 * @param avgCostPerReportUsd mean estimated cost per such report
 * @param unknownCalls        calls that were billed but whose reply never arrived
 * @param estimatedTokens     predicted tokens of those calls — prompt size measured locally, output
 *                            predicted from what comparable calls actually returned
 * @param estimatedCostUsd    estimated cost of {@code estimatedTokens}
 * @param unattributedCalls   measured calls belonging to no report
 * @param unattributedTokens  measured tokens of those calls (already included in {@code totalTokens})
 * @param unattributedCostUsd estimated cost of those calls
 */
public record AdminTokenTotals(
		int reportsWithUsage,
		long claudeCalls,
		long inputTokens,
		long outputTokens,
		long cacheWriteTokens,
		long cacheReadTokens,
		long totalTokens,
		double costUsd,
		long avgTokensPerReport,
		long avgInputPerReport,
		long avgOutputPerReport,
		double avgCostPerReportUsd,
		long unknownCalls,
		long estimatedTokens,
		double estimatedCostUsd,
		long unattributedCalls,
		long unattributedTokens,
		double unattributedCostUsd) {
}
