// Shared auth constants. SSO-only: the concern here is which routes require a
// valid Clerk Bearer JWT (PROTECTED_PATHS: the API + sensitive actuator) versus
// which stay public. Public = the SPA shell, static assets, health/metrics, and
// the OpenAPI surface, plus every unmatched client route so browser refresh /
// deep links serve index.html instead of 401.

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

	/**
	 * Path patterns that require a valid Clerk Bearer JWT. Everything NOT matched by
	 * these (and not already in {@link #PUBLIC_PATHS}) is treated as the public SPA
	 * shell, so browser refresh / deep links on client routes (e.g. {@code /reports},
	 * {@code /admin}) serve {@code index.html} instead of returning 401. The data
	 * behind those routes lives under {@code /api/**} and stays protected here;
	 * per-endpoint role checks (e.g. the admin allow-list) remain enforced in the
	 * service layer.
	 */
	public static final String[] PROTECTED_PATHS = {
			"/api/**",
			"/actuator/**"
	};
}
