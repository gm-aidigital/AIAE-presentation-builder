package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the token-tab headline figures and weekly trend from the per-job token counts stamped on
 * {@code report_jobs}, the same source the "Spend by user", "All reports" and "Failures" views
 * read. Deriving every headline number from that one source is what keeps the dashboard's token
 * figures matched: a report's spend reads identically wherever it is shown.
 *
 * <p>The per-job stamp is a rollup, so the measured/estimated split and the unattributed-call
 * figures the event-level {@link AdminTokenAggregator} reports have no equivalent here and stay
 * zero. Those refinements survive only in the event-sourced "By pipeline stage" breakdown.
 */
@Component
@RequiredArgsConstructor
public class AdminJobTokenTotals {

	/** Days covered by the token trend series. */
	private static final int WEEK_DAYS = 7;

	private final JobTokenUsage tokenUsage;
	private final ReportCountPolicy reportCountPolicy;

	/**
	 * Sums the per-job stamps into the token tab's headline figures.
	 *
	 * <p>Every job that carries usage contributes its spend to the totals, including a slide-deck
	 * flow's intermediate sheet step, so one two-step run's sheet-step and deck-step tokens both
	 * count. The report denominator, however, counts only standalone reports, so that same run
	 * averages as a single report rather than two.
	 *
	 * @param jobs every report job
	 * @param now  reference time for the "this month" window
	 * @return the aggregated token totals, all-zero when no job carries usage
	 */
	public AdminTokenTotals totals(List<ReportJobEntity> jobs, OffsetDateTime now) {
		long input = 0;
		long output = 0;
		long cacheWrite = 0;
		long cacheRead = 0;
		long calls = 0;
		long tokensThisMonth = 0;
		double cost = 0d;
		double costThisMonth = 0d;
		int reports = 0;

		for (ReportJobEntity job : jobs) {
			if (!tokenUsage.hasUsage(job)) {
				continue;
			}
			input += tokenUsage.inputTokens(job);
			output += tokenUsage.outputTokens(job);
			cacheWrite += tokenUsage.cacheWriteTokens(job);
			cacheRead += tokenUsage.cacheReadTokens(job);
			calls += tokenUsage.calls(job);
			double jobCost = tokenUsage.costUsd(job);
			cost += jobCost;
			if (isSameMonth(job.getCreatedAt(), now)) {
				tokensThisMonth += tokenUsage.totalTokens(job);
				costThisMonth += jobCost;
			}
			if (reportCountPolicy.isCountableReport(job)) {
				reports++;
			}
		}

		long total = input + output + cacheWrite + cacheRead;
		long allInput = input + cacheWrite + cacheRead;
		return new AdminTokenTotals(
				reports, calls, input, output, cacheWrite, cacheRead, total, cost,
				tokensThisMonth, costThisMonth,
				perReport(total, reports), perReport(allInput, reports), perReport(output, reports),
				reports == 0 ? 0d : cost / reports,
				0L, 0L, 0d,
				0L, 0L, 0d);
	}

	/**
	 * Builds the last {@value #WEEK_DAYS} days of token spend from the per-job stamps, oldest first.
	 *
	 * @param jobs every report job
	 * @param now  reference time whose local date anchors "today"
	 * @return one spend point per day for the trailing week
	 */
	public List<AdminTokenDay> weekly(List<ReportJobEntity> jobs, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();
		Map<LocalDate, long[]> tokensByDay = new LinkedHashMap<>();
		Map<LocalDate, Double> costByDay = new LinkedHashMap<>();
		for (ReportJobEntity job : jobs) {
			if (!tokenUsage.hasUsage(job) || job.getCreatedAt() == null) {
				continue;
			}
			LocalDate day = job.getCreatedAt().toLocalDate();
			tokensByDay.computeIfAbsent(day, k -> new long[1])[0] += tokenUsage.totalTokens(job);
			costByDay.merge(day, tokenUsage.costUsd(job), Double::sum);
		}
		List<AdminTokenDay> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			long[] tokens = tokensByDay.get(day);
			String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			series.add(new AdminTokenDay(
					day, label, tokens == null ? 0L : tokens[0], costByDay.getOrDefault(day, 0d)));
		}
		return series;
	}

	/**
	 * Divides a total by the number of reports it came from, guarding the empty case.
	 *
	 * @param total   the summed value
	 * @param reports reports the sum covers
	 * @return the mean, rounded down, or 0 when there are no reports
	 */
	long perReport(long total, int reports) {
		return reports == 0 ? 0L : total / reports;
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
