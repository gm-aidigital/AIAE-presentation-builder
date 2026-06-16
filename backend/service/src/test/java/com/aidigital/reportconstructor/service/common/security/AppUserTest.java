package com.aidigital.reportconstructor.service.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

	@Test
	void shouldExposeIdentityFieldsTest() {
		AppUser user = new AppUser("user_abc123", "alice@aidigital.com", "Alice Example");

		assertThat(user.userId()).isEqualTo("user_abc123");
		assertThat(user.email()).isEqualTo("alice@aidigital.com");
		assertThat(user.fullName()).isEqualTo("Alice Example");
	}

	@Test
	void shouldSupportRecordEqualityTest() {
		AppUser a = new AppUser("user_1", "alice@aidigital.com", "Alice");
		AppUser b = new AppUser("user_1", "alice@aidigital.com", "Alice");

		assertThat(a).isEqualTo(b);
	}
}
