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
}
