package com.aidigital.reportconstructor.service.reports.usage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token pricing used to turn recorded Claude usage into dollars on the admin dashboard, bound from
 * {@code app.claude-pricing.*}.
 *
 * <p>Cost is computed at read time rather than stored per job, so correcting a rate here fixes every
 * historical figure at once. {@link #byModel} lets a deployment price several models correctly when
 * the configured model has changed over the life of the data; jobs whose model has no entry fall
 * back to {@link #getDefaultPrice()}. Figures are list prices and therefore an estimate — discounts
 * and batch rates are not modelled.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.claude-pricing")
public class ClaudePricingProperties {

	/**
	 * Rates applied to any model without a {@link #byModel} entry.
	 */
	private ClaudeModelPrice defaultPrice = new ClaudeModelPrice();

	/**
	 * Per-model rate overrides, keyed by the model id Anthropic reports (matched case-insensitively
	 * on a prefix, so a dated id like {@code claude-sonnet-4-6-20260101} matches a {@code claude-sonnet-4-6}
	 * key).
	 */
	private Map<String, ClaudeModelPrice> byModel = new LinkedHashMap<>();
}
