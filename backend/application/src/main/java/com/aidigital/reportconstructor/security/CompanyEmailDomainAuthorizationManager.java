package com.aidigital.reportconstructor.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Post-authentication authorization policy that restricts access to users whose
 * Clerk email belongs to the configured company domain.
 *
 * <p>Runs after JWT authentication, so the outcomes are:
 * <ul>
 *   <li>missing / invalid token &rarr; 401 (handled by the resource server);</li>
 *   <li>valid token, missing email &rarr; 403;</li>
 *   <li>valid token, email outside the exact domain &rarr; 403;</li>
 *   <li>valid token, permitted email &rarr; allowed.</li>
 * </ul>
 *
 * <p>Instance methods only — no static helpers — so the policy is injectable
 * and unit-testable.
 */
@Component
public class CompanyEmailDomainAuthorizationManager
		implements AuthorizationManager<RequestAuthorizationContext> {

	private static final String EMAIL_CLAIM = "email";

	private final AuthProperties authProperties;

	public CompanyEmailDomainAuthorizationManager(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	@Override
	public AuthorizationDecision check(
			Supplier<Authentication> authentication,
			RequestAuthorizationContext context
	) {
		Authentication auth = authentication.get();
		if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
			return new AuthorizationDecision(false);
		}
		String email = jwtAuth.getToken().getClaimAsString(EMAIL_CLAIM);
		return new AuthorizationDecision(isAllowedEmail(email));
	}

	/**
	 * Decides whether an email belongs to the configured company domain.
	 *
	 * @param email raw email claim value (may be null)
	 * @return true when the email's domain exactly matches the allow-list domain
	 */
	public boolean isAllowedEmail(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		String domain = normalizeDomain(authProperties.getAllowedEmailDomain());
		if (domain.isBlank()) {
			return false;
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		int at = normalized.lastIndexOf('@');
		if (at < 0 || at == normalized.length() - 1) {
			return false;
		}
		return domain.equals(normalized.substring(at + 1));
	}

	/**
	 * Normalizes the configured company domain for comparison.
	 *
	 * @param domain configured domain value (may be null)
	 * @return trimmed lower-case domain without a leading {@code @}
	 */
	String normalizeDomain(String domain) {
		if (domain == null) {
			return "";
		}
		String normalized = domain.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("@")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}
}
