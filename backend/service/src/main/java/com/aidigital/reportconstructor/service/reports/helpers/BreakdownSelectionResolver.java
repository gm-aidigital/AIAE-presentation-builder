package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reduces the raw per-tactic breakdown selections from the request into a typed
 * {@code tactic number → enabled sections} map shared by the sheet-clear step (Step 1) and the
 * slide-insertion step (Step 2), so both consume the Step-3 toggle state the same way.
 */
public interface BreakdownSelectionResolver {

	/**
	 * Reduces the raw per-tactic breakdown selections to a map of 1-based tactic number to the set of
	 * enabled breakdown sections, dropping unknown/blank section codes and null tactic numbers.
	 *
	 * @param selections the per-tactic breakdown selections from the request, possibly {@code null}
	 * @return tactic number → enabled breakdown sections (empty map when {@code selections} is null)
	 */
	Map<Integer, Set<BreakdownType>> resolve(List<BreakdownSelection> selections);
}
