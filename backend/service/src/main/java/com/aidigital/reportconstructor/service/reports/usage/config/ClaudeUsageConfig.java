package com.aidigital.reportconstructor.service.reports.usage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the Claude token pricing configuration so {@link ClaudePricingProperties} binds from
 * {@code app.claude-pricing.*} without a component-scan stereotype on the properties class.
 */
@Configuration
@EnableConfigurationProperties(ClaudePricingProperties.class)
public class ClaudeUsageConfig {

}
