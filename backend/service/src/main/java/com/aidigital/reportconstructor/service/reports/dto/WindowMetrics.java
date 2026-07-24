package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Campaign totals and per-tactic metrics aggregated from delivery rows within one date window.
 * Produced twice per report when an EOM reporting period is set: once for the full flight window
 * and once more for the narrower period, so plan proration can compare the same shapes.
 *
 * @param totals  campaign-level rollup for the window
 * @param tactics per-tactic metrics for the window, keyed by the tactic's ordinal index
 */
public record WindowMetrics(Totals totals, Map<Integer, Tactic> tactics) {
	// required
}
