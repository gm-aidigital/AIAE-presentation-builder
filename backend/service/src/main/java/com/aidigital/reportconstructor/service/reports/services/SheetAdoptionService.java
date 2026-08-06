package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.ReportResume;

/**
 * Adopts a workbook the constructor did not build.
 *
 * <p>A user who already has a filled report workbook should not have to walk the media-plan,
 * Elevate, matching and pacing steps again just to reach the deck: none of that survives into the
 * slides-from-sheet step, which reads every number, name and date back out of the workbook. This
 * registers such a workbook as a finished sheet build, so it becomes a draft like any other and the
 * ordinary resume path takes it from there.
 *
 * <p>Costs no Claude call: it reads the workbook, validates it, and writes one job row.
 */
public interface SheetAdoptionService {

	/**
	 * Validates a user-supplied workbook and registers it as a resumable draft.
	 *
	 * @param userId      internal id of the adopting user, who becomes the draft's owner
	 * @param clerkUserId Clerk identity used to fetch the Google access token for the sheet read
	 * @param userEmail   email of the adopting user, used to name the eventual deck
	 * @param sheetUrl    URL of the user's filled Google Sheet
	 * @param reportType  report template code the deck should be built as (EOC/EOM)
	 * @return the new draft, in the same shape the resume endpoint serves
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException when the workbook
	 *         cannot be read or does not look like a report workbook
	 */
	ReportResume adopt(String userId, String clerkUserId, String userEmail, String sheetUrl, String reportType);
}
