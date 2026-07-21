package com.aidigital.reportconstructor.externalservices.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the Anthropic (Claude) integration, bound from
 * {@code external.anthropic.*} (which maps the {@code ANTHROPIC_API_KEY} and
 * {@code ANTHROPIC_MODEL} env vars). The live Claude client activates only when
 * {@code api-key} is non-blank; otherwise the stub fallback is used.
 */
@Component
@ConfigurationProperties(prefix = "external.anthropic")
public class AnthropicProperties {

	/**
	 * Anthropic API key. Live Claude client is inactive when blank.
	 */
	private String apiKey = "";

	/**
	 * Claude model id used for all batches.
	 */
	private String model = "claude-sonnet-4-6";

	/**
	 * Tactics per Step-2 combined per-tactic conclusions call in the slides-from-sheet flow. Default 1 keeps
	 * each call small so the cached instruction prefix is re-read cheaply per tactic; raising it batches more
	 * tactics per call at the cost of a larger, likelier-to-truncate reply. Clamped to at least 1 at use.
	 */
	private int breakdownChunkSize = 1;

	/**
	 * Upper bound on Claude HTTP calls in flight at once across the whole run, enforced by a shared semaphore
	 * in {@link AnthropicMessagesClient}. The restructured flow fans out many small per-tactic calls (Step 2
	 * chunk=1, plus per-tactic Steps 3 and 5), so this caps concurrency to stay inside the account rate limit.
	 * Clamped to at least 1 at use.
	 */
	private int maxConcurrentCalls = 6;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getBreakdownChunkSize() {
		return breakdownChunkSize;
	}

	public void setBreakdownChunkSize(int breakdownChunkSize) {
		this.breakdownChunkSize = breakdownChunkSize;
	}

	public int getMaxConcurrentCalls() {
		return maxConcurrentCalls;
	}

	public void setMaxConcurrentCalls(int maxConcurrentCalls) {
		this.maxConcurrentCalls = maxConcurrentCalls;
	}
}
