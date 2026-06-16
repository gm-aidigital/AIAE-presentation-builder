// Shared auth constants. SSO-only: the single concern here is which routes
// stay public (the SPA shell, static assets, health/metrics, the OpenAPI
// surface). Everything else requires a valid Clerk Bearer JWT.

package com.aidigital.reportconstructor.security;

/**
 * Shared constants for public routes.
 */
public final class AuthConstants {

	private AuthConstants() {
	}

	/**
	 * Clerk JWT claim bound as {@code Authentication#getName()}.
	 */
	public static final String USER_ID_CLAIM = "user_id";

	/**
	 * Path patterns that must remain public (no Bearer JWT required).
	 */
	public static final String[] PUBLIC_PATHS = {
			"/",
			"/index.html",
			"/favicon.ico",
			"/assets/**",
			"/error",
			"/*.css",
			"/*.js",
			"/*.png",
			"/*.svg",
			"/login",
			"/login/**",
			"/sign-in",
			"/sign-in/**",
			"/sign-up",
			"/sign-up/**",
			"/actuator/health",
			"/actuator/prometheus",
			"/api/v1/specs/**",
			"/swagger-ui/**",
			"/v3/api-docs/**"
	};
}
