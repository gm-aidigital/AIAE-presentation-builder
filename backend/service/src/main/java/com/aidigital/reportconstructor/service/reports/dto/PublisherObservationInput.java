package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's context for the publisher-observations batch: the hand-entered "Top Publishers" table
 * Claude must reason over, plus the tactic's name so the copy can talk about the right channel mix.
 *
 * <p>The two impression totals replace a vague licence to extrapolate past the table with a figure the copy
 * can cite: what the listed rows carry against the tactic's whole delivery. Both are {@code 0} when the sheet
 * held no parseable number, and the prompt then omits the coverage line rather than quoting a share it cannot
 * stand behind.
 *
 * @param tacticNum         the 1-based tactic number, used to route the reply back to the tactic's slide
 * @param tacticName        the tactic's display name (e.g. {@code "CTV"}), as it appears on the deck
 * @param rows              the tactic's publisher rows, in sheet order; never padded with empty rows
 * @param headImpressions   impressions {@code rows} carry together, or {@code 0} when unknown
 * @param tacticImpressions the tactic's total delivered impressions, or {@code 0} when unknown
 */
public record PublisherObservationInput(
		int tacticNum, String tacticName, List<PublisherRow> rows,
		long headImpressions, long tacticImpressions) {
}
