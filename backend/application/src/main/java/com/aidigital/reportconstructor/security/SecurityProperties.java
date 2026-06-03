package com.aidigital.reportconstructor.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed browser security configuration bound from {@code app.security.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private final Cors cors = new Cors();
    private final Csp csp = new Csp();

    public Cors getCors() {
        return cors;
    }

    public Csp getCsp() {
        return csp;
    }

    /**
     * Cross-origin resource sharing settings.
     */
    public static class Cors {
        @NotBlank
        private String allowedOrigins =
            "https://*.replit.dev,https://*.repl.co,http://localhost:5173,http://localhost:5000";

        private long maxAgeSeconds = 3600L;

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    /**
     * Content Security Policy settings.
     */
    public static class Csp {
        private String frameAncestors = "'self' https://*.replit.dev https://*.repl.co";

        public String getFrameAncestors() {
            return frameAncestors;
        }

        public void setFrameAncestors(String frameAncestors) {
            this.frameAncestors = frameAncestors;
        }
    }
}
