package com.aidigital.reportconstructor.service.reports.usage.config;

import lombok.Getter;
import lombok.Setter;

/**
 * List price of one Claude model, in US dollars per million tokens. Cache writes and cache reads
 * are priced separately from plain input because Anthropic bills them at different rates — a write
 * above the input rate, a read at a fraction of it — and the dashboard's money column is only
 * meaningful if it keeps them apart.
 */
@Getter
@Setter
public class ClaudeModelPrice {

	/** USD per million plain (uncached) input tokens. */
	private double inputPerMtok;

	/** USD per million output tokens. */
	private double outputPerMtok;

	/** USD per million input tokens written into the prompt cache. */
	private double cacheWritePerMtok;

	/** USD per million input tokens served from the prompt cache. */
	private double cacheReadPerMtok;
}
