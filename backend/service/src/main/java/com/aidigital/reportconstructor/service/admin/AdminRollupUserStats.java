package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.admin.dto.AdminUserStat;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the "By user" table from the {@code usage_daily} rollup.
 *
 * <p>The rollup keys on the internal owner id, which is stable; the email is not, so it is joined on
 * from a one-row-per-user query rather than stored in the rollup. That split is the reason a user
 * who changed address still shows one row here instead of two.
 *
 * <p>As elsewhere, report counts exclude a slide-deck flow's intermediate sheet step while token
 * sums keep every job — so a user's real spend shows even though the sheet step does not add to
 * their report tally.
 *
 * <p>The rows cover the selected window; "last activity" is the exception and is always the user's
 * genuine last run, because a date clamped to the window would be a different fact wearing the same
 * label.
 */
@Component
@RequiredArgsConstructor
public class AdminRollupUserStats {

	private final RollupUsageMath math;
	private final DisplayNameHelper displayNameHelper;

	/**
	 * Assembles per-user rows, biggest spenders first.
	 *
	 * @param rows   per-user rollup rows
	 * @param owners one row per owner carrying the email last recorded for it
	 * @return the per-user activity and spend rows
	 */
	public List<AdminUserStat> build(List<UsageDailyUserRow> rows, List<JobOwner> owners) {
		Map<String, JobOwner> ownersById = new LinkedHashMap<>();
		for (JobOwner owner : owners) {
			ownersById.put(owner.ownerUserId(), owner);
		}

		Map<String, long[]> counters = new LinkedHashMap<>();
		Map<String, Double> costs = new LinkedHashMap<>();

		for (UsageDailyUserRow row : rows) {
			String userId = row.ownerUserId();
			// [0] reports, [1] slides, [2] input, [3] output, [4] cache
			long[] counter = counters.computeIfAbsent(userId, k -> new long[5]);
			if (math.isCountable(row)) {
				counter[0] += math.value(row.jobs());
				counter[1] += math.value(row.slides());
			}
			counter[2] += math.value(row.inputTokens());
			counter[3] += math.value(row.outputTokens());
			counter[4] += math.cacheTokens(row);
			costs.merge(userId, math.costUsd(row), Double::sum);
		}

		List<AdminUserStat> stats = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : counters.entrySet()) {
			String userId = entry.getKey();
			long[] counter = entry.getValue();
			JobOwner owner = ownersById.get(userId);
			String email = owner == null ? null : owner.ownerEmail();
			OffsetDateTime lastActivity = owner == null ? null : owner.lastActivity();
			stats.add(new AdminUserStat(
					userId,
					email,
					displayNameHelper.fromEmail(email),
					(int) counter[0],
					counter[1],
					// From the jobs table, not the rollup: the rollup's grain is a day and would only ever
					// be able to say "that Tuesday".
					lastActivity == null ? null : lastActivity.toLocalDateTime(),
					counter[2],
					counter[3],
					counter[4],
					counter[2] + counter[3] + counter[4],
					costs.getOrDefault(userId, 0d)));
		}
		stats.sort(Comparator.comparingInt(AdminUserStat::total).reversed());
		return stats;
	}
}
