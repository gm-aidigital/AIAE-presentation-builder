package com.aidigital.reportconstructor.service.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the admin allow-list configuration so {@link AdminProperties} binds from
 * {@code app.admin.*} without a component-scan stereotype on the properties class.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig {

}
