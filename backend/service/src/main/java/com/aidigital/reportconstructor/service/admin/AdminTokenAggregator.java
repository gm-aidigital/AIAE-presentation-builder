package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Breaks Claude spend down by the pipeline stage that caused it, for the token tab's "By pipeline
 * stage" panel.
 *
 * <p>This is the one dashboard figure the per-job token stamp cannot produce, so it stays event
 * sourced. The headline totals, per-report averages and every trend are derived from the per-job
 * stamps by way of the {@code usage_daily} rollup (see {@link AdminRollupTokenTotals}), so a report's
 * spend reads identically across the "Spend by user", "All reports" and "Failures" views.
 *
 * <p>The grouping itself now happens in the database. The event table grows by a few dozen rows per
 * report, and reading it whole to add it up here was the fastest-growing full-table read the
 * dashboard had; what arrives is already one row per (stage, status, model).
 */
@Component
@RequiredArgsConstructor
public class AdminTokenAggregator {

	private final ClaudeCostCalculator costCalculator;

	/**
	 * Tells whether an aggregated row carries counts the API itself reported.
	 *
	 * @param row the aggregated usage row
	 * @return true when the row is measured rather than estimated
	 */
	public boolean isMeasured(ClaudeLabelUsage row) {
		return ClaudeUsageStatus.RECORDED.getCode().equals(row.status());
	}

	/**
	 * Every token an aggregated row accounts for.
	 *
	 * @param row the aggregated usage row
	 * @return input + output + both cache classes
	 */
	public long totalTokens(ClaudeLabelUsage row) {
		return value(row.inputTokens()) + value(row.outputTokens())
				+ value(row.cacheWriteTokens()) + value(row.cacheReadTokens());
	}

	/**
	 * Estimated cost of an aggregated row at the configured list prices for the model it names.
	 *
	 * @param row the aggregated usage row
	 * @return the cost in USD
	 */
	public double costUsd(ClaudeLabelUsage row) {
		return costCalculator.costUsd(
				value(row.inputTokens()), value(row.outputTokens()),
				value(row.cacheWriteTokens()), value(row.cacheReadTokens()), row.model());
	}

	/**
	 * Groups measured spend by the pipeline stage that caused it, most expensive first.
	 *
	 * <p>Estimated rows contribute only their call count, to the {@code unknownCalls} column: a call
	 * whose reply never arrived was billed, so it must stay visible, but its tokens are a guess and
	 * are kept out of the measured columns beside them.
	 *
	 * @param rows aggregated usage rows, one per (stage, status, model)
	 * @return per-stage spend rows
	 */
	public List<AdminTokenLabel> byLabel(List<ClaudeLabelUsage> rows) {
		Map<String, long[]> counters = new LinkedHashMap<>();
		Map<String, Double> costs = new LinkedHashMap<>();
		for (ClaudeLabelUsage row : rows) {
			// [0] measured calls, [1] measured tokens, [2] lost calls
			long[] counter = counters.computeIfAbsent(row.label(), k -> new long[3]);
			if (isMeasured(row)) {
				counter[0] += value(row.calls());
				counter[1] += totalTokens(row);
				costs.merge(row.label(), costUsd(row), Double::sum);
			} else {
				counter[2] += value(row.calls());
			}
		}
		List<AdminTokenLabel> labels = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : counters.entrySet()) {
			long[] counter = entry.getValue();
			labels.add(new AdminTokenLabel(
					entry.getKey(), counter[0], counter[1], costs.getOrDefault(entry.getKey(), 0d), counter[2]));
		}
		labels.sort(Comparator.comparingLong(AdminTokenLabel::totalTokens).reversed());
		return labels;
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
