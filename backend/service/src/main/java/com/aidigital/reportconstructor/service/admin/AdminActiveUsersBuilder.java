package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminActiveUsersPeriod;
import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts distinct active users per week or month, with the change against the previous bucket.
 *
 * <p>Built from (day, user) pairs rather than from a pre-counted per-month number, because active
 * users do not add up: the same person active in two months is one active user in each, and summing
 * a monthly count into a quarter would double-count them. Holding the identities lets every
 * granularity be answered exactly from one read.
 *
 * <p>{@code newUsers} is the growth signal the raw count hides — a flat month-over-month total can
 * be the same people returning or an equal number arriving and leaving, and only the first-seen
 * count tells those apart. A user counts as new in the first bucket they ever appear in, which means
 * the earliest bucket in the window reports everyone as new; the window is deliberately much longer
 * than the series shown, so that artefact stays off the chart.
 */
@Component
@RequiredArgsConstructor
public class AdminActiveUsersBuilder {

	private final AdminPeriodBucketer bucketer;
	private final RollupUsageMath math;

	/**
	 * Counts the distinct users active on or after a day.
	 *
	 * @param activeDays distinct (day, user) pairs
	 * @param from       first day of the window, inclusive
	 * @return how many distinct users were active in it
	 */
	public int activeInWindow(List<UsageActiveDay> activeDays, LocalDate from) {
		Set<String> users = new LinkedHashSet<>();
		for (UsageActiveDay active : activeDays) {
			if (!active.day().isBefore(from)) {
				users.add(active.ownerUserId());
			}
		}
		return users.size();
	}

	/**
	 * Counts the users whose first ever activity falls inside the window.
	 *
	 * <p>Needs the pairs from before the window as well as inside it — that is the whole point. Someone
	 * who has been generating reports for a year is active this month, not new this month, and only
	 * the earlier history can tell those apart.
	 *
	 * @param activeDays distinct (day, user) pairs, including history before the window
	 * @param from       first day of the window, inclusive
	 * @return how many of the window's users had never appeared before it
	 */
	public int newInWindow(List<UsageActiveDay> activeDays, LocalDate from) {
		Set<String> before = new HashSet<>();
		Set<String> during = new LinkedHashSet<>();
		for (UsageActiveDay active : activeDays) {
			if (active.day().isBefore(from)) {
				before.add(active.ownerUserId());
			} else {
				during.add(active.ownerUserId());
			}
		}
		return (int) during.stream().filter(user -> !before.contains(user)).count();
	}

	/**
	 * Builds an active-user series at the given granularity, oldest first.
	 *
	 * @param activeDays distinct (day, user) pairs, in any order
	 * @param unit       bucket granularity
	 * @return one row per bucket that saw activity, oldest first
	 */
	public List<AdminActiveUsersPeriod> build(List<UsageActiveDay> activeDays, AdminPeriodUnit unit) {
		Map<String, Set<String>> usersByKey = new LinkedHashMap<>();
		Map<String, LocalDate> starts = new LinkedHashMap<>();
		for (UsageActiveDay active : activeDays) {
			String key = bucketer.keyOf(active.day(), unit);
			starts.putIfAbsent(key, bucketer.startOf(active.day(), unit));
			usersByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(active.ownerUserId());
		}

		List<String> ordered = new ArrayList<>(starts.keySet());
		ordered.sort((a, b) -> starts.get(a).compareTo(starts.get(b)));

		Set<String> seenBefore = new HashSet<>();
		List<AdminActiveUsersPeriod> series = new ArrayList<>();
		for (String key : ordered) {
			LocalDate start = starts.get(key);
			Set<String> users = usersByKey.get(key);
			int newUsers = (int) users.stream().filter(user -> !seenBefore.contains(user)).count();
			seenBefore.addAll(users);

			// Against the calendar predecessor, not the previous element: a bucket in which nobody was
			// active has no row, and comparing across that gap would overstate the recovery.
			Set<String> previous = usersByKey.get(bucketer.keyOf(bucketer.previousStart(start, unit), unit));
			series.add(new AdminActiveUsersPeriod(
					key,
					start,
					bucketer.labelOf(start, unit),
					users.size(),
					newUsers,
					previous == null ? null : previous.size(),
					previous == null ? null : math.deltaPct(users.size(), previous.size())));
		}
		return series;
	}
}
