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
	 * Sampling temperature sent with every Claude call. Every prompt in this integration asks for a strict
	 * JSON reply whose fields carry hard character budgets, so the API default of 1.0 buys variety we do not
	 * want and costs format stability: a hotter sample is likelier to drift on the schema, overrun a limit
	 * (forcing an extra compression call) or wrap the JSON in prose. 0.4 keeps the copy varied enough to read
	 * as written prose while holding the contract. Clamped to 0.0..1.0 at use.
	 */
	private double temperature = 0.4;

	/**
	 * Tactics per Step-2 per-tactic conclusions call in the slides-from-sheet flow. Default 7: that call
	 * produces only each tactic's ~190-character overview (every breakdown section has its own call), so a
	 * chunk of 7 still asks for a small reply while turning a 28-tactic deck's 28 calls into 4 — and lets the
	 * model see neighbouring tactics rather than writing every overview blind. Chunks run concurrently, so a
	 * larger chunk trades parallelism for context; raise it further only alongside a larger reply budget.
	 * Clamped to at least 1 at use.
	 */
	private int breakdownChunkSize = 7;

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
	 * such failure otherwise discards a whole batch and blanks its slide tokens, so a cheap re-send is worth
	 * it. Default 1 means one retry, two sends in total: a run fans out many calls, and a third send mostly
	 * lengthens an already-slow run against an upstream that is still failing rather than rescuing it — the
	 * same reasoning as {@link #sectionRetries}. Clamped to at least 0 (no retry) at use.
	 */
	private int maxRetries = 1;

	/**
	 * Base backoff between retries in milliseconds, scaled linearly by attempt number (attempt 1 waits this,
	 * attempt 2 waits twice this, and so on). Default 1000. Clamped to at least 0 at use; 0 retries with no
	 * delay.
	 */
	private long retryBackoffMillis = 1000;

	/**
	 * Extra attempts a per-section pilot call makes when its reply fails the positional contract (not a JSON
	 * array of exactly the expected count of non-blank strings) before the tactic's section ships blank.
	 * Default 1 means one retry, two sends in total: a deterministic rejection reproduces on the re-send, so a
	 * third identical prompt mostly burns tokens. Clamped to at least 0 (no retry) at use.
	 */
	private int sectionRetries = 1;

	/**
	 * Characters of an unparseable reply written to the WARN line that reports the parse failure. The default
	 * 400 keeps the log readable but truncates before the defect on a long reply, so this is raised
	 * temporarily when a parse failure has to be diagnosed on a deployed environment. The full reply is
	 * always available on DEBUG regardless of this value. Clamped to at least 80 at use.
	 */
	private int replySnippetLimit = 400;

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

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
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

	public int getSectionRetries() {
		return sectionRetries;
	}

	public void setSectionRetries(int sectionRetries) {
		this.sectionRetries = sectionRetries;
	}

	public int getReplySnippetLimit() {
		return replySnippetLimit;
	}

	public void setReplySnippetLimit(int replySnippetLimit) {
		this.replySnippetLimit = replySnippetLimit;
	}
}
