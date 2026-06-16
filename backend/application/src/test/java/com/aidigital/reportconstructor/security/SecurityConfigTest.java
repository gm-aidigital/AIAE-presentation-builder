package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Clerk-SSO {@link JwtDecoder} wiring.
 */
class SecurityConfigTest {

	private final SecurityProperties securityProperties = new SecurityProperties();
	private final ClerkJwtClaimsValidator clerkJwtClaimsValidator =
			new ClerkJwtClaimsValidator(new AuthProperties());
	private final ClerkPublishableKeyDecoder publishableKeyDecoder =
			new ClerkPublishableKeyDecoder();
	private final CompanyEmailDomainAuthorizationManager companyEmailDomainAuthorizationManager =
			new CompanyEmailDomainAuthorizationManager(new AuthProperties());
	private final SecurityConfig securityConfig = new SecurityConfig(
			securityProperties, clerkJwtClaimsValidator, publishableKeyDecoder,
			companyEmailDomainAuthorizationManager);

	@Test
	void shouldFailFastWhenSsoIsUnconfiguredTest() {
		AuthProperties props = new AuthProperties();
		assertThatThrownBy(() -> securityConfig.buildSsoDecoder(props))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Clerk SSO is required");
	}

	@Test
	void shouldBuildSignatureValidatingJwksDecoderWhenConfiguredTest() {
		AuthProperties props = new AuthProperties();
		props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");
		JwtDecoder decoder = securityConfig.buildSsoDecoder(props);
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
	}

	@Test
	void shouldDeriveIssuerAndJwksFromPublishableKeyTest() {
		AuthProperties props = new AuthProperties();
		String host = "clerk.example.com";
		props.setPublishableKey("pk_test_"
				+ java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(host.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		JwtDecoder decoder = securityConfig.buildSsoDecoder(props);
		assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
		assertThat(props.getSso().getIssuerUri()).isEqualTo("https://clerk.example.com");
		assertThat(props.getSso().getJwkSetUri())
				.isEqualTo("https://clerk.example.com/.well-known/jwks.json");
	}
}
