package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;

import java.util.List;

/**
 * Single source of truth for the ordered list of tactics under a media plan's "Media" column.
 *
 * <p>Both the line-item matcher (which numbers the tactics shown in the matching modal) and the
 * campaign data collector (which resolves each tactic slot's line item, KPIs and creative) rely on
 * this extractor so their tactic numbering can never diverge. A divergent numbering would silently
 * drop the manual line-item mapping of any tactic the collector failed to re-discover.
 */
public interface MediaPlanTacticExtractor {

	/**
	 * Extracts whitelisted tactics from the Media column in top-to-bottom order, together with the
	 * context used to disambiguate duplicate tactic names (the most recent section/group label plus
	 * the tactic row's own cells). Section-label rows and non-tactic rows are skipped rather than
	 * treated as a terminator, so grouped plans (product sub-totals between tactic blocks) parse in
	 * full; the list is capped at the number of report tactic slots.
	 *
	 * @param planRows the Media Plan grid (may be {@code null}/empty)
	 * @return the tactics with context, in Media-column order (never {@code null})
	 */
	List<PlanTactic> extract(List<List<String>> planRows);
}
