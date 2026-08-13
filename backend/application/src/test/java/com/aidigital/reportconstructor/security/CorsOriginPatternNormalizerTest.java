package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsOriginPatternNormalizerTest {

	@Test
	void shouldAddAnyPortSiblingForWildcardHostTest() {
		// Given:
		CorsOriginPatternNormalizer normalizer = new CorsOriginPatternNormalizer();

		// When:
		var patterns = normalizer.normalize("https://*.replit.dev, https://*.repl.co");

		// Then:
		assertThat(patterns).containsExactly(
				"https://*.replit.dev", "https://*.replit.dev:[*]",
				"https://*.repl.co", "https://*.repl.co:[*]");
	}

	@Test
	void shouldLeaveExactAndPinnedPortOriginsUntouchedTest() {
		// Given:
		CorsOriginPatternNormalizer normalizer = new CorsOriginPatternNormalizer();

		// When:
		var patterns = normalizer.normalize(
				"http://localhost:5173, https://aiae-presentation-builder.aidigital.tech, "
						+ "https://*.example.com:3000, https://*.replit.dev:[*]");

		// Then:
		assertThat(patterns).containsExactly(
				"http://localhost:5173",
				"https://aiae-presentation-builder.aidigital.tech",
				"https://*.example.com:3000",
				"https://*.replit.dev:[*]");
	}

	@Test
	void shouldReturnEmptyListForBlankConfigurationTest() {
		// Given:
		CorsOriginPatternNormalizer normalizer = new CorsOriginPatternNormalizer();

		// When-Then:
		assertThat(normalizer.normalize(null)).isEmpty();
		assertThat(normalizer.normalize("  ,  ")).isEmpty();
	}

	@Test
	void shouldAcceptWorkspacePreviewOriginWithExplicitPortTest() {
		// Given: the shipped default list, which alone rejects an origin carrying a port.
		CorsOriginPatternNormalizer normalizer = new CorsOriginPatternNormalizer();
		String configured = new SecurityProperties().getCors().getAllowedOrigins();
		String workspaceOrigin =
				"https://de1ddd8c-7885-4345-bdff-dbff942be7e0-00-3bdhs3ngq2l9d.spock.replit.dev:5173";

		CorsConfiguration raw = new CorsConfiguration();
		raw.setAllowedOriginPatterns(normalizer.split(configured));
		CorsConfiguration expanded = new CorsConfiguration();
		expanded.setAllowedOriginPatterns(normalizer.normalize(configured));

		// When-Then: this is the actual dev failure — 403 "Invalid CORS request" on POST.
		assertThat(raw.checkOrigin(workspaceOrigin)).isNull();
		assertThat(expanded.checkOrigin(workspaceOrigin)).isEqualTo(workspaceOrigin);
	}

	@Test
	void shouldNotWidenBeyondTheConfiguredHostSuffixTest() {
		// Given:
		CorsOriginPatternNormalizer normalizer = new CorsOriginPatternNormalizer();
		CorsConfiguration cfg = new CorsConfiguration();
		cfg.setAllowedOriginPatterns(normalizer.normalize("https://*.replit.dev"));

		// When-Then:
		assertThat(cfg.checkOrigin("http://abc.replit.dev:5173")).isNull();
		assertThat(cfg.checkOrigin("https://abc.replit.dev.evil.example")).isNull();
		assertThat(cfg.checkOrigin("https://evil.example")).isNull();
	}
}
