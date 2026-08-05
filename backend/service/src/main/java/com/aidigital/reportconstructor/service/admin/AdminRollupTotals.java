package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.service.admin.dto.AdminTotals;
import com.aidigital.reportconstructor.service.admin.dto.AdminTypeStat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the dashboard's headline counters and its per-type split out of the {@code usage_daily}
 * rollup, for whatever window the caller selected.
 *
 * <p>Replaces the stream passes that used to run over every {@code report_jobs} row on each request.
 * The counters that must be current to the second — jobs in flight, jobs that failed — are not built
 * here: they are read live from the jobs table and handed in, because they are facts about now
 * rather than about the selected window.
 */
@Component
@RequiredArgsConstructor
public class AdminRollupTotals {

	private final RollupUsageMath math;

	/**
	 * Builds the headline counters for the window.
	 *
	 * @param days        daily rollup rows already restricted to the window
	 * @param activeUsers distinct users active in the window
	 * @param newUsers    of those, the ones seen for the first time in it
	 * @param inFlight    jobs currently queued or running, read live
	 * @param failed      jobs that ended in error, read live
	 * @return the aggregated totals
	 */
	public AdminTotals totals(
			List<UsageDailyBucket> days, int activeUsers, int newUsers, int inFlight, int failed) {
		long reports = 0;
		for (UsageDailyBucket day : days) {
			if (math.isCountable(day)) {
				reports += math.value(day.jobs());
			}
		}
		return new AdminTotals((int) reports, activeUsers, newUsers, inFlight, failed);
	}

	/**
	 * Counts reports per type in the window, most reports first.
	 *
	 * @param days daily rollup rows already restricted to the window
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
}
