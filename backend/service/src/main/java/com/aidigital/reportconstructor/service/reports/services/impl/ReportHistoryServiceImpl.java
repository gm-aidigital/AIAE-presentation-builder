package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link ReportHistoryService} — reads the owner's jobs through the report-job
 * entity helper and maps each to a display-ready {@link ReportSummary}.
 */
@Service
@RequiredArgsConstructor
public class ReportHistoryServiceImpl implements ReportHistoryService {

	private final ReportJobProgressHelper jobs;
	private final ReportSummaryAssembler assembler;

	@Override
	public List<ReportSummary> historyForOwner(String userId) {
		return jobs.listJobsByOwner(userId).stream().map(assembler::toSummary).toList();
	}
}
