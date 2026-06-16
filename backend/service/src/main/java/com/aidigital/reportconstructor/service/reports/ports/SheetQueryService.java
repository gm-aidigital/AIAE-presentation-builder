package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;

/**
 * Read access to Google Sheets for the reports aggregate. Implemented in the
 * {@code external-services} module over the underlying Google provider; the
 * application controller depends only on this port so no provider call leaks
 * into the REST layer.
 */
public interface SheetQueryService {

	/**
	 * Reports the caller's Google connectivity.
	 *
	 * @param callerEmail the authenticated caller's email
	 * @return the connection status (connected flag, mock-mode flag, email)
	 */
	GoogleConnectionStatus connectionStatus(String callerEmail);

	/**
	 * Reads a single tab from the spreadsheet at the given URL.
	 *
	 * <p>The read is attempted as the signed-in user (using their Clerk-brokered
	 * Google OAuth token) so the spreadsheet only needs to be viewable by them;
	 * it falls back to the service account when no user token is available.
	 *
	 * @param spreadsheetUrl the Google Sheets URL
	 * @param tab            the tab name to read
	 * @param callerUserId   the caller's Clerk user id ({@code sub}), used to look
	 *                       up their Google OAuth token; {@code null} forces the
	 *                       service-account fallback
	 * @return the fetched sheet data
	 */
	SheetData fetchTab(String spreadsheetUrl, String tab, String callerUserId);
}
