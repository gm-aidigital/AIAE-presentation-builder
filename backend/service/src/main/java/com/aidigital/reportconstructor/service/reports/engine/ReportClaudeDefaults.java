package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Empty Claude batch payloads used when a batch is skipped or the API returns nothing.
 */
@Component
public class ReportClaudeDefaults {

	/**
	 * Builds a placeholder strategic batch used for previews or when Batch A is skipped.
	 *
	 * @return a {@link ClaudeStrategic} with null narrative fields and an empty strategic-insights list
	 */
	public ClaudeStrategic emptyStrategic() {
		return new ClaudeStrategic(null, null, null, List.of());
	}

	/**
	 * Builds a placeholder tactical batch used when Batch B is skipped or returns nothing.
	 *
	 * @return a {@link ClaudeTactical} whose per-tactic insight map is empty
	 */
	public ClaudeTactical emptyTactical() {
		return new ClaudeTactical(Map.of());
	}

	/**
	 * Builds a placeholder results batch used when Batch C is skipped or returns nothing.
	 *
	 * @return a {@link ClaudeResults} with a null results overview, an empty performance-thoughts list,
	 * an empty per-tactic overviews map, an empty recommendations list, and null frequency-narrative copy
	 */
	public ClaudeResults emptyResults() {
		return new ClaudeResults(null, List.of(), Map.of(), List.of(), null, null, null);
	}
}
