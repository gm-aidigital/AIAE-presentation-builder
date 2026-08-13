package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizedPartyMatcherTest {

	@Test
	void shouldTrustExactOriginTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("http://localhost:5173, https://my-app.replit.app");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then:
		assertThat(matcher.isTrusted("https://my-app.replit.app")).isTrue();
	}

	@Test
	void shouldRejectUnknownOriginWhenNoPatternsConfiguredTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("http://localhost:5173");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then:
		assertThat(matcher.isTrusted("https://abc.spock.replit.dev:5173")).isFalse();
	}

	@Test
	void shouldRejectNullAndBlankAzpTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("http://localhost:5173");
		props.setAuthorizedPartyPatterns("https://*.replit.dev:5173");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then:
		assertThat(matcher.isTrusted(null)).isFalse();
		assertThat(matcher.isTrusted("  ")).isFalse();
	}

	@Test
	void shouldTrustWorkspacePreviewHostViaWildcardTest() {
		// Given: the real workspace origin — UUID host, extra cluster label, explicit port.
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("https://my-app.replit.app");
		props.setAuthorizedPartyPatterns("https://*.replit.dev:5173");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When:
		boolean trusted = matcher.isTrusted(
				"https://de1ddd8c-7885-4345-bdff-dbff942be7e0-00-3bdhs3ngq2l9d.spock.replit.dev:5173");

		// Then:
		assertThat(trusted).isTrue();
	}

	@Test
	void shouldNotLetWildcardCrossSchemePortOrPathTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("http://localhost:5173");
		props.setAuthorizedPartyPatterns("https://*.replit.dev:5173");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then: wrong scheme, wrong port, trailing path, and credential/query
		// smuggling attempts must all miss.
		assertThat(matcher.isTrusted("http://abc.replit.dev:5173")).isFalse();
		assertThat(matcher.isTrusted("https://abc.replit.dev:5000")).isFalse();
		assertThat(matcher.isTrusted("https://abc.replit.dev:5173/evil")).isFalse();
		assertThat(matcher.isTrusted("https://evil.example/x.replit.dev:5173")).isFalse();
		assertThat(matcher.isTrusted("https://evil.example@x.replit.dev:5173")).isFalse();
		assertThat(matcher.isTrusted("https://abc.replit.dev.evil.example:5173")).isFalse();
	}

	@Test
	void shouldTreatBlankPatternsAsExactMatchOnlyTest() {
		// Given:
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("https://my-app.replit.app");
		props.setAuthorizedPartyPatterns("   ");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then:
		assertThat(matcher.compiledPatterns()).isEmpty();
		assertThat(matcher.isTrusted("https://anything.replit.dev:5173")).isFalse();
	}

	@Test
	void shouldQuoteLiteralSegmentsWhenBuildingRegexTest() {
		// Given: dots in the literal part must not act as regex wildcards.
		AuthProperties props = new AuthProperties();
		props.setAuthorizedPartyPatterns("https://*.replit.dev:5173");
		AuthorizedPartyMatcher matcher = new AuthorizedPartyMatcher(props);

		// When-Then:
		assertThat(matcher.isTrusted("https://abc.replitXdev:5173")).isFalse();
		assertThat(matcher.toRegex("https://*.replit.dev:5173"))
				.contains("\\Qhttps://\\E")
				.contains("\\Q.replit.dev:5173\\E");
	}
}
