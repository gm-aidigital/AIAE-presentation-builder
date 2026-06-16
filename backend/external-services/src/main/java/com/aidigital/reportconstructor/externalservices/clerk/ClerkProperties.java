package com.aidigital.reportconstructor.externalservices.clerk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Clerk Backend API integration. Maps from
 * {@code app.clerk.*} (and {@code CLERK_SECRET_KEY} / {@code CLERK_GOOGLE_PROVIDER}).
 */
@ConfigurationProperties(prefix = "app.clerk")
public class ClerkProperties {

	/**
	 * Clerk Backend API secret key. Bean is only active when non-blank.
	 */
	private String secretKey = "";

	/**
	 * OAuth provider id whose access token is fetched (default Google).
	 */
	private String googleProvider = "oauth_google";

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getGoogleProvider() {
		return googleProvider;
	}

	public void setGoogleProvider(String googleProvider) {
		this.googleProvider = googleProvider;
	}
}
