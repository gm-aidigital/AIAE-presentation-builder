package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkJwtClaimsValidatorTest {

	private Jwt.Builder baseJwt() {
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user_123")
				.claim("user_id", "user_123")
				.claim("email", "user@aidigital.com");
	}

	private ClerkJwtClaimsValidator validatorWithParties(String authorizedParties) {
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties(authorizedParties);
		return new ClerkJwtClaimsValidator(props);
	}

	@Test
	void shouldRejectMismatchedSubAndUserIdTest() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user_a")
				.claim("user_id", "user_b")
				.claim("azp", "http://localhost:5173")
				.build();

		assertThat(validatorWithParties("http://localhost:5173").validate(jwt).hasErrors())
				.isTrue();
	}

	@Test
	void shouldRejectMissingUserIdTest() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user_a")
				.claim("azp", "http://localhost:5173")
				.build();

		assertThat(validatorWithParties("http://localhost:5173").validate(jwt).hasErrors())
				.isTrue();
	}

	@Test
	void shouldRejectUntrustedAzpWhenPartiesConfiguredTest() {
		Jwt jwt = baseJwt().claim("azp", "https://anything.example").build();

		OAuth2TokenValidatorResult result =
				validatorWithParties("http://localhost:5173").validate(jwt);

		assertThat(result.hasErrors()).isTrue();
	}

	@Test
	void shouldAcceptAzpInAuthorizedPartiesTest() {
		Jwt jwt = baseJwt().claim("azp", "http://localhost:5173").build();

		OAuth2TokenValidatorResult result = validatorWithParties(
				"http://localhost:5173, https://my-app.replit.app").validate(jwt);

		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	void shouldRejectAzpOutsideAuthorizedPartiesTest() {
		Jwt jwt = baseJwt().claim("azp", "https://evil.example").build();

		OAuth2TokenValidatorResult result =
				validatorWithParties("http://localhost:5173").validate(jwt);

		assertThat(result.hasErrors()).isTrue();
	}

	@Test
	void shouldRejectMissingAzpWhenAuthorizedPartiesConfiguredTest() {
		Jwt jwt = baseJwt().build();

		OAuth2TokenValidatorResult result =
				validatorWithParties("http://localhost:5173").validate(jwt);

		assertThat(result.hasErrors()).isTrue();
	}
}
