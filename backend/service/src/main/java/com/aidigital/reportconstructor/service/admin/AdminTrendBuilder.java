package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenPeriod;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds daily rollup rows into week or month buckets and attaches each bucket's comparison with the
 * one before it.
 *
 * <p>The comparison is made against the calendar predecessor, not against the previous element of
 * the series. Those are different whenever a week or month saw no activity at all: such a bucket has
 * no row to appear as, and comparing February against December because January is missing would
 * quietly overstate every delta after a quiet spell. Looking the predecessor up by its key returns
 * nothing in that case, and the delta is reported as absent rather than wrong.
 */
@Component
@RequiredArgsConstructor
public class AdminTrendBuilder {

	private final AdminPeriodBucketer bucketer;
	private final RollupUsageMath math;

	/**
	 * Builds a trend series at the given granularity, oldest first.
	 *
	 * @param days daily rollup rows, in any order
	 * @param unit bucket granularity
	 * @return one row per bucket that saw activity, oldest first
	 */
	public List<AdminTokenPeriod> build(List<UsageDailyBucket> days, AdminPeriodUnit unit) {
		Map<String, long[]> counters = new LinkedHashMap<>();
		Map<String, Double> costs = new LinkedHashMap<>();
		Map<String, LocalDate> starts = new LinkedHashMap<>();

		for (UsageDailyBucket day : days) {
			String key = bucketer.keyOf(day.day(), unit);
			starts.putIfAbsent(key, bucketer.startOf(day.day(), unit));
			// [0] reports, [1] input, [2] output, [3] cache, [4] total
			long[] row = counters.computeIfAbsent(key, k -> new long[5]);
			if (math.isCountable(day)) {
				row[0] += math.value(day.jobs());
			}
			row[1] += math.value(day.inputTokens());
			row[2] += math.value(day.outputTokens());
			row[3] += math.cacheTokens(day);
			row[4] += math.totalTokens(day);
			costs.merge(key, math.costUsd(day), Double::sum);
		}

		List<String> ordered = new ArrayList<>(starts.keySet());
		ordered.sort((a, b) -> starts.get(a).compareTo(starts.get(b)));

		List<AdminTokenPeriod> series = new ArrayList<>();
		for (String key : ordered) {
			LocalDate start = starts.get(key);
			long[] row = counters.get(key);
			double cost = costs.getOrDefault(key, 0d);
			String previousKey = bucketer.keyOf(bucketer.previousStart(start, unit), unit);
			long[] previous = counters.get(previousKey);
			Double previousCost = costs.get(previousKey);
			series.add(new AdminTokenPeriod(
					key,
					start,
					bucketer.labelOf(start, unit),
					(int) row[0],
					row[1],
					row[2],
					row[3],
					row[4],
					cost,
					previous == null ? null : previous[4],
					previous == null ? null : math.deltaPct(row[4], previous[4]),
					previous == null ? null : (int) previous[0],
					previous == null ? null : math.deltaPct(row[0], previous[0]),
					previousCost,
					previousCost == null ? null : math.deltaPct(cost, previousCost)));
		}
		return series;
	}
}
