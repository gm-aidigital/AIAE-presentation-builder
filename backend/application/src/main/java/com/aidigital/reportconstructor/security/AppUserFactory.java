// AppUserFactory — builds AppUser from the Spring Security authentication.

package com.aidigital.reportconstructor.security;

import com.aidigital.reportconstructor.service.common.security.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Builds {@link AppUser} from the Spring Security authentication.
 *
 * <p>Fail-closed: accepts only Clerk-issued {@link JwtAuthenticationToken}s.
 * Requires {@code user_id} and {@code email} claims — never falls back from
 * a missing claim to {@code sub} or to a placeholder string.
 */
@Component
public class AppUserFactory {

    private static final String CLAIM_USER_ID = "user_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_FULLNAME = "full_name";

    /**
     * Converts the current request authentication into an {@link AppUser}.
     *
     * @param auth Spring Security authentication from the security context
     * @return caller identity built from the Clerk JWT claims
     * @throws IllegalStateException when the authentication is not a Clerk JWT
     *         or required claims are absent
     */
    public AppUser from(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(
                "Unsupported authentication type; expected a Clerk JWT: "
                + (auth == null ? "null" : auth.getClass().getName()));
        }
        Jwt jwt = jwtAuth.getToken();

        String userId = jwt.getClaimAsString(CLAIM_USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Clerk JWT is missing required claim: user_id");
        }

        String rawEmail = jwt.getClaimAsString(CLAIM_EMAIL);
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalStateException("Clerk JWT is missing required claim: email");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        String fullName = Optional.ofNullable(jwt.getClaimAsString(CLAIM_FULLNAME))
            .filter(v -> !v.isBlank())
            .orElse(email);

        return new AppUser(userId, email, fullName);
    }
}
