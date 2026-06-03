package com.aidigital.reportconstructor.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup when {@code AUTH_AUTHORIZED_PARTIES} is unset.
 *
 * <p>Blank authorized parties would silently disable {@code azp} enforcement in
 * {@link ClerkJwtClaimsValidator}. The test profile supplies a value in
 * {@code application-test.yml} instead.
 */
@Component
@Profile("!test")
public class AuthStartupValidator implements InitializingBean {

    private final AuthProperties authProperties;

    public AuthStartupValidator(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void afterPropertiesSet() {
        String parties = authProperties.getAuthorizedParties();
        if (parties == null || parties.isBlank()) {
            throw new IllegalStateException(
                "AUTH_AUTHORIZED_PARTIES is required — set comma-separated trusted "
                    + "browser origins (e.g. http://localhost:5173,https://my-app.replit.app). "
                    + "azp enforcement cannot be disabled.");
        }
    }
}
