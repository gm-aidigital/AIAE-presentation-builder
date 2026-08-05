package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the token tab's headline figures from the {@code usage_daily} rollup, plus the two things
 * the rollup structurally cannot see.
 *
 * <p>The rollup sums the per-job token stamps, which is what keeps a report's spend reading
 * identically here, in "Spend by user", in "All reports" and in "Failures". But a stamp lives on a
 * job, and two kinds of billed call have no job to live on: a call made outside any report — the
 * line-item match runs in a web request — and a call whose reply never arrived, which was billed
 * with no usage block to record. Both are read from the per-call event table and reported
 * separately, so the measured total stays measured and the estimate stands beside it rather than
 * being folded into it.
 */
@Component
@RequiredArgsConstructor
public class AdminRollupTokenTotals {

	private final RollupUsageMath math;
	private final ClaudeCostCalculator costCalculator;

	/**
	 * Builds the team-wide token figures.
	 *
	 * @param days         daily rollup rows
	 * @param unattributed per-call aggregates for calls belonging to no report job
	 * @param byLabel      per-call aggregates for every stage, from which lost calls are counted
	 * @return the aggregated token totals
	 */
	public AdminTokenTotals totals(
			List<UsageDailyBucket> days, List<ClaudeLabelUsage> unattributed,
			List<ClaudeLabelUsage> byLabel) {
		long reportsWithUsage = 0;
		long calls = 0;
		long input = 0;
		long output = 0;
		long cacheWrite = 0;
		long cacheRead = 0;
		double cost = 0d;

		for (UsageDailyBucket day : days) {
			// Every job that carries usage contributes its spend, including a slide-deck flow's
			// intermediate sheet step; only the report denominator below excludes it, so one two-step run
			// averages as a single report while both its steps' tokens count.
			if (math.isCountable(day)) {
				reportsWithUsage += math.value(day.jobsWithUsage());
			}
			calls += math.value(day.claudeCalls());
			input += math.value(day.inputTokens());
			output += math.value(day.outputTokens());
			cacheWrite += math.value(day.cacheWriteTokens());
			cacheRead += math.value(day.cacheReadTokens());
			cost += math.costUsd(day);
		}

		// Calls with no job are invisible to the rollup, so they are added into the headline rather than
		// merely reported alongside it — otherwise the "total" would exclude money that was spent.
		long unattributedCalls = 0;
		long unattributedTokens = 0;
		double unattributedCost = 0d;
		for (ClaudeLabelUsage row : unattributed) {
			if (!isMeasured(row)) {
				continue;
			}
			unattributedCalls += value(row.calls());
			unattributedTokens += totalTokens(row);
			unattributedCost += costUsd(row);
			calls += value(row.calls());
			input += value(row.inputTokens());
			output += value(row.outputTokens());
			cacheWrite += value(row.cacheWriteTokens());
			cacheRead += value(row.cacheReadTokens());
			cost += costUsd(row);
		}

		long unknownCalls = 0;
		long estimatedTokens = 0;
		double estimatedCost = 0d;
		for (ClaudeLabelUsage row : byLabel) {
			if (isMeasured(row)) {
				continue;
			}
			unknownCalls += value(row.calls());
			estimatedTokens += totalTokens(row);
			estimatedCost += costUsd(row);
		}

		long total = input + output + cacheWrite + cacheRead;
		return new AdminTokenTotals(
				(int) reportsWithUsage,
				calls,
				input,
				output,
				cacheWrite,
				cacheRead,
				total,
				cost,
				perReport(total, reportsWithUsage),
				perReport(input + cacheWrite + cacheRead, reportsWithUsage),
				perReport(output, reportsWithUsage),
				reportsWithUsage == 0 ? 0d : cost / reportsWithUsage,
				unknownCalls,
				estimatedTokens,
				estimatedCost,
				unattributedCalls,
				unattributedTokens,
				unattributedCost);
	}

	/**
	 * Tells whether an aggregated event row carries counts the API itself reported.
	 *
	 * @param row the aggregated event row
	 * @return true when the row is measured rather than estimated
	 */
	boolean isMeasured(ClaudeLabelUsage row) {
		return ClaudeUsageStatus.RECORDED.getCode().equals(row.status());
	}

	/**
	 * Every token an aggregated event row accounts for.
	 *
	 * @param row the aggregated event row
	 * @return input, output and both cache classes, summed
	 */
	long totalTokens(ClaudeLabelUsage row) {
		return value(row.inputTokens()) + value(row.outputTokens())
				+ value(row.cacheWriteTokens()) + value(row.cacheReadTokens());
	}

	/**
	 * Estimated cost of an aggregated event row at the configured list prices for its model.
	 *
	 * @param row the aggregated event row
	 * @return the cost in USD
	 */
	double costUsd(ClaudeLabelUsage row) {
		return costCalculator.costUsd(
				value(row.inputTokens()), value(row.outputTokens()),
				value(row.cacheWriteTokens()), value(row.cacheReadTokens()), row.model());
	}

	/**
	 * Mean value per report, floored at zero reports.
	 *
	 * @param total   the summed value
	 * @param reports the denominator
	 * @return the mean, or 0 when there are no reports to divide by
	 */
	long perReport(long total, long reports) {
		return reports == 0 ? 0L : total / reports;
	}

	/**
	 * Reads a nullable aggregate as a number.
	 *
	 * @param aggregate the value from the projection
	 * @return the value, or 0 when absent
	 */
	long value(Long aggregate) {
		return aggregate == null ? 0L : aggregate;
	}
}
