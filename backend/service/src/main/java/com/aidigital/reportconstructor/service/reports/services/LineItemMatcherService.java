package com.aidigital.reportconstructor.service.reports.services;

import java.util.List;

/**
 * Matches Media-Plan tactics to BigQuery line item IDs using the unique-ID rule.
 */
public interface LineItemMatcherService {

    /**
     * Metadata for one BigQuery line item, keyed by the numeric line item ID.
     *
     * @param id      numeric line item ID parsed from the 9th underscore segment of the naming
     * @param naming  the raw "Level 1 Naming" string the ID was extracted from
     * @param channel the BQ Channel value for this line item (used by the unique-ID match rule)
     * @param tactic  the BQ Tactic column value for this line item (empty when absent)
     */
    record LineItemMeta(String id, String naming, String channel, String tactic) { }

    /**
     * A proposed tactic&rarr;line-item mapping produced for one Media-Plan tactic.
     *
     * @param tactic     the tactic name as it appears in the Media Plan (original casing)
     * @param lineItemId the auto-matched line item ID, or empty string when no unique match was found
     * @param confidence {@code "auto"} when a unique line item ID was matched, otherwise {@code "none"}
     */
    record TacticSuggestion(String tactic, String lineItemId, String confidence) { }

    /**
     * Full result of a match run: the per-tactic suggestions plus the underlying BQ line item data.
     *
     * @param tactics   one suggestion per extracted Media-Plan tactic, in Media-column order
     * @param lineItems all distinct line item metadata, ordered by numeric ID ascending
     * @param uniqueIds the distinct numeric line item IDs, ordered ascending (parallel to {@code lineItems})
     */
    record MatchResult(
        List<TacticSuggestion> tactics,
        List<LineItemMeta> lineItems,
        List<String> uniqueIds
    ) { }

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
