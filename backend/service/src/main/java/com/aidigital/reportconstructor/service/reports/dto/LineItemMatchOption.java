package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A candidate BigQuery line item the AI matcher may assign to a tactic.
 *
 * @param id      the numeric line item ID
 * @param channel the BigQuery Channel value for this line item (assignments never cross channels)
 * @param naming  the raw "Level 1 Naming" string, whose tail (audience/strategy tokens) distinguishes
 *                line items sharing a channel
 */
public record LineItemMatchOption(String id, String channel, String naming) {

}
