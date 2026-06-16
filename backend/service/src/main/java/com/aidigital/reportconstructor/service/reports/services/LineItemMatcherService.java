package com.aidigital.reportconstructor.service.reports.services;

import java.util.List;

/**
 * Matches Media-Plan tactics to BigQuery line item IDs using the unique-ID rule.
 */
public interface LineItemMatcherService {

	/**
	 * Matches Media-Plan tactics to BigQuery line item IDs using the unique-ID rule:
	 * a tactic auto-matches only when its expected BQ Channel holds exactly one line item ID.
	 *
	 * @param bqRows   BigQuery export rows with the header on the first row; the "Level 1 Naming",
	 *                 "Channel" and "Tactic" columns are located by header name (must be non-empty)
	 * @param planRows Media Plan rows from which whitelisted tactic names are extracted (may be null/empty)
	 * @return the tactic suggestions together with all distinct line item metadata and IDs
	 */
	MatchResult match(List<List<String>> bqRows, List<List<String>> planRows);
}
