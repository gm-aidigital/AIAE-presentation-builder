package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;

import java.util.List;

/**
 * Read-back access to the per-tactic summary table of a generated report workbook. Lets the
 * review step show the plan/fact figures the sheet actually carries instead of re-deriving them
 * on the client, reusing the same first-tab read and placeholder parsing as the slides-from-sheet
 * pipeline.
 */
public interface SheetSummaryQueryService {

	/**
	 * Reads the per-tactic summary table off the generated workbook's first tab and returns each
	 * tactic's plan/fact figures in summary-table order.
	 *
	 * <p>The read is attempted as the signed-in user (using their Clerk-brokered Google OAuth
	 * token) and falls back to the service account when no user token is available. Returns an
	 * empty list when the workbook carries no summary table.
	 *
	 * @param sheetUrl     the generated workbook's Google Sheets URL
	 * @param callerUserId the caller's Clerk user id, used to look up their Google OAuth token;
	 *                     {@code null} forces the service-account fallback
	 * @return one {@link SheetSummaryRow} per tactic, in the workbook's summary-table order
	 */
	List<SheetSummaryRow> readSummary(String sheetUrl, String callerUserId);
}
