package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
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
 * Breaks the per-call usage events down by the pipeline stage that caused them, for the token tab's
 * "By pipeline stage" panel.
 *
 * <p>This is the one dashboard figure the per-job token stamp cannot produce, so it stays event
 * sourced. The headline totals, per-report averages and weekly trend are derived from the per-job
 * stamps instead (see {@link AdminJobTokenTotals}), so every report's spend reads identically
 * across the "Spend by user", "All reports" and "Failures" views.
 */
@Component
@RequiredArgsConstructor
public class AdminTokenAggregator {

	private final ClaudeCostCalculator costCalculator;

	/**
	 * Tells whether an event carries counts the API itself reported.
	 *
	 * @param event the usage event
	 * @return true when the event is measured rather than estimated
	 */
	public boolean isMeasured(ClaudeUsageEventEntity event) {
		return ClaudeUsageStatus.RECORDED.getCode().equals(event.getStatus());
	}

	/**
	 * Every token one event accounts for.
	 *
	 * @param event the usage event
	 * @return input + output + both cache classes
	 */
	public long totalTokens(ClaudeUsageEventEntity event) {
		return event.getInputTokens() + event.getOutputTokens()
				+ event.getCacheWriteTokens() + event.getCacheReadTokens();
	}

	/**
	 * Estimated cost of one event at the configured list prices for the model it billed against.
	 *
	 * @param event the usage event
	 * @return the cost in USD
	 */
	public double costUsd(ClaudeUsageEventEntity event) {
		return costCalculator.costUsd(
				event.getInputTokens(), event.getOutputTokens(),
				event.getCacheWriteTokens(), event.getCacheReadTokens(), event.getModel());
	}

	/**
	 * Groups measured spend by the pipeline stage that caused it, most expensive first.
	 *
	 * @param events every recorded usage event
	 * @return per-stage spend rows
	 */
	public List<AdminTokenLabel> byLabel(List<ClaudeUsageEventEntity> events) {
		Map<String, long[]> counters = new LinkedHashMap<>();
		Map<String, Double> costs = new LinkedHashMap<>();
		for (ClaudeUsageEventEntity event : events) {
			// [0] measured calls, [1] measured tokens, [2] lost calls
			long[] row = counters.computeIfAbsent(event.getLabel(), k -> new long[3]);
			if (isMeasured(event)) {
				row[0]++;
				row[1] += totalTokens(event);
				costs.merge(event.getLabel(), costUsd(event), Double::sum);
			} else {
				row[2]++;
			}
		}
		List<AdminTokenLabel> rows = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : counters.entrySet()) {
			long[] row = entry.getValue();
			rows.add(new AdminTokenLabel(
					entry.getKey(), row[0], row[1], costs.getOrDefault(entry.getKey(), 0d), row[2]));
		}
		rows.sort(Comparator.comparingLong(AdminTokenLabel::totalTokens).reversed());
		return rows;
	}
}
