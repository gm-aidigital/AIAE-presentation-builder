package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyEmailDomainAuthorizationManagerTest {

	private final CompanyEmailDomainAuthorizationManager manager =
			new CompanyEmailDomainAuthorizationManager(new AuthProperties());

	@ParameterizedTest
	@ValueSource(strings = {
			"user@aidigital.com",
			"USER@AIDIGITAL.COM",
			" user@aidigital.com "
	})
	void shouldAllowCompanyEmailTest(String email) {
		assertThat(manager.isAllowedEmail(email)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"user@aidigital.com.attacker.example",
			"user@team.aidigital.com",
			"user@attacker.example",
			"not-an-email",
			"missing-domain@"
	})
	void shouldRejectOutsideOrInvalidEmailTest(String email) {
		assertThat(manager.isAllowedEmail(email)).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectMissingEmailTest(String email) {
		assertThat(manager.isAllowedEmail(email)).isFalse();
	}

	@Test
	void shouldEnforceConfiguredDomainOverrideTest() {
		AuthProperties props = new AuthProperties();
		props.setAllowedEmailDomain("@Example.COM");
		CompanyEmailDomainAuthorizationManager custom =
				new CompanyEmailDomainAuthorizationManager(props);

		assertThat(custom.isAllowedEmail("dev@example.com")).isTrue();
		assertThat(custom.isAllowedEmail("dev@aidigital.com")).isFalse();
	}

	@Test
	void shouldAllowJwtWithCompanyEmailTest() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user_1")
				.claim("email", "alice@aidigital.com")
				.build();
		AuthorizationDecision decision = manager.check(
				() -> new JwtAuthenticationToken(jwt), null);

		assertThat(decision.isGranted()).isTrue();
	}

	@Test
	void shouldDenyJwtWithForeignEmailTest() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("user_1")
				.claim("email", "mallory@attacker.example")
				.build();
		AuthorizationDecision decision = manager.check(
				() -> new JwtAuthenticationToken(jwt), null);

		assertThat(decision.isGranted()).isFalse();
	}

	@Test
	void shouldDenyNonJwtAuthenticationTest() {
		AuthorizationDecision decision = manager.check(() -> null, null);

		assertThat(decision.isGranted()).isFalse();
	}
}
