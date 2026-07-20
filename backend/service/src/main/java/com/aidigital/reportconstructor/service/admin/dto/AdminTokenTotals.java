package com.aidigital.reportconstructor.service.admin.dto;

/**
 * Team-wide Claude token consumption for the dashboard's "Token consumption" tab.
 *
 * <p>Averages are taken over {@code reportsWithUsage} rather than over every job: reports generated
 * before token accounting existed carry no counts, and dividing by them would understate the real
 * per-report cost.
 *
 * @param reportsWithUsage   reports that carry recorded token counts
 * @param claudeCalls        Anthropic API calls those reports made in total
 * @param inputTokens        plain (uncached) input tokens
 * @param outputTokens       output tokens
 * @param cacheWriteTokens   input tokens written into the prompt cache
 * @param cacheReadTokens    input tokens served from the prompt cache
 * @param totalTokens        every token above, summed
 * @param costUsd            estimated total cost at configured list prices
 * @param tokensThisMonth    tokens spent in the current calendar month
 * @param costThisMonthUsd   estimated cost of the current calendar month
 * @param avgTokensPerReport mean total tokens per report that carries usage
 * @param avgInputPerReport  mean input-side tokens (plain + cache) per such report
 * @param avgOutputPerReport mean output tokens per such report
 * @param avgCostPerReportUsd mean estimated cost per such report
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
		long tokensThisMonth,
		double costThisMonthUsd,
		long avgTokensPerReport,
		long avgInputPerReport,
		long avgOutputPerReport,
		double avgCostPerReportUsd) {
}
