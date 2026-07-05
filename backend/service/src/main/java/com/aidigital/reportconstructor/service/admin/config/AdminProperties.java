// @ConfigurationProperties bean — the admin email allow-list.
// Maps from application.yml `app.admin.*` and the APP_ADMIN_EMAILS env var.

package com.aidigital.reportconstructor.service.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed configuration for admin authorization. The dashboard and the {@code /admin}
 * API are granted only to callers whose email appears in {@link #emails}. Editing this
 * list (via the {@code APP_ADMIN_EMAILS} env var) and redeploying is how admin access
 * is granted or revoked — there is no in-app role store.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

	/**
	 * Emails allowed to see the admin dashboard, compared case-insensitively.
	 */
	private List<String> emails = new ArrayList<>();
}
