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

	/**
	 * Extra attempts a Claude HTTP call makes after its first send fails on a transient upstream condition —
	 * a retryable status such as a Cloudflare 522 or an Anthropic 529 overload, or a dropped connection. One
	 * such failure otherwise discards a whole batch and blanks its slide tokens, so a few cheap re-sends are
	 * worth it. Default 2 means up to three sends in total. Clamped to at least 0 (no retry) at use.
	 */
	private int maxRetries = 2;

	/**
	 * Base backoff between retries in milliseconds, scaled linearly by attempt number (attempt 1 waits this,
	 * attempt 2 waits twice this, and so on). Default 1000. Clamped to at least 0 at use; 0 retries with no
	 * delay.
	 */
	private long retryBackoffMillis = 1000;

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

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public long getRetryBackoffMillis() {
		return retryBackoffMillis;
	}

	public void setRetryBackoffMillis(long retryBackoffMillis) {
		this.retryBackoffMillis = retryBackoffMillis;
	}
}
