package com.aidigital.reportconstructor.service.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the admin dashboard's configuration so its properties classes bind without a
 * component-scan stereotype: the allow-list ({@code app.admin.*}), the usage rollup that backs the
 * dashboard's figures ({@code app.usage-rollup.*}), and the savings model ({@code app.savings.*}).
 *
 * <p>Also turns on scheduling, which the rollup's periodic refresh needs.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({AdminProperties.class, UsageRollupProperties.class, SavingsProperties.class})
public class AdminConfig {

}
