package com.aidigital.reportconstructor.admin.controllers;

import com.aidigital.reportconstructor.admin.mappers.AdminManagementApiMapper;
import com.aidigital.reportconstructor.admin.mappers.AdminStatsApiMapper;
import com.aidigital.reportconstructor.api.v1.AdminApi;
import com.aidigital.reportconstructor.api.v1.model.AddAdminRequestV1;
import com.aidigital.reportconstructor.api.v1.model.AdminListV1;
import com.aidigital.reportconstructor.api.v1.model.AdminReportPageV1;
import com.aidigital.reportconstructor.api.v1.model.AdminStatsV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.reports.mappers.ReportsApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.admin.dto.AdminReportPage;
import com.aidigital.reportconstructor.service.admin.services.AdminFailuresService;
import com.aidigital.reportconstructor.service.admin.services.AdminManagementService;
import com.aidigital.reportconstructor.service.admin.services.AdminReportsService;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the admin dashboard: statistics, team-wide report history, and
 * managing admins by email. Authorization (email allow-list) is enforced by the admin
 * services, which throw {@code C004}/403 for non-admin callers.
 */
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

	private final AdminStatsService adminStats;
	private final AdminReportsService adminReports;
	private final AdminManagementService adminManagement;
	private final AdminFailuresService adminFailures;
	private final AdminStatsApiMapper statsMapper;
	private final AdminManagementApiMapper adminMapper;
	private final ReportsApiMapper reportsMapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<AdminStatsV1> getAdminStats() {
		return ResponseEntity.ok(statsMapper.toStats(adminStats.statsFor(caller().email())));
	}

	@Override
	public ResponseEntity<Void> clearAdminFailures() {
		adminFailures.clearFailures(caller().email());
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Void> resolveAdminFailure(Long jobId) {
		adminFailures.resolveFailure(caller().email(), jobId);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<AdminReportPageV1> listAllReports(
			Integer page, Integer size, String sort, String dir) {
		AdminReportPage found = adminReports.allReports(caller().email(), page, size, sort, dir);
		List<ReportSummaryV1> reports = reportsMapper.toSummaries(found.reports());
		return ResponseEntity.ok(new AdminReportPageV1()
				.total(found.total())
				.page(found.page())
				.size(found.size())
				.hasMore(found.hasMore())
				.reports(reports));
	}

	@Override
	public ResponseEntity<AdminListV1> listAdmins() {
		return ResponseEntity.ok(toList(adminManagement.listAdmins(caller().email())));
	}

	@Override
	public ResponseEntity<AdminListV1> addAdmin(AddAdminRequestV1 body) {
		return ResponseEntity.ok(toList(adminManagement.addAdmin(caller().email(), body.getEmail())));
	}

	@Override
	public ResponseEntity<AdminListV1> removeAdmin(String email) {
		return ResponseEntity.ok(toList(adminManagement.removeAdmin(caller().email(), email)));
	}

	/**
	 * Resolves the current caller from the security context.
	 *
	 * @return the authenticated caller
	 */
	AppUser caller() {
		return appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
	}

	/**
	 * Wraps mapped admin entries in the V1 list envelope.
	 *
	 * @param entries the service admin entries
	 * @return the V1 admin list
	 */
	AdminListV1 toList(List<com.aidigital.reportconstructor.service.admin.dto.AdminEntry> entries) {
		return new AdminListV1().admins(adminMapper.toEntries(entries));
	}
}
