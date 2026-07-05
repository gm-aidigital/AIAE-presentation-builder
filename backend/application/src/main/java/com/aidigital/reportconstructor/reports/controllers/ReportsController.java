package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.ReportsApi;
import com.aidigital.reportconstructor.api.v1.model.ReportListV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.reports.mappers.ReportsApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the signed-in user's report history ("My reports").
 */
@RestController
@RequiredArgsConstructor
public class ReportsController implements ReportsApi {

	private final ReportHistoryService reportHistory;
	private final ReportsApiMapper mapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<ReportListV1> listMyReports() {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		List<ReportSummaryV1> reports = mapper.toSummaries(reportHistory.historyForOwner(user.userId()));
		return ResponseEntity.ok(new ReportListV1().total(reports.size()).reports(reports));
	}
}
