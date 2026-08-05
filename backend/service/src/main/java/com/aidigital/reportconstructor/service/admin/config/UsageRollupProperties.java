package com.aidigital.reportconstructor.service.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the {@code usage_daily} rollup that backs the admin dashboard.
 *
 * <p>Binds from {@code app.usage-rollup.*}. The defaults are chosen for a rollup that is a cache,
 * not a ledger: a short trailing window keeps the routine refresh cheap, and a short staleness
 * budget means the dashboard repairs itself on the next request rather than waiting for the timer.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.usage-rollup")
public class UsageRollupProperties {

	/**
	 * How many days back the routine refresh recomputes.
	 *
	 * <p>More than one, because a job created just before midnight can be updated just after it, and
	 * because an admin clearing failures deletes jobs whose day must then be rebuilt without them.
	 */
	private int trailingDays = 3;

	/**
	 * How old the rollup may be before a dashboard request rebuilds the trailing window itself,
	 * in seconds.
	 *
	 * <p>This is what makes a report that finished a minute ago show up: the timer alone would leave
	 * it invisible until its next tick.
	 */
	private int stalenessSeconds = 120;

	/**
	 * How many days of history the dashboard's trend series read.
	 *
	 * <p>Bounds the read regardless of how long the deployment has been running: at 400 days a
	 * month-over-month series still has a full previous year to compare against.
	 */
	private int historyDays = 400;
}
