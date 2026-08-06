package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChartFileSharerTest {

	private final DriveSharer driveSharer = mock(DriveSharer.class);
	private final DriveShareRecipients recipients = mock(DriveShareRecipients.class);
	private final ChartFileSharer sharer = new ChartFileSharer(driveSharer, recipients);
	private final Drive drive = mock(Drive.class, RETURNS_DEEP_STUBS);

	@Test
	void shouldShareTheChartFolderWithTheStandingRecipientsTest() {
		// Given: the usual recipients
		when(recipients.resolve()).thenReturn(List.of("admin@x.com"));

		// When: the per-report chart folder is shared
		sharer.shareFolder(drive, "folder-1");

		// Then: one share call for the folder itself — the copies inside inherit it
		verify(driveSharer).shareWith(eq(drive), eq("folder-1"), eq(List.of("admin@x.com")));
	}

	@Test
	void shouldNotShareAFolderThatWasNeverCreatedTest() {
		// When: folder creation failed
		sharer.shareFolder(drive, null);
		sharer.shareFolder(drive, " ");

		// Then: nothing is shared
		verifyNoInteractions(driveSharer);
	}

	@Test
	void shouldShareALooseCopyOnlyWhenThereIsNoFolderTest() {
		// Given: the usual recipients
		when(recipients.resolve()).thenReturn(List.of("admin@x.com"));

		// When: one copy lands in the shared folder, another in the drive root
		sharer.shareLooseCopy(drive, "folder-1", "copy-in-folder");
		sharer.shareLooseCopy(drive, null, "loose-copy");

		// Then: only the loose copy needs its own permission
		verify(driveSharer).shareWith(eq(drive), eq("loose-copy"), eq(List.of("admin@x.com")));
		verify(driveSharer, org.mockito.Mockito.never())
				.shareWith(eq(drive), eq("copy-in-folder"), org.mockito.ArgumentMatchers.anyList());
	}
}
