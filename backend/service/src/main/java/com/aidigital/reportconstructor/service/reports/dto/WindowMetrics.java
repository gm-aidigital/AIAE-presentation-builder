package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Campaign totals and per-tactic metrics aggregated from delivery rows within the flight window.
 *
 * @param totals  campaign-level rollup for the window
 * @param tactics per-tactic metrics for the window, keyed by the tactic's ordinal index
 */
public record WindowMetrics(Totals totals, Map<Integer, Tactic> tactics) {
	// required
}
