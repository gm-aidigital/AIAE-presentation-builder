package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.dto.AdminDateRange;
import com.aidigital.reportconstructor.service.admin.dto.AdminStats;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Holds the assembled dashboard snapshot between requests.
 *
 * <p>Invalidation is by cause, not by clock. The snapshot can only go out of date when the
 * {@code usage_daily} rollup behind it is rebuilt, so that rebuild is what drops it — which means
 * the dashboard is never showing figures older than the rollup, and a quiet hour costs no repeated
 * work at all. A time-to-live would have to choose between those two, and would be wrong in one
 * direction or the other.
 *
 * <p>Two admins looking at the same dates therefore share one assembly pass rather than each
 * triggering their own queries and fold. Different dates are different answers, so the window is the
 * cache key — otherwise switching the range would hand back the previous window's figures.
 */
@Component
public class AdminStatsCache {

	/** Name of the cache holding the single dashboard snapshot. */
	public static final String CACHE_NAME = "adminStats";

	/**
	 * Returns the cached snapshot, assembling it on a miss.
	 *
	 * <p>Takes a supplier rather than the stats themselves so a hit never does the work: passing an
	 * already-assembled value would defeat the point of asking.
	 *
	 * @param range    the window being reported on, and the cache key
	 * @param assemble builds the snapshot when the cache has none
	 * @return the snapshot
	 */
	@Cacheable(cacheNames = CACHE_NAME, key = "#range")
	public AdminStats snapshot(AdminDateRange range, Supplier<AdminStats> assemble) {
		return assemble.get();
	}

	/**
	 * Drops the cached snapshot, so the next request reassembles it.
	 */
	@CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
	public void invalidate() {
		// The annotation is the behaviour; nothing to do in the body.
	}
}
