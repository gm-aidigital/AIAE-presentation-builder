package com.aidigital.reportconstructor.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Injectable component that derives Clerk issuer and JWKS endpoints from a
 * Clerk publishable key. Instance methods — no statics — so the decoding
 * policy can be substituted in tests.
 *
 * <p>A Clerk publishable key has the form {@code pk_test_<base64>} or
 * {@code pk_live_<base64>}. The base64 payload decodes to a bare DNS hostname
 * (the Frontend API host), optionally with a trailing {@code $} delimiter.
 */
@Component
public class ClerkPublishableKeyDecoder {

	private static final Pattern KEY_PATTERN = Pattern.compile("^pk_(test|live)_([A-Za-z0-9_=-]+)$");
	/**
	 * Valid bare DNS hostname: labels 1-63 chars, letters/digits/hyphens, no leading/trailing hyphens.
	 */
	private static final Pattern DNS_LABEL = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$");
	private static final String SCHEME = "https://";
	private static final String JWKS_PATH = "/.well-known/jwks.json";

	/**
	 * Decodes the Clerk Frontend API host embedded in a publishable key.
	 *
	 * @param publishableKey Clerk publishable key (pk_test_* or pk_live_*)
	 * @return bare DNS hostname, or null when the key is blank
	 * @throws IllegalStateException when the key is non-blank but malformed
	 */
	public String decodeFrontendApiHost(String publishableKey) {
		if (publishableKey == null || publishableKey.isBlank()) {
			return null;
		}
		var matcher = KEY_PATTERN.matcher(publishableKey.trim());
		if (!matcher.matches()) {
			throw new IllegalStateException(
					"Malformed CLERK_PUBLISHABLE_KEY: expected pk_test_<base64> or pk_live_<base64>");
		}
		String host;
		try {
			byte[] decoded = Base64.getUrlDecoder().decode(matcher.group(2));
			host = new String(decoded, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Malformed CLERK_PUBLISHABLE_KEY: payload is not valid base64", ex);
		}
		// Strip trailing $ delimiter used in some Clerk key formats
		if (host.endsWith("$")) {
			host = host.substring(0, host.length() - 1);
		}
		validateHost(host);
		return host;
	}

	/**
	 * Builds the Clerk issuer URI from a publishable key.
	 *
	 * @param publishableKey Clerk publishable key
	 * @return issuer URI such as {@code https://clean-clerk.clerk.accounts.dev}, or null
	 */
	public String issuerFromPublishableKey(String publishableKey) {
		String host = decodeFrontendApiHost(publishableKey);
		return host == null ? null : SCHEME + host;
	}

	/**
	 * Builds the JWKS URI from a publishable key.
	 *
	 * @param publishableKey Clerk publishable key
	 * @return JWKS URI for the Clerk tenant, or null
	 */
	public String jwksUriFromPublishableKey(String publishableKey) {
		String issuer = issuerFromPublishableKey(publishableKey);
		return issuer == null ? null : issuer + JWKS_PATH;
	}

	/**
	 * Validates that the decoded value is a bare DNS hostname with no
	 * scheme, port, path, query, fragment, whitespace, or invalid labels.
	 *
	 * @param host decoded host value
	 * @throws IllegalStateException when the host is not a valid bare DNS hostname
	 */
	void validateHost(String host) {
		if (host == null || host.isBlank()) {
			throw new IllegalStateException(
					"Malformed CLERK_PUBLISHABLE_KEY: decoded host is blank");
		}
		if (host.contains("://") || host.contains("/") || host.contains("?")
				|| host.contains("#") || host.contains("@") || host.contains(":")
				|| host.contains(" ") || host.contains("\t")) {
			throw new IllegalStateException(
					"Malformed CLERK_PUBLISHABLE_KEY: decoded host must be a bare DNS hostname");
		}
		// Validate each DNS label
		String[] labels = host.split("\\.", -1);
		if (labels.length == 0) {
			throw new IllegalStateException(
					"Malformed CLERK_PUBLISHABLE_KEY: decoded host has no DNS labels");
		}
		for (String label : labels) {
			if (label.isEmpty() || !DNS_LABEL.matcher(label).matches()) {
				throw new IllegalStateException(
						"Malformed CLERK_PUBLISHABLE_KEY: invalid DNS label '" + label + "'");
			}
		}
	}
}
