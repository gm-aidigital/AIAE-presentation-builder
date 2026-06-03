// @ConfigurationProperties bean — typed home for Clerk SSO settings.
// Maps from application.yml `app.auth.*` and the AUTH_* / CLERK_* env vars.

package com.aidigital.reportconstructor.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

/**
 * Typed configuration for Clerk SSO authentication.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Company email domain without a leading {@code @}. */
    private String allowedEmailDomain = "aidigital.com";

    /** Clerk publishable key — derives issuer/JWKS when explicit URIs are blank. */
    private String publishableKey;

    /**
     * Comma-separated list of exact trusted browser origins for the JWT
     * {@code azp} (authorized party) claim, e.g.
     * {@code http://localhost:5173,https://my-app.replit.app}. Never a Clerk
     * publishable key. Blank fails startup via {@link AuthStartupValidator}.
     */
    private String authorizedParties = "";

    @NotNull
    private Sso sso = new Sso();

    public String getAllowedEmailDomain() {
        return allowedEmailDomain;
    }

    public void setAllowedEmailDomain(String allowedEmailDomain) {
        this.allowedEmailDomain = allowedEmailDomain;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getAuthorizedParties() {
        return authorizedParties;
    }

    public void setAuthorizedParties(String authorizedParties) {
        this.authorizedParties = authorizedParties;
    }

    public Sso getSso() {
        return sso;
    }

    public void setSso(Sso sso) {
        this.sso = sso;
    }

    /**
     * SSO/OIDC settings for Clerk or another compatible provider.
     */
    public static class Sso {
        /** Clerk issuer URI, e.g. https://clean-clerk.clerk.accounts.dev */
        private String issuerUri;
        /** Optional JWKS override; usually discovered from issuerUri. */
        private String jwkSetUri;
        /** Expected `aud` claim value. */
        private String audience;

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
