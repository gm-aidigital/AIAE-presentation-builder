package com.aidigital.reportconstructor.externalservices.anthropic;

import org.springframework.stereotype.Component;

/**
 * Cheap, dependency-free estimate of how many tokens a prompt will cost, used to keep the prompts that
 * embed user-supplied grids inside a hard budget before they are sent.
 *
 * <p>The Messages API bills by real BPE tokens, which cannot be counted locally without shipping a
 * tokenizer. English prose and the pipe-delimited spreadsheet text these prompts carry both sit close to
 * four characters per token, so character length divided by {@link #CHARS_PER_TOKEN} is used as the
 * estimate. It is deliberately used only for guard rails — a budget check that must never let a
 * multi-megabyte workbook reach the API — so a modest over- or under-count changes nothing but where the
 * cut-off lands.
 */
@Component
public class PromptTokenEstimator {

	/** Characters per token assumed by {@link #estimateTokens}. */
	static final int CHARS_PER_TOKEN = 4;

	/**
	 * Estimates the token cost of a prompt.
	 *
	 * @param text the prompt text, may be {@code null}
	 * @return the estimated token count, rounded up; {@code 0} for {@code null}
	 */
	public int estimateTokens(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
	}

	/**
	 * Converts a token budget into the character budget that matches it under the same assumption
	 * {@link #estimateTokens} makes, for callers that assemble a prompt up to a size rather than measuring
	 * a finished one.
	 *
	 * @param maxTokens the token budget
	 * @return the equivalent character budget
	 */
	public int maxCharsFor(int maxTokens) {
		return maxTokens * CHARS_PER_TOKEN;
	}

	/**
	 * Reports whether a prompt fits inside a token budget.
	 *
	 * @param text      the prompt text, may be {@code null}
	 * @param maxTokens the inclusive token budget
	 * @return {@code true} when the estimated cost is within the budget
	 */
	public boolean fitsWithin(String text, int maxTokens) {
		return estimateTokens(text) <= maxTokens;
	}
}
