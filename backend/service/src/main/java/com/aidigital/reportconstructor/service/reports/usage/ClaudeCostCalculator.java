package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Turns recorded Claude token counts into an estimated dollar cost at the configured list prices.
 */
@Component
@RequiredArgsConstructor
public class ClaudeCostCalculator {

	/** Tokens per pricing unit — rates are quoted per million tokens. */
	private static final double TOKENS_PER_MTOK = 1_000_000d;

	private final ClaudePricingProperties pricing;

	/**
	 * Prices one job's (or one aggregate's) token consumption.
	 *
	 * @param inputTokens      plain input tokens
	 * @param outputTokens     output tokens
	 * @param cacheWriteTokens input tokens written into the prompt cache
	 * @param cacheReadTokens  input tokens served from the prompt cache
	 * @param model            model the tokens billed against, or {@code null} for the default rates
	 * @return estimated cost in USD
	 */
	public double costUsd(
			long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens, String model) {
		ClaudeModelPrice price = priceFor(model);
		double usd = inputTokens * price.getInputPerMtok()
				+ outputTokens * price.getOutputPerMtok()
				+ cacheWriteTokens * price.getCacheWritePerMtok()
				+ cacheReadTokens * price.getCacheReadPerMtok();
		return usd / TOKENS_PER_MTOK;
	}

	/**
	 * Resolves the rate card for a model id. Matching is a case-insensitive prefix test so a dated id
	 * ({@code claude-sonnet-4-6-20260101}) still finds its undated configuration key; an unknown or
	 * missing model falls back to the default rates rather than being priced at zero, which would
	 * quietly hide spend.
	 *
	 * @param model the model id recorded on the job, possibly {@code null}
	 * @return the matching rate card, or the configured default
	 */
	ClaudeModelPrice priceFor(String model) {
		if (model == null || model.isBlank()) {
			return pricing.getDefaultPrice();
		}
		String needle = model.trim().toLowerCase(Locale.ROOT);
		for (Map.Entry<String, ClaudeModelPrice> entry : pricing.getByModel().entrySet()) {
			String key = entry.getKey().toLowerCase(Locale.ROOT);
			if (needle.startsWith(key) || key.startsWith(needle)) {
				return entry.getValue();
			}
		}
		return pricing.getDefaultPrice();
	}
}
