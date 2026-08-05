package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminReportSortResolver;
import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link AdminReportsService} — validates admin access, then reads one page of deliverable
 * jobs (through the report-job entity helper) and maps it with owner fields.
 *
 * <p>The filter that keeps a slide-deck flow's intermediate sheet step out of the list now lives in
 * the query rather than in a stream over every row, so a page of fifty reports costs a page of fifty
 * rows however long the history is.
 */
@Service
@RequiredArgsConstructor
public class AdminReportsServiceImpl implements AdminReportsService {

	private final ReportJobProgressHelper jobs;
	private final ReportSummaryAssembler assembler;
	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminReportSortResolver sortResolver;

	@Override
	public AdminReportPage allReports(String callerEmail, Integer page, Integer size, String sort, String dir) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		Pageable pageable = sortResolver.pageable(page, size, sort, dir);
		Page<ReportJobEntity> found = jobs.listDeliverables(pageable);
		List<ReportSummary> reports = found.getContent().stream().map(assembler::toSummary).toList();
		return new AdminReportPage(
				found.getTotalElements(),
				found.getNumber(),
				found.getSize(),
				found.hasNext(),
				reports);
	}
}
