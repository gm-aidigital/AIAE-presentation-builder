package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link AdminReportsService} — validates admin access, then lists every
 * job (read through the report-job entity helper) mapped with owner fields.
 */
@Service
@RequiredArgsConstructor
public class AdminReportsServiceImpl implements AdminReportsService {

	private final ReportJobProgressHelper jobs;
	private final ReportSummaryAssembler assembler;
	private final AdminAccessPolicy adminAccessPolicy;

	@Override
	public List<ReportSummary> allReports(String callerEmail) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		return jobs.listAllJobs().stream().map(assembler::toSummary).toList();
	}
}
