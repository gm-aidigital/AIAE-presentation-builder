package com.aidigital.reportconstructor.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a JWT {@code azp} (authorized party) claim is a trusted
 * browser origin.
 *
 * <p>Two sources are consulted, in order:
 * <ol>
 *   <li>{@code app.auth.authorized-parties} — exact origins, the production
 *       mechanism. Compared with {@link String#equals(Object)};</li>
 *   <li>{@code app.auth.authorized-party-patterns} — optional wildcard origins
 *       for ephemeral development hosts, e.g.
 *       {@code https://*.replit.dev:5173}. Blank by default, which leaves
 *       matching exact-only.</li>
 * </ol>
 *
 * <p>Wildcard support exists because a Replit workspace preview hostname carries
 * a per-workspace UUID and changes whenever the repl moves cluster, so it cannot
 * be pinned as an exact origin. A single {@code *} stands for one or more
 * hostname labels ({@code [A-Za-z0-9-]} separated by dots) and nothing else — it
 * cannot span {@code /}, {@code :}, {@code @}, {@code ?} or {@code #}, so a
 * pattern can never be widened into a different scheme, port, credential or
 * path by the claim value. Everything outside the wildcard is matched literally.
 *
 * <p>Patterns are strictly weaker than exact origins and are meant for the
 * development workspace only; {@code scripts/lib/deploy-env.sh} clears the
 * variable for the published app so production always matches exact-only.
 */
@Component
public class AuthorizedPartyMatcher {

	private static final String LIST_DELIMITER = ",";
	private static final String WILDCARD = "*";
	/**
	 * Regex a single {@code *} expands to: one or more hostname labels. Deliberately
	 * excludes {@code . } boundaries at the edges and every origin-structural
	 * character, so the wildcard stays inside the host component.
	 */
	private static final String WILDCARD_REGEX = "[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*";

	private final AuthProperties authProperties;

	public AuthorizedPartyMatcher(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	/**
	 * Checks an {@code azp} claim against the exact origins and, when configured,
	 * the wildcard patterns.
	 *
	 * @param azp the {@code azp} claim value (may be null or blank)
	 * @return true when the claim is a trusted origin
	 */
	public boolean isTrusted(String azp) {
		if (azp == null || azp.isBlank()) {
			return false;
		}
		if (exactParties().contains(azp)) {
			return true;
		}
		for (Pattern pattern : compiledPatterns()) {
			if (pattern.matcher(azp).matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parses the configured comma-separated exact trusted browser origins.
	 *
	 * @return exact origins from {@code app.auth.authorized-parties}
	 */
	List<String> exactParties() {
		return splitList(authProperties.getAuthorizedParties());
	}

	/**
	 * Compiles the configured comma-separated wildcard origin patterns.
	 *
	 * @return compiled patterns from {@code app.auth.authorized-party-patterns},
	 * empty when the property is blank
	 */
	List<Pattern> compiledPatterns() {
		List<String> raw = splitList(authProperties.getAuthorizedPartyPatterns());
		List<Pattern> compiled = new ArrayList<>(raw.size());
		for (String candidate : raw) {
			compiled.add(Pattern.compile(toRegex(candidate)));
		}
		return compiled;
	}

	/**
	 * Translates one wildcard origin pattern into an anchored regex. Literal
	 * segments are quoted, so only {@code *} carries meaning.
	 *
	 * @param pattern wildcard origin pattern, e.g. {@code https://*.replit.dev:5173}
	 * @return regex source matching the whole origin
	 */
	String toRegex(String pattern) {
		StringBuilder regex = new StringBuilder();
		String[] literals = pattern.split(Pattern.quote(WILDCARD), -1);
		for (int i = 0; i < literals.length; i++) {
			if (i > 0) {
				regex.append(WILDCARD_REGEX);
			}
			if (!literals[i].isEmpty()) {
				regex.append(Pattern.quote(literals[i]));
			}
		}
		return regex.toString();
	}

	/**
	 * Splits a comma-separated configuration value into trimmed, non-empty entries.
	 *
	 * @param raw configuration value (may be null)
	 * @return trimmed entries, empty when nothing is configured
	 */
	List<String> splitList(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(LIST_DELIMITER))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
