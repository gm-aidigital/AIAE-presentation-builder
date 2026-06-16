package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClerkPublishableKeyDecoderTest {

	private final ClerkPublishableKeyDecoder decoder = new ClerkPublishableKeyDecoder();

	private String makeKey(String host) {
		return "pk_test_"
				+ Base64.getUrlEncoder().withoutPadding()
				.encodeToString(host.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void shouldDecodeIssuerAndJwksFromPublishableKeyTest() {
		String pk = makeKey("clerk.example.com");

		assertThat(decoder.decodeFrontendApiHost(pk)).isEqualTo("clerk.example.com");
		assertThat(decoder.issuerFromPublishableKey(pk)).isEqualTo("https://clerk.example.com");
		assertThat(decoder.jwksUriFromPublishableKey(pk))
				.isEqualTo("https://clerk.example.com/.well-known/jwks.json");
	}

	@Test
	void shouldStripTrailingDollarDelimiterTest() {
		String pk = makeKey("clerk.example.com$");
		assertThat(decoder.decodeFrontendApiHost(pk)).isEqualTo("clerk.example.com");
	}

	@Test
	void shouldReturnNullForBlankKeyTest() {
		assertThat(decoder.decodeFrontendApiHost(null)).isNull();
		assertThat(decoder.issuerFromPublishableKey("")).isNull();
		assertThat(decoder.jwksUriFromPublishableKey("   ")).isNull();
	}

	@Test
	void shouldRejectKeyWithWrongPrefixTest() {
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost("sk_test_abc"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("pk_test_");
	}

	@Test
	void shouldRejectMalformedBase64PayloadTest() {
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost("pk_test_!!!not-base64!!!"))
				.isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://clerk.example.com",  // scheme present
			"clerk.example.com/path",     // path present
			"clerk.example.com?q=1",      // query present
			"user@clerk.example.com",     // userinfo present
			"clerk.example.com:443",      // port present
			"clerk .example.com",         // whitespace
			"-badlabel.example.com",      // leading hyphen
			"bad..label.example.com"      // empty label
	})
	void shouldRejectMalformedHostTest(String malformedHost) {
		String pk = makeKey(malformedHost);
		assertThatThrownBy(() -> decoder.decodeFrontendApiHost(pk))
				.isInstanceOf(IllegalStateException.class);
	}
}
