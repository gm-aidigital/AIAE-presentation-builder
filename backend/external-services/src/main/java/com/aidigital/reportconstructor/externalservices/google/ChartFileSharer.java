package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Auto-shares the chart source workbooks the chart engine creates, so the people who already get the
 * deck and the EOC workbook ({@link DriveShareRecipients}) can also open the spreadsheets that feed
 * every linked chart. Preferably the whole per-report chart folder is shared once — the copies inside
 * inherit that access; only when the folder could not be created is each loose copy shared on its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartFileSharer {

	private final DriveSharer driveSharer;
	private final DriveShareRecipients shareRecipients;

	/**
	 * Shares the per-report chart folder with the standing recipients. A null/blank folder id (folder
	 * creation failed, copies land in the drive root) is a no-op — {@link #shareLooseCopy} covers that case.
	 *
	 * @param drive    authenticated Drive client for this request
	 * @param folderId Drive id of the chart output folder, or {@code null} when there is none
	 */
	public void shareFolder(Drive drive, String folderId) {
		if (folderId == null || folderId.isBlank()) {
			return;
		}
		driveSharer.shareWith(drive, folderId, shareRecipients.resolve());
	}

	/**
	 * Shares a single chart workbook copy, but only when it is not inside a shared chart folder — a copy in
	 * the folder already inherits access, so this avoids one Drive permission call per chart.
	 *
	 * @param drive    authenticated Drive client for this request
	 * @param folderId Drive id of the chart output folder, or {@code null} when the copy is loose
	 * @param copyId   Drive id of the chart workbook copy
	 */
	public void shareLooseCopy(Drive drive, String folderId, String copyId) {
		if (folderId != null && !folderId.isBlank()) {
			return;
		}
		if (copyId == null || copyId.isBlank()) {
			return;
		}
		driveSharer.shareWith(drive, copyId, shareRecipients.resolve());
	}
}
