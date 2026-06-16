package com.aidigital.reportconstructor.service.reports.engine;

import java.util.LinkedHashMap;

/**
 * Pivot result for one tactic.
 *
 * @param data           insertion-ordered (chronological) map of label &rarr; {@code {imps, clicks, completions}}
 * @param hasClicks      {@code true} when any row carried clicks (display/social &rarr; CTR line series)
 * @param hasCompletions {@code true} when any row carried completions (video/CTV &rarr; VCR line series)
 */
public record Pivot(LinkedHashMap<String, double[]> data, boolean hasClicks, boolean hasCompletions) {

	/**
	 * @return {@code true} when no rows were pivoted (no chart should be written).
	 */
	public boolean isEmpty() {
		return data.isEmpty();
	}
}
