package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Claude token consumption summed over one report-generation run.
 *
 * <p>Cache tokens are kept separate from {@code inputTokens} because Anthropic bills them at
 * different rates — a cache write costs more than plain input, a cache read a fraction of it — so
 * folding them into one number would make any cost derived from it wrong.
 *
 * @param inputTokens      plain (uncached) input tokens billed
 * @param outputTokens     tokens the model generated
 * @param cacheWriteTokens input tokens written into the prompt cache
 * @param cacheReadTokens  input tokens served from the prompt cache
 * @param calls            number of Messages API calls the run made
 * @param model            Claude model the calls billed against, or {@code null} when unknown
 */
public record ClaudeUsage(
		long inputTokens,
		long outputTokens,
		long cacheWriteTokens,
		long cacheReadTokens,
		int calls,
		String model) {
}
