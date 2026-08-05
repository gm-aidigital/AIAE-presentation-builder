package com.aidigital.reportconstructor.service.admin.dto;

import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;

import java.util.List;

/**
 * One page of the team-wide report history.
 *
 * <p>{@code total} counts every deliverable report, not the rows in this page — that is what lets
 * the table say "50 of 12,480" and size its pager without a second request. The page's own length is
 * simply {@code reports.size()}.
 *
 * @param total   deliverable reports in the whole history
 * @param page    zero-based index of this page
 * @param size    rows per page that was applied, after clamping
 * @param hasMore whether another page follows this one
 * @param reports the rows of this page, in the requested order
 */
public record AdminReportPage(
		long total,
		int page,
		int size,
		boolean hasMore,
		List<ReportSummary> reports) {
}
