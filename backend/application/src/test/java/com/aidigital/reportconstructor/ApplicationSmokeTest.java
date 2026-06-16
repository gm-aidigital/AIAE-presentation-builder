package com.aidigital.reportconstructor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test — Spring context loads end-to-end with the test profile.
 * Catches the bulk of wiring/regression failures (missing beans, conflicting
 * conditionals, broken @ConfigurationProperties binding, datasource config
 * crashes) with zero per-bean assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

	// SSO-only: SecurityConfig.jwtDecoder is @ConditionalOnMissingBean and
	// fails fast without a real Clerk issuer. A stub decoder lets the context
	// load without reaching a JWKS endpoint.
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void shouldLoadSpringContextTest() {
	}
}
