package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminActiveUsersBuilder;
import com.aidigital.reportconstructor.service.admin.AdminFailureAssembler;
import com.aidigital.reportconstructor.service.admin.AdminRollupTokenTotals;
import com.aidigital.reportconstructor.service.admin.AdminRollupTotals;
import com.aidigital.reportconstructor.service.admin.AdminRollupUserStats;
import com.aidigital.reportconstructor.service.admin.AdminSavingsCalculator;
import com.aidigital.reportconstructor.service.admin.AdminStatsCache;
import com.aidigital.reportconstructor.service.admin.AdminTokenAggregator;
import com.aidigital.reportconstructor.service.admin.AdminTrendBuilder;
import com.aidigital.reportconstructor.service.admin.UsageRollupRefresher;
import com.aidigital.reportconstructor.service.admin.config.UsageRollupProperties;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.UsageDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Default {@link AdminStatsService}.
 *
 * <p>Validates admin access, makes sure the {@code usage_daily} rollup is current, then assembles
 * the dashboard payload out of five bounded reads: the rollup by day, the rollup by user, the
 * rollup's distinct active (day, user) pairs, the per-stage usage aggregate, and the live
 * operational counters. None of them loads a report job into memory, which is the whole point — the
 * previous implementation read every {@code report_jobs} row, JSONB payloads included, and made
 * roughly ten stream passes over the result on every request.
 *
 * <p>All figures are derived; nothing is faked.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

	/** Failures shown on the dashboard — enough to spot a pattern, bounded so the payload stays small. */
	private static final int FAILURE_LIMIT = 50;

	private final ReportJobProgressHelper jobs;
	private final UsageDailyService usageDaily;
	private final ClaudeUsageEventService usageEvents;
	private final UsageRollupRefresher rollupRefresher;
	private final UsageRollupProperties rollupProps;
	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminRollupTotals rollupTotals;
	private final AdminRollupTokenTotals rollupTokenTotals;
	private final AdminRollupUserStats rollupUserStats;
	private final AdminTrendBuilder trendBuilder;
	private final AdminActiveUsersBuilder activeUsersBuilder;
	private final AdminSavingsCalculator savingsCalculator;
	private final AdminTokenAggregator tokenAggregator;
	private final AdminFailureAssembler failureAssembler;
	private final AdminStatsCache statsCache;

	@Override
	public AdminStats statsFor(String callerEmail) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		// A report that finished a minute ago must be visible; the refresh timer alone would leave it
		// out until its next tick. When that rebuild actually moves the numbers, the snapshot built on
		// the old ones goes with it — the freshness check itself stays outside the cache, so a cache
		// hit can never mean nobody looked.
		if (rollupRefresher.ensureFresh()) {
			statsCache.invalidate();
		}
		return statsCache.snapshot(this::assemble);
	}

	/**
	 * Assembles the dashboard payload from the rollup and the live counters.
	 *
	 * <p>Separate from {@link #statsFor} because this is the expensive half and the half that is
	 * cached; the access check and the freshness check must happen on every request.
	 *
	 * @return the assembled snapshot
	 */
	AdminStats assemble() {
		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(Math.max(1, rollupProps.getHistoryDays()));
		LocalDate monthStart = today.withDayOfMonth(1);

		List<UsageDailyBucket> days = usageDaily.byDay(from);
		List<UsageDailyUserRow> userRows = usageDaily.byUser(from, monthStart);
		List<UsageActiveDay> activeDays = usageDaily.activeDays(from);
		List<ClaudeLabelUsage> byLabel = usageEvents.byLabel();
		List<ClaudeLabelUsage> unattributed = usageEvents.unattributed();
		OffsetDateTime rollupUpdatedAt = usageDaily.lastRefreshedAt();

		return new AdminStats(
				OffsetDateTime.now().toLocalDateTime(),
				rollupUpdatedAt == null ? null : rollupUpdatedAt.toLocalDateTime(),
				rollupTotals.totals(days, activeDays, jobs.countInFlight(), jobs.countFailed(), today),
				savingsCalculator.calculate(days, today),
				rollupUserStats.build(userRows, jobs.listOwners()),
				rollupTotals.byType(days),
				rollupTotals.weekly(days, today),
				rollupTokenTotals.totals(days, unattributed, byLabel, today),
				rollupTotals.tokenWeekly(days, today),
				trendBuilder.build(days, AdminPeriodUnit.WEEK),
				trendBuilder.build(days, AdminPeriodUnit.MONTH),
				activeUsersBuilder.build(activeDays, AdminPeriodUnit.WEEK),
				activeUsersBuilder.build(activeDays, AdminPeriodUnit.MONTH),
				tokenAggregator.byLabel(byLabel),
				failureAssembler.recentFailures(jobs.listRecentIssues(FAILURE_LIMIT), FAILURE_LIMIT));
	}
}
