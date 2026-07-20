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
import java.util.List;
import java.util.Locale;

/**
 * Sums the Claude token counts stamped on report jobs into the dashboard's spend figures.
 *
 * <p>Only jobs that actually carry counts take part — see {@link JobTokenUsage#hasUsage} for why
 * counting the rest as zero-token reports would distort every average.
 */
@Component
@RequiredArgsConstructor
public class AdminTokenAggregator {

	/** Days covered by the token trend series. */
	private static final int WEEK_DAYS = 7;

	private final JobTokenUsage usage;

	/**
	 * Sums every job's token consumption into the token tab's headline figures.
	 *
	 * @param all all report jobs
	 * @param now reference time for the "this month" window
	 * @return the aggregated token totals, all-zero when nothing has been recorded yet
	 */
	public AdminTokenTotals totals(List<ReportJobEntity> all, OffsetDateTime now) {
		int reports = 0;
		long calls = 0;
		long input = 0;
		long output = 0;
		long cacheWrite = 0;
		long cacheRead = 0;
		double cost = 0d;
		long tokensThisMonth = 0;
		double costThisMonth = 0d;
		for (ReportJobEntity job : all) {
			if (!usage.hasUsage(job)) {
				continue;
			}
			reports++;
			calls += usage.calls(job);
			input += usage.inputTokens(job);
			output += usage.outputTokens(job);
			cacheWrite += usage.cacheWriteTokens(job);
			cacheRead += usage.cacheReadTokens(job);
			double jobCost = usage.costUsd(job);
			cost += jobCost;
			if (isSameMonth(job.getCreatedAt(), now)) {
				tokensThisMonth += usage.totalTokens(job);
				costThisMonth += jobCost;
			}
		}
		long total = input + output + cacheWrite + cacheRead;
		return new AdminTokenTotals(
				reports, calls, input, output, cacheWrite, cacheRead, total, cost,
				tokensThisMonth, costThisMonth,
				perReport(total, reports),
				perReport(input + cacheWrite + cacheRead, reports),
				perReport(output, reports),
				reports == 0 ? 0d : cost / reports);
	}

	/**
	 * Builds the last {@value #WEEK_DAYS} days of token spend, oldest first, for the trend bars.
	 *
	 * @param all all report jobs
	 * @param now reference time whose local date anchors "today"
	 * @return one spend point per day for the trailing week
	 */
	public List<AdminTokenDay> weekly(List<ReportJobEntity> all, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();
		List<AdminTokenDay> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			long tokens = 0;
			double cost = 0d;
			for (ReportJobEntity job : all) {
				if (job.getCreatedAt() == null
						|| !day.equals(job.getCreatedAt().toLocalDate())
						|| !usage.hasUsage(job)) {
					continue;
				}
				tokens += usage.totalTokens(job);
				cost += usage.costUsd(job);
			}
			String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			series.add(new AdminTokenDay(day, label, tokens, cost));
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
