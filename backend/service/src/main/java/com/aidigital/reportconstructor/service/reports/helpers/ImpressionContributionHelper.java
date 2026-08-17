package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.Map;

/**
 * Fills the tactic slide's "Weighted impression contribution" legend: each tactic's share of the
 * campaign's impressions, and the remainder attributed to every other tactic.
 *
 * <p>Derived from the resolved placeholder values rather than from the parsed campaign data, and derived
 * last: on the "Slides from Sheet" flow the impressions the deck prints are the ones the user reviewed in
 * the workbook, so reading the same tokens the deck will print is what keeps the legend agreeing with both
 * the impressions above it and the pie chart beside it.
 */
public interface ImpressionContributionHelper {

	/**
	 * Writes {@code {{tactic N contr}}} and {@code {{tactic N other contr}}} for tactics
	 * {@code 1..tacticCount} into the given map.
	 *
	 * <p>A token that already carries a value is left alone, so a figure the user entered in the workbook
	 * keeps winning over the computed one. A tactic whose impressions — or the campaign total — cannot be
	 * read renders as an em dash rather than a misleading {@code 0.0%}.
	 *
	 * @param flatReplacements resolved token → value map, mutated in place
	 * @param tacticCount      number of active tactics
	 */
	void fillContributions(Map<String, String> flatReplacements, int tacticCount);
}
