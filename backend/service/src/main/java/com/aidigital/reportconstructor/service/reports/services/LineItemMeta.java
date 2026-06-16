package com.aidigital.reportconstructor.service.reports.services;

/**
 * Metadata for one BigQuery line item, keyed by the numeric line item ID.
 *
 * @param id      numeric line item ID parsed from the 9th underscore segment of the naming
 * @param naming  the raw "Level 1 Naming" string the ID was extracted from
 * @param channel the BQ Channel value for this line item (used by the unique-ID match rule)
 * @param tactic  the BQ Tactic column value for this line item (empty when absent)
 */
public record LineItemMeta(String id, String naming, String channel, String tactic) {

}
