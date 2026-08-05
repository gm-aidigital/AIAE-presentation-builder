package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.config.UsageRollupProperties;
import com.aidigital.reportconstructor.service.reports.usage.UsageDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Keeps the {@code usage_daily} rollup current.
 *
 * <p>Two triggers, because one is not enough on its own. The timer keeps the rollup warm without
 * anyone watching, but on its own it would leave a report that finished a minute ago invisible until
 * the next tick. {@link #ensureFresh()} closes that gap from the read side: a dashboard request that
 * finds the rollup stale rebuilds the trailing window before reading it.
 *
 * <p>Refreshing is never allowed to break the caller. The rollup is a cache over data that is still
 * intact in {@code report_jobs}; a failed rebuild means slightly stale figures, which is a much
 * smaller problem than a dashboard that returns an error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageRollupRefresher {

	/**
	 * How often the timer rebuilds the trailing window, in milliseconds.
	 *
	 * <p>Ten minutes: the rebuild is a single grouped scan of a few days of jobs, so it is cheap, and
	 * anything a viewer actually notices is picked up by {@link #ensureFresh()} long before this.
	 */
	private static final long REFRESH_INTERVAL_MS = 600_000L;

	private final UsageDailyService rollup;
	private final UsageRollupProperties props;
	private final AdminStatsCache statsCache;

	/**
	 * Rebuilds the trailing window on a timer, dropping anything derived from the old rows.
	 */
	@Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
	public void refreshOnSchedule() {
		refreshTrailingWindow();
		statsCache.invalidate();
	}

	/**
	 * Rebuilds the trailing window when the rollup is older than the configured staleness budget, or
	 * rebuilds it in full when the rollup has never been built at all.
	 *
	 * <p>Called before a dashboard read, so the figures a viewer sees are at most
	 * {@link UsageRollupProperties#getStalenessSeconds()} old. The return value is what lets the
	 * caller drop its cached snapshot only when the numbers underneath it actually moved.
	 *
	 * @return true when the rollup was rebuilt, so anything derived from it is now stale
	 */
	public boolean ensureFresh() {
		OffsetDateTime last = lastRefreshedAt();
		if (last == null) {
			// Empty rollup: either the first run after this feature shipped, or a wiped table. Either
			// way the trailing window alone would leave every earlier day missing from the totals.
			rebuildEverything();
			return true;
		}
		if (Duration.between(last, OffsetDateTime.now()).getSeconds() >= props.getStalenessSeconds()) {
			refreshTrailingWindow();
			return true;
		}
		return false;
	}

	/**
	 * Rebuilds the last {@link UsageRollupProperties#getTrailingDays()} days of the rollup.
	 */
	void refreshTrailingWindow() {
		LocalDate today = LocalDate.now();
		try {
			int rows = rollup.rebuild(
					today.minusDays(Math.max(1, props.getTrailingDays())), today.plusDays(1));
			log.debug("[rollup] trailing window rebuilt, {} row(s)", rows);
		} catch (Exception ex) {
			log.warn("[rollup] trailing window rebuild failed: {}", ex.getMessage());
		}
	}

	/**
	 * Rebuilds the whole rollup from the earliest report job.
	 */
	void rebuildEverything() {
		try {
			int rows = rollup.rebuildAll();
			log.info("[rollup] full rebuild wrote {} row(s)", rows);
		} catch (Exception ex) {
			log.warn("[rollup] full rebuild failed: {}", ex.getMessage());
		}
	}

	/**
	 * Reads the rollup's last refresh timestamp, treating a read failure as "never refreshed".
	 *
	 * @return the timestamp, or {@code null} when the rollup is empty or unreadable
	 */
	OffsetDateTime lastRefreshedAt() {
		try {
			return rollup.lastRefreshedAt();
		} catch (Exception ex) {
			log.warn("[rollup] freshness check failed: {}", ex.getMessage());
			return null;
		}
	}
}
