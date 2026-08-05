package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDate;

/**
 * The window a dashboard payload actually covers, echoed back to the client.
 *
 * <p>Sent because what was asked for and what was answered can differ: an absent end is filled in, a
 * reversed pair is swapped, and a start earlier than the retained history is pulled forward. A screen
 * that showed one window's figures under another window's label would be worse than one that showed
 * nothing, so the label comes from the server that did the arithmetic.
 *
 * @param from            first day covered, inclusive
 * @param to              last day covered, inclusive
 * @param suggestedUnit   wire code of the trend granularity this span reads best at: week or month
 */
public record AdminRangeView(LocalDate from, LocalDate to, String suggestedUnit) {
}
