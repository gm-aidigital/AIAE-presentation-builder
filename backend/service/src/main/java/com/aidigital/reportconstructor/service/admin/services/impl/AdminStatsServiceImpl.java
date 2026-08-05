package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminActiveUsersBuilder;
import com.aidigital.reportconstructor.service.admin.AdminDateRangeResolver;
import com.aidigital.reportconstructor.service.admin.AdminFailureAssembler;
import com.aidigital.reportconstructor.service.admin.AdminPeriodBucketer;
import com.aidigital.reportconstructor.service.admin.AdminRollupTokenTotals;
import com.aidigital.reportconstructor.service.admin.AdminRollupTotals;
import com.aidigital.reportconstructor.service.admin.AdminRollupUserStats;
import com.aidigital.reportconstructor.service.admin.AdminSavingsCalculator;
import com.aidigital.reportconstructor.service.admin.AdminStatsCache;
import com.aidigital.reportconstructor.service.admin.AdminTokenAggregator;
import com.aidigital.reportconstructor.service.admin.AdminTrendBuilder;
import com.aidigital.reportconstructor.service.admin.UsageRollupRefresher;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminRangeView;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.UsageDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Default {@link AdminStatsService}.
 *
 * <p>Validates admin access, makes sure the {@code usage_daily} rollup is current, then assembles the
 * dashboard payload for one window of dates out of a handful of bounded reads: the rollup by day, by
 * user, and its distinct active (day, user) pairs, the per-stage usage aggregate, and the live
 * operational counters. None of them loads a report job into memory, which is the whole point — the
 * previous implementation read every {@code report_jobs} row, JSONB payloads included, and made
 * roughly ten stream passes over the result on every request.
 *
 * <p>All figures are derived; nothing is faked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

	/** Failures shown on the dashboard — enough to spot a pattern, bounded so the payload stays small. */
	private static final int FAILURE_LIMIT = 50;

	private final ReportJobProgressHelper jobs;
	private final UsageDailyService usageDaily;
	private final ClaudeUsageEventService usageEvents;
	private final UsageRollupRefresher rollupRefresher;
	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminDateRangeResolver rangeResolver;
	private final AdminPeriodBucketer bucketer;
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
	public AdminStats statsFor(String callerEmail, LocalDate from, LocalDate to) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		if (refreshRollup()) {
			statsCache.invalidate();
		}
		AdminDateRange range = rangeResolver.resolve(from, to, LocalDate.now());
		return statsCache.snapshot(range, () -> assemble(range));
	}

	/**
	 * Brings the rollup up to date before the figures are read, and reports whether it moved.
	 *
	 * <p>A report that finished a minute ago must be visible, and the refresh timer alone would leave
	 * it out until its next tick. But refreshing a cache is not what the caller asked for: the numbers
	 * behind it are still intact in {@code report_jobs}, so a refresh that fails must degrade to
	 * slightly stale figures rather than to an error page. The refresher already swallows its own
	 * failures; this is the outer guarantee, so that nothing escaping it can turn a working dashboard
	 * into a 500.
	 *
	 * @return true when the rollup was rebuilt, so anything derived from it is now stale
	 */
	boolean refreshRollup() {
		try {
			return rollupRefresher.ensureFresh();
		} catch (Exception ex) {
			log.warn("[admin] usage rollup could not be refreshed; serving the figures as they stand", ex);
			return false;
		}
	}

	/**
	 * Assembles the dashboard payload for one window, from the rollup and the live counters.
	 *
	 * <p>Separate from {@link #statsFor} because this is the expensive half and the half that is
	 * cached; the access check and the freshness check must happen on every request.
	 *
	 * @param range the window to report on, already resolved and clamped
	 * @return the assembled snapshot
	 */
	AdminStats assemble(AdminDateRange range) {
		List<UsageDailyBucket> days = usageDaily.byDay(range.from(), range.to());
		List<UsageDailyUserRow> userRows = usageDaily.byUser(range.from(), range.to());

		// Active users need history from before the window to tell an arrival from a return: someone
		// who first appeared last year is not a new user this month. The window's own figures are then
		// taken from this longer series rather than from a second, shorter read.
		List<UsageActiveDay> activeHistory = usageDaily.activeDays(range.from().minusYears(1), range.to());

		OffsetDateTime eventsFrom = range.from().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
		OffsetDateTime eventsTo = range.to().plusDays(1)
				.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
		List<ClaudeLabelUsage> byLabel = usageEvents.byLabel(eventsFrom, eventsTo);
		List<ClaudeLabelUsage> unattributed = usageEvents.unattributed(eventsFrom, eventsTo);

		AdminPeriodUnit seriesUnit = bucketer.chartUnitFor(
				ChronoUnit.DAYS.between(range.from(), range.to()) + 1);
		OffsetDateTime rollupUpdatedAt = usageDaily.lastRefreshedAt();

		return new AdminStats(
				OffsetDateTime.now().toLocalDateTime(),
				rollupUpdatedAt == null ? null : rollupUpdatedAt.toLocalDateTime(),
				new AdminRangeView(range.from(), range.to(), range.unit().getCode()),
				rollupTotals.totals(days,
						activeUsersBuilder.activeInWindow(activeHistory, range.from()),
						activeUsersBuilder.newInWindow(activeHistory, range.from()),
						jobs.countInFlight(), jobs.countFailed()),
				savingsCalculator.calculate(days),
				rollupUserStats.build(userRows, jobs.listOwners()),
				rollupTotals.byType(days),
				trendBuilder.build(days, seriesUnit),
				seriesUnit.getCode(),
				rollupTokenTotals.totals(days, unattributed, byLabel),
				trendBuilder.build(days, AdminPeriodUnit.WEEK),
				trendBuilder.build(days, AdminPeriodUnit.MONTH),
				withinWindow(activeUsersBuilder.build(activeHistory, AdminPeriodUnit.WEEK), range),
				withinWindow(activeUsersBuilder.build(activeHistory, AdminPeriodUnit.MONTH), range),
				tokenAggregator.byLabel(byLabel),
				failureAssembler.recentFailures(jobs.listRecentIssues(FAILURE_LIMIT), FAILURE_LIMIT));
	}

	/**
	 * Trims an active-user series back to the buckets the window covers.
	 *
	 * <p>The series is built over a longer history so that first-time users can be recognised, but the
	 * dashboard must not then show months the viewer did not ask about. A bucket the window covers
	 * only partly is kept: dropping it would hide activity that did happen inside the window.
	 *
	 * @param series the full series, oldest first
	 * @param range  the window being reported on
	 * @return the buckets overlapping the window
	 */
	List<AdminActiveUsersPeriod> withinWindow(List<AdminActiveUsersPeriod> series, AdminDateRange range) {
		return series.stream()
				.filter(period -> !period.start().isAfter(range.to()))
				.filter(period -> !period.start().isBefore(range.from().withDayOfMonth(1)))
				.toList();
	}
}
