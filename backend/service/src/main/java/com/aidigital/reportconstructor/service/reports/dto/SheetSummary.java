package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Everything the review step reads back out of a generated report workbook: the per-tactic
 * summary table plus the two campaign-context cells the deck's narrative is written from.
 *
 * <p>The context cells travel with the rows deliberately. A resumed draft only carries the brief
 * as it stood when the workbook was built, so a user who fills {@code {{RFP info}}} in the sheet
 * afterwards would otherwise still be told the sheet has no brief — the review step reads the
 * live cell instead.
 *
 * @param rows      one entry per tactic, in the workbook's summary-table order
 * @param rfpInfo   the workbook's {@code {{RFP info}}} cell, or {@code null} when never filled
 * @param changeLog the workbook's {@code {{change log}}} cell, or {@code null} when never filled
 */
public record SheetSummary(
		List<SheetSummaryRow> rows,
		String rfpInfo,
		String changeLog
) {
	// required
}
