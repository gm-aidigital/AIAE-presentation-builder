package com.aidigital.reportconstructor.service.reports.services;

import java.util.List;

/**
 * Full result of a match run: the per-tactic suggestions plus the underlying BQ line item data.
 *
 * @param tactics   one suggestion per extracted Media-Plan tactic, in Media-column order
 * @param lineItems all distinct line item metadata, ordered by numeric ID ascending
 * @param uniqueIds the distinct numeric line item IDs, ordered ascending (parallel to {@code lineItems})
 */
public record MatchResult(
		List<TacticSuggestion> tactics,
		List<LineItemMeta> lineItems,
		List<String> uniqueIds
) {

}
