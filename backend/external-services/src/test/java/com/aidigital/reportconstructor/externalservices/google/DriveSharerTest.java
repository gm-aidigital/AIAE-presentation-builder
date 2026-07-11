package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DriveSharerTest {

	private final DriveSharer sharer = new DriveSharer();

	@Test
	void shouldCreateOneWriterPermissionPerEmailTest() throws IOException {
		// Given:
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);
		Drive.Permissions.Create create = mock(Drive.Permissions.Create.class, RETURNS_DEEP_STUBS);
		when(drive.permissions().create(eq("file-1"), any(Permission.class))).thenReturn(create);
		when(create.setSendNotificationEmail(any())).thenReturn(create);
		when(create.setSupportsAllDrives(any())).thenReturn(create);

		// When:
		sharer.shareWith(drive, "file-1", List.of("a@x.com", "b@x.com"));

		// Then:
		verify(drive.permissions(), times(2)).create(eq("file-1"), any(Permission.class));
		verify(create, times(2)).setSendNotificationEmail(false);
	}

	@Test
	void shouldSkipBlankAndNullEmailsTest() throws IOException {
		// Given:
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);
		Drive.Permissions.Create create = mock(Drive.Permissions.Create.class, RETURNS_DEEP_STUBS);
		when(drive.permissions().create(eq("file-1"), any(Permission.class))).thenReturn(create);
		when(create.setSendNotificationEmail(any())).thenReturn(create);
		when(create.setSupportsAllDrives(any())).thenReturn(create);

		// When:
		sharer.shareWith(drive, "file-1", java.util.Arrays.asList(" ", null, "real@x.com"));

		// Then:
		verify(drive.permissions(), times(1)).create(eq("file-1"), any(Permission.class));
	}

	@Test
	void shouldDoNothingWhenNoEmailsTest() {
		// Given:
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);

		// When:
		sharer.shareWith(drive, "file-1", List.of());

		// Then:
		verifyNoInteractions(drive);
	}

	@Test
	void shouldSwallowFailuresAndContinueToNextRecipientTest() throws IOException {
		// Given:
		Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);
		Drive.Permissions.Create failing = mock(Drive.Permissions.Create.class, RETURNS_DEEP_STUBS);
		Drive.Permissions.Create ok = mock(Drive.Permissions.Create.class, RETURNS_DEEP_STUBS);
		when(drive.permissions().create(eq("file-1"), any(Permission.class))).thenReturn(failing, ok);
		when(failing.setSendNotificationEmail(any())).thenReturn(failing);
		when(failing.setSupportsAllDrives(any())).thenReturn(failing);
		when(failing.execute()).thenThrow(new IOException("boom"));
		when(ok.setSendNotificationEmail(any())).thenReturn(ok);
		when(ok.setSupportsAllDrives(any())).thenReturn(ok);

		// When / Then: the first recipient's failure never propagates...
		assertThatCode(() -> sharer.shareWith(drive, "file-1", List.of("bad@x.com", "good@x.com")))
				.doesNotThrowAnyException();
		// ...and the second recipient is still attempted.
		verify(ok, times(1)).execute();
		verify(drive.permissions(), never()).delete(any(), any());
	}
}
