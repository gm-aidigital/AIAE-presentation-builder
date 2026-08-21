package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One row of a channel slide's METRIC table, as the slide prints it: the metric's name and its five
 * columns. Carried as display strings rather than numbers because the slide's own figures are what the
 * narrative above the table has to be true to — on the two-step flow those are the figures the user
 * reviewed in the workbook, and a narrative reasoning over freshly recomputed numbers can contradict the
 * table it sits on.
 *
 * @param label         the metric's row label ({@code "Impressions"}, {@code "CTR"}, …)
 * @param monthGoal     the reporting month's goal column
 * @param monthActual   the reporting month's actual column
 * @param vsGoal        the pacing column, actual against the month's goal
 * @param eocGoal       the end-of-campaign goal column
 * @param eocProjection the end-of-campaign projection column
 */
public record TacticPacingMetric(
		String label,
		String monthGoal,
		String monthActual,
		String vsGoal,
		String eocGoal,
		String eocProjection
) {
}
