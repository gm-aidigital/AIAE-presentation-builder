package com.aidigital.reportconstructor.service.reports.dto;

/**
 * AI-generated audience and timing insight for a single tactic.
 *
 * @param male     percentage of the audience for this tactic that is male
 * @param female   percentage of the audience for this tactic that is female
 * @param weekdays copy describing the tactic's weekday performance or recommendation
 * @param weekends copy describing the tactic's weekend performance or recommendation
 */
public record TacticInsight(int male, int female, String weekdays, String weekends) {
	// required
}
