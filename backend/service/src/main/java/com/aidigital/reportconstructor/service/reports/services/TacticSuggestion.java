package com.aidigital.reportconstructor.service.reports.services;

/**
 * A proposed tactic&rarr;line-item mapping produced for one Media-Plan tactic.
 *
 * @param tactic     the tactic name as it appears in the Media Plan (original casing)
 * @param lineItemId the auto-matched line item ID, or empty string when no unique match was found
 * @param confidence {@code "auto"} when a unique line item ID was matched, otherwise {@code "none"}
 */
public record TacticSuggestion(String tactic, String lineItemId, String confidence) {

}
