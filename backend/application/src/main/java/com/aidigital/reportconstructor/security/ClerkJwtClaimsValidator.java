package com.aidigital.reportconstructor.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Validates the Clerk aidigital-api JWT contract in the resource-server layer:
 * required claims, matching {@code sub}/{@code user_id}, and the authorized
 * party ({@code azp}). A failure here yields HTTP 401. Company-email-domain
 * authorization is enforced separately and yields HTTP 403.
 *
 * <p>{@code azp} is the trusted browser origin, never a Clerk publishable key.
 * Trust is decided by {@link AuthorizedPartyMatcher}: the exact origins in
 * {@code app.auth.authorized-parties} plus, when configured, the development-only
 * wildcard patterns in {@code app.auth.authorized-party-patterns}.
 */
@Component
public class ClerkJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

	private static final String ERROR_CODE = "invalid_token";
	private static final String CLAIM_USER_ID = "user_id";
	private static final String CLAIM_AZP = "azp";

	private final AuthorizedPartyMatcher authorizedPartyMatcher;

	public ClerkJwtClaimsValidator(AuthorizedPartyMatcher authorizedPartyMatcher) {
		this.authorizedPartyMatcher = authorizedPartyMatcher;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		String sub = jwt.getSubject();
		String userId = jwt.getClaimAsString(CLAIM_USER_ID);
		if (sub == null || sub.isBlank() || userId == null || userId.isBlank()) {
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error(ERROR_CODE, "Missing sub or user_id claim", null));
		}
		if (!sub.equals(userId)) {
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error(ERROR_CODE, "sub and user_id must match", null));
		}
		String azp = jwt.getClaimAsString(CLAIM_AZP);
		if (!authorizedPartyMatcher.isTrusted(azp)) {
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error(ERROR_CODE, "Authorized party is not trusted", null));
		}
		return OAuth2TokenValidatorResult.success();
	}
}
