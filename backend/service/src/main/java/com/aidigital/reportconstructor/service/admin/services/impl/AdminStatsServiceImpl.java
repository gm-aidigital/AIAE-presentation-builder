package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.admin.AdminFailureAssembler;
import com.aidigital.reportconstructor.service.admin.AdminTokenAggregator;
import com.aidigital.reportconstructor.service.admin.ReportCountPolicy;
import com.aidigital.reportconstructor.service.admin.dto.AdminDayVolume;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTypeStat;
import com.aidigital.reportconstructor.service.admin.dto.AdminUserStat;
import com.aidigital.reportconstructor.service.admin.services.AdminStatsService;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link AdminStatsService} — validates admin access, then aggregates every
 * {@code report_jobs} row (read through the report-job entity helper) into the dashboard
 * payload. All figures are derived; nothing is faked.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

	private static final String STATUS_ERROR = "error";
	private static final String STATUS_QUEUED = "queued";
	private static final String STATUS_RUNNING = "running";
	private static final String TYPE_OTHER = "OTHER";
	private static final int WEEK_DAYS = 7;

	/** Failures shown on the dashboard — enough to spot a pattern, bounded so the payload stays small. */
	private static final int FAILURE_LIMIT = 50;

	private final ReportJobProgressHelper jobs;
	private final AdminAccessPolicy adminAccessPolicy;
	private final DisplayNameHelper displayNameHelper;
	private final AdminTokenAggregator tokenAggregator;
	private final ClaudeUsageEventService usageEvents;
	private final JobTokenUsage tokenUsage;
	private final AdminFailureAssembler failureAssembler;
	private final ReportCountPolicy reportCountPolicy;

	@Override
	public AdminStats statsFor(String callerEmail) {
		if (!adminAccessPolicy.isAdmin(callerEmail)) {
			throw new AppException(ErrorReason.C004, "Admin access required");
		}
		List<ReportJobEntity> all = jobs.listAllJobs();
		var events = usageEvents.listAll();
		OffsetDateTime now = OffsetDateTime.now();
		Set<Long> intermediateSheetJobIds = all.stream()
				.filter(reportCountPolicy::isIntermediateSheet)
				.map(ReportJobEntity::getId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		return new AdminStats(
				now.toLocalDateTime(),
				totals(all, now),
				byUser(all, now),
				byType(all),
				weekly(all, now),
				tokenAggregator.totals(events, now, intermediateSheetJobIds),
				tokenAggregator.weekly(events, now),
				tokenAggregator.byLabel(events),
				failureAssembler.recentFailures(all, FAILURE_LIMIT));
	}

	/**
	 * Computes the headline counters shown on the stat cards.
	 *
	 * @param all all report jobs
	 * @param now reference time for the "this month" window
	 * @return the aggregated totals
	 */
	AdminTotals totals(List<ReportJobEntity> all, OffsetDateTime now) {
		// Report-volume counters exclude a slide-deck flow's intermediate sheet step so one two-step run
		// is one report; the operational counters (running/failed) read every job so no in-flight or broken
		// step is hidden.
		int reportsTotal = (int) all.stream().filter(reportCountPolicy::isCountableReport).count();
		int thisMonth = (int) all.stream()
				.filter(reportCountPolicy::isCountableReport)
				.filter(j -> isSameMonth(j.getCreatedAt(), now)).count();
		int activeUsers = (int) all.stream().map(ReportJobEntity::getOwnerUserId).distinct().count();
		int running = (int) all.stream()
				.filter(j -> STATUS_QUEUED.equals(j.getStatus()) || STATUS_RUNNING.equals(j.getStatus()))
				.count();
		int failed = (int) all.stream().filter(j -> STATUS_ERROR.equals(j.getStatus())).count();
		return new AdminTotals(reportsTotal, thisMonth, activeUsers, running, failed);
	}

	/**
	 * Groups jobs by owner into per-user rows carrying both report counts and token spend,
	 * most reports first.
	 *
	 * @param all all report jobs
	 * @param now reference time for the "this month" window
	 * @return per-user activity rows
	 */
	List<AdminUserStat> byUser(List<ReportJobEntity> all, OffsetDateTime now) {
		Map<String, List<ReportJobEntity>> byOwner = new LinkedHashMap<>();
		for (ReportJobEntity job : all) {
			byOwner.computeIfAbsent(job.getOwnerUserId(), k -> new ArrayList<>()).add(job);
		}
		List<AdminUserStat> rows = new ArrayList<>();
		for (Map.Entry<String, List<ReportJobEntity>> entry : byOwner.entrySet()) {
			List<ReportJobEntity> owned = entry.getValue();
			String email = owned.stream()
					.map(ReportJobEntity::getOwnerEmail)
					.filter(e -> e != null && !e.isBlank())
					.findFirst().orElse(null);
			// Report counts exclude the intermediate sheet step; token sums keep every job, so the user's
			// real spend still shows even though the sheet step does not add to their report tally.
			int reports = (int) owned.stream().filter(reportCountPolicy::isCountableReport).count();
			int thisMonth = (int) owned.stream()
					.filter(reportCountPolicy::isCountableReport)
					.filter(j -> isSameMonth(j.getCreatedAt(), now)).count();
			OffsetDateTime last = owned.stream()
					.map(ReportJobEntity::getCreatedAt)
					.filter(Objects::nonNull)
					.max(Comparator.naturalOrder()).orElse(null);
			long input = owned.stream().mapToLong(tokenUsage::inputTokens).sum();
			long output = owned.stream().mapToLong(tokenUsage::outputTokens).sum();
			long cache = owned.stream()
					.mapToLong(job -> tokenUsage.cacheWriteTokens(job) + tokenUsage.cacheReadTokens(job)).sum();
			double cost = owned.stream().mapToDouble(tokenUsage::costUsd).sum();
			rows.add(new AdminUserStat(entry.getKey(), email, displayNameHelper.fromEmail(email), reports,
					thisMonth, last == null ? null : last.toLocalDateTime(),
					input, output, cache, input + output + cache, cost));
		}
		rows.sort(Comparator.comparingInt(AdminUserStat::total).reversed());
		return rows;
	}

	/**
	 * Counts jobs per report type, most reports first.
	 *
	 * @param all all report jobs
	 * @return per-type counts
	 */
	List<AdminTypeStat> byType(List<ReportJobEntity> all) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ReportJobEntity job : all) {
			if (!reportCountPolicy.isCountableReport(job)) {
				continue;
			}
			counts.merge(normalizeType(job.getReportTypeCode()), 1, Integer::sum);
		}
		return counts.entrySet().stream()
				.map(e -> new AdminTypeStat(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingInt(AdminTypeStat::count).reversed())
				.toList();
	}

	/**
	 * Builds the last {@value #WEEK_DAYS} days of report volume, oldest first.
	 *
	 * @param all all report jobs
	 * @param now reference time whose local date anchors "today"
	 * @return one volume point per day for the trailing week
	 */
	List<AdminDayVolume> weekly(List<ReportJobEntity> all, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();
		List<AdminDayVolume> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			int count = (int) all.stream()
					.filter(reportCountPolicy::isCountableReport)
					.filter(j -> j.getCreatedAt() != null && day.equals(j.getCreatedAt().toLocalDate()))
					.count();
			String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			series.add(new AdminDayVolume(day, label, count));
		}
		return series;
	}

	/**
	 * Normalizes a stored report-type code to an uppercase bucket, folding blanks to {@code OTHER}.
	 *
	 * @param code raw {@code report_type_code}, possibly {@code null}
	 * @return uppercased type code, or {@code OTHER}
	 */
	String normalizeType(String code) {
		return (code == null || code.isBlank()) ? TYPE_OTHER : code.trim().toUpperCase(Locale.ROOT);
	}

	/**
	 * Tells whether a timestamp falls in the same calendar month and year as the reference.
	 *
	 * @param when      timestamp under test, possibly {@code null}
	 * @param reference reference time
	 * @return true when {@code when} shares the reference year and month
	 */
	boolean isSameMonth(OffsetDateTime when, OffsetDateTime reference) {
		return when != null
				&& when.getYear() == reference.getYear()
				&& when.getMonth() == reference.getMonth();
	}
}
