package com.aidigital.reportconstructor.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthStartupValidatorTest {

	@Test
	void shouldFailWhenAuthorizedPartiesBlankTest() {
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("   ");
		AuthStartupValidator validator = new AuthStartupValidator(props);

		assertThatThrownBy(validator::afterPropertiesSet)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AUTH_AUTHORIZED_PARTIES");
	}

	@Test
	void shouldPassWhenAuthorizedPartiesConfiguredTest() {
		AuthProperties props = new AuthProperties();
		props.setAuthorizedParties("http://localhost:5173");
		AuthStartupValidator validator = new AuthStartupValidator(props);

		assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
	}
}
