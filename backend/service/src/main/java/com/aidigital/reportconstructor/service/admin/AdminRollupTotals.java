package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminDayVolume;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTypeStat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the dashboard's headline counters and its day-by-day series out of the {@code usage_daily}
 * rollup.
 *
 * <p>Replaces the stream passes that used to run over every {@code report_jobs} row on each request.
 * The counters that must be current to the second — jobs in flight, jobs that failed — are not built
 * here: they are read live from the jobs table and handed in, because a rollup refreshed on a timer
 * would report an in-flight job minutes late.
 */
@Component
@RequiredArgsConstructor
public class AdminRollupTotals {

	/** Days covered by the dashboard's short trend strips. */
	private static final int WEEK_DAYS = 7;

	private final RollupUsageMath math;

	/**
	 * Builds the headline counters.
	 *
	 * @param days       daily rollup rows
	 * @param activeDays distinct (day, user) pairs, from which the all-time user count is taken
	 * @param inFlight   jobs currently queued or running, read live
	 * @param failed     jobs that ended in error, read live
	 * @param today      reference date for the "this month" window
	 * @return the aggregated totals
	 */
	public AdminTotals totals(
			List<UsageDailyBucket> days, List<UsageActiveDay> activeDays,
			int inFlight, int failed, LocalDate today) {
		long reportsTotal = 0;
		long thisMonth = 0;
		for (UsageDailyBucket day : days) {
			if (!math.isCountable(day)) {
				continue;
			}
			long jobs = math.value(day.jobs());
			reportsTotal += jobs;
			if (isSameMonth(day.day(), today)) {
				thisMonth += jobs;
			}
		}
		Set<String> users = new HashSet<>();
		for (UsageActiveDay active : activeDays) {
			users.add(active.ownerUserId());
		}
		return new AdminTotals((int) reportsTotal, (int) thisMonth, users.size(), inFlight, failed);
	}

	/**
	 * Counts reports per type, most reports first.
	 *
	 * @param days daily rollup rows
	 * @return per-type counts
	 */
	public List<AdminTypeStat> byType(List<UsageDailyBucket> days) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (UsageDailyBucket day : days) {
			if (math.isCountable(day)) {
				counts.merge(day.reportTypeCode(), math.value(day.jobs()), Long::sum);
			}
		}
		return counts.entrySet().stream()
				.map(entry -> new AdminTypeStat(entry.getKey(), entry.getValue().intValue()))
				.sorted(Comparator.comparingInt(AdminTypeStat::count).reversed())
				.toList();
	}

	/**
	 * Builds the trailing {@value #WEEK_DAYS} days of report volume, oldest first.
	 *
	 * <p>Every day in the window gets a point, including the ones with no activity: a bar chart with
	 * quiet days missing reads as a shorter week rather than as a quiet one.
	 *
	 * @param days  daily rollup rows
	 * @param today reference date whose value anchors "today"
	 * @return one point per day
	 */
	public List<AdminDayVolume> weekly(List<UsageDailyBucket> days, LocalDate today) {
		Map<LocalDate, Long> counts = new LinkedHashMap<>();
		for (UsageDailyBucket day : days) {
			if (math.isCountable(day)) {
				counts.merge(day.day(), math.value(day.jobs()), Long::sum);
			}
		}
		List<AdminDayVolume> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			series.add(new AdminDayVolume(day, weekdayLabel(day), counts.getOrDefault(day, 0L).intValue()));
		}
		return series;
	}

	/**
	 * Builds the trailing {@value #WEEK_DAYS} days of token spend, oldest first.
	 *
	 * @param days  daily rollup rows
	 * @param today reference date whose value anchors "today"
	 * @return one point per day
	 */
	public List<AdminTokenDay> tokenWeekly(List<UsageDailyBucket> days, LocalDate today) {
		Map<LocalDate, Long> tokens = new LinkedHashMap<>();
		Map<LocalDate, Double> costs = new LinkedHashMap<>();
		for (UsageDailyBucket day : days) {
			tokens.merge(day.day(), math.totalTokens(day), Long::sum);
			costs.merge(day.day(), math.costUsd(day), Double::sum);
		}
		List<AdminTokenDay> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			series.add(new AdminTokenDay(
					day, weekdayLabel(day), tokens.getOrDefault(day, 0L), costs.getOrDefault(day, 0d)));
		}
		return series;
	}

	/**
	 * Short weekday label for a date, e.g. {@code Mon}.
	 *
	 * @param day the date
	 * @return the abbreviated English weekday name
	 */
	String weekdayLabel(LocalDate day) {
		return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
	}

	/**
	 * Tells whether a day falls in the same calendar month as the reference date.
	 *
	 * @param day       the day under test
	 * @param reference the reference date
	 * @return true when both share a year and a month
	 */
	boolean isSameMonth(LocalDate day, LocalDate reference) {
		return day != null && day.getYear() == reference.getYear()
				&& day.getMonth() == reference.getMonth();
	}
}
