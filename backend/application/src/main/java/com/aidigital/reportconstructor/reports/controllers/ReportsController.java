package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.ReportsApi;
import com.aidigital.reportconstructor.api.v1.model.AdoptSheetRequestV1;
import com.aidigital.reportconstructor.api.v1.model.ReportListV1;
import com.aidigital.reportconstructor.api.v1.model.ReportResumeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.reports.mappers.ReportsApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.reports.services.ReportHistoryService;
import com.aidigital.reportconstructor.service.reports.services.SheetAdoptionService;
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
	private final SheetAdoptionService sheetAdoption;
	private final ReportsApiMapper mapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<ReportListV1> listMyReports() {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		List<ReportSummaryV1> reports = mapper.toSummaries(reportHistory.historyForOwner(user.userId()));
		return ResponseEntity.ok(new ReportListV1().total(reports.size()).reports(reports));
	}

	@Override
	public ResponseEntity<ReportResumeV1> getReportResume(Long jobId) {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		return ResponseEntity.ok(mapper.toResume(reportHistory.resumeForOwner(user.userId(), jobId)));
	}

	@Override
	public ResponseEntity<ReportResumeV1> adoptSheet(AdoptSheetRequestV1 body) {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		var draft = sheetAdoption.adopt(
				user.userId(), user.userId(), user.email(), body.getSheetUrl(), body.getReportType().getValue());
		return ResponseEntity.ok(mapper.toResume(draft));
	}

	@Override
	public ResponseEntity<Void> dismissReport(Long jobId) {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		reportHistory.dismissForOwner(user.userId(), jobId);
		return ResponseEntity.noContent().build();
	}
}
