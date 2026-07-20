package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminUserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriveShareRecipientsTest {

	private final GoogleProperties props = new GoogleProperties();
	private final AdminAccessPolicy accessPolicy = mock(AdminAccessPolicy.class);
	private final AdminUserService adminUserService = mock(AdminUserService.class);
	private final DriveShareRecipients recipients =
			new DriveShareRecipients(props, accessPolicy, adminUserService);

	private AdminUserEntity managed(String email) {
		AdminUserEntity entity = new AdminUserEntity();
		entity.setEmail(email);
		return entity;
	}

	@Test
	void shouldMergeConfiguredEmailsWithConfigAndManagedAdminsTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com", " "));
		when(accessPolicy.configAdminEmails()).thenReturn(List.of("root@x.com"));
		when(adminUserService.listAll()).thenReturn(List.of(managed("managed@x.com")));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com", "root@x.com", "managed@x.com");
	}

	@Test
	void shouldDeduplicateCaseInsensitivelyTest() {
		// Given: the same address configured as a share recipient and as an admin
		props.setShareWithEmails(List.of("Owner@X.com"));
		when(accessPolicy.configAdminEmails()).thenReturn(List.of("owner@x.com"));
		when(adminUserService.listAll()).thenReturn(List.of(managed("OWNER@x.com")));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com");
	}

	@Test
	void shouldStillReturnConfiguredEmailsWhenManagedAdminLookupFailsTest() {
		// Given:
		props.setShareWithEmails(List.of("owner@x.com"));
		when(accessPolicy.configAdminEmails()).thenReturn(List.of("root@x.com"));
		when(adminUserService.listAll()).thenThrow(new IllegalStateException("db down"));

		// When:
		List<String> resolved = recipients.resolve();

		// Then:
		assertThat(resolved).containsExactly("owner@x.com", "root@x.com");
	}
}
