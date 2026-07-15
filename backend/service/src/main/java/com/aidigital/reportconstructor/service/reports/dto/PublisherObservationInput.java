package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's context for the publisher-observations batch: the hand-entered "Top Publishers" table
 * Claude must reason over, plus the tactic's name so the copy can talk about the right channel mix.
 *
 * @param tacticNum  the 1-based tactic number, used to route the reply back to the tactic's slide
 * @param tacticName the tactic's display name (e.g. {@code "CTV"}), as it appears on the deck
 * @param rows       the tactic's publisher rows, in sheet order; never padded with empty rows
 */
public record PublisherObservationInput(int tacticNum, String tacticName, List<PublisherRow> rows) {
}
