package com.aidigital.reportconstructor.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Turns the configured {@code app.security.cors.allowed-origins} string into the
 * pattern list handed to {@link org.springframework.web.cors.CorsConfiguration#setAllowedOriginPatterns}.
 *
 * <p>Beyond splitting, it fixes one sharp edge of Spring's origin-pattern syntax:
 * a {@code *} never matches the port. {@code https://*.replit.dev} therefore
 * rejects {@code https://<workspace>.spock.replit.dev:5173}, and because the
 * rejection happens in {@code DefaultCorsProcessor} the caller gets a bodyless
 * {@code 403 Invalid CORS request} — which only shows up on requests that carry
 * an {@code Origin} header (every fetch POST; not a same-origin GET). Spring's
 * own spelling for "any port" is the explicit {@code :[*]} suffix.
 *
 * <p>So every wildcard-host pattern that does not already pin a port gets an
 * additional {@code :[*]} sibling. Patterns with a fixed port and patterns
 * without a wildcard host (e.g. an exact production origin) are passed through
 * untouched — widening those would be a policy change, not a syntax fix.
 */
@Component
public class CorsOriginPatternNormalizer {

	private static final String LIST_DELIMITER = ",";
	private static final String WILDCARD = "*";
	private static final String SCHEME_SEPARATOR = "://";
	private static final String ANY_PORT_SUFFIX = ":[*]";

	/**
	 * Splits the configured origins and adds any-port siblings where needed.
	 *
	 * @param configuredOrigins raw comma-separated value of
	 *                          {@code app.security.cors.allowed-origins}
	 * @return origin patterns in configuration order, each configured entry kept
	 * and followed by its {@code :[*]} sibling when one applies
	 */
	public List<String> normalize(String configuredOrigins) {
		List<String> normalized = new ArrayList<>();
		for (String pattern : split(configuredOrigins)) {
			normalized.add(pattern);
			if (needsAnyPortSibling(pattern)) {
				normalized.add(pattern + ANY_PORT_SUFFIX);
			}
		}
		return List.copyOf(new LinkedHashSet<>(normalized));
	}

	/**
	 * Decides whether a pattern should also be registered for any port.
	 *
	 * @param pattern one configured origin pattern
	 * @return true when the host carries a wildcard and no port is pinned yet
	 */
	boolean needsAnyPortSibling(String pattern) {
		String authority = authorityOf(pattern);
		return authority.contains(WILDCARD) && !authority.contains(":");
	}

	/**
	 * Extracts the authority (host plus optional port) of an origin pattern.
	 *
	 * @param pattern one configured origin pattern
	 * @return the part after {@code ://}, or the whole value when no scheme is present
	 */
	String authorityOf(String pattern) {
		int schemeEnd = pattern.indexOf(SCHEME_SEPARATOR);
		return schemeEnd < 0 ? pattern : pattern.substring(schemeEnd + SCHEME_SEPARATOR.length());
	}

	/**
	 * Splits a comma-separated configuration value into trimmed, non-empty entries.
	 *
	 * @param raw configuration value (may be null)
	 * @return trimmed entries, empty when nothing is configured
	 */
	List<String> split(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(LIST_DELIMITER))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
