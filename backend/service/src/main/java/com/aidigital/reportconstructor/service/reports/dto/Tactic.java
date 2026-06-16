package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Per-tactic delivery metrics paired with their planned targets and top-creative breakdown.
 *
 * @param name              human-readable tactic name as shown in the report
 * @param channel           media channel/format the tactic ran on (e.g. display, video)
 * @param lineItemId        identifier of the source line item this tactic maps to
 * @param spend             actual media spend for this tactic
 * @param imps              actual impressions delivered by this tactic
 * @param clicks            actual clicks recorded for this tactic
 * @param completions       actual video/ad completions recorded for this tactic
 * @param ctr               actual click-through rate ({@code null} when not computable)
 * @param vcr               actual video completion rate ({@code null} when not computable)
 * @param weekdays          count of weekday days the tactic was active ({@code null} if unknown)
 * @param weekends          count of weekend days the tactic was active ({@code null} if unknown)
 * @param planSpend         planned/budgeted spend target for the tactic ({@code null} if not planned)
 * @param planImps          planned impressions target for the tactic ({@code null} if not planned)
 * @param planCtr           planned click-through-rate target ({@code null} if not planned)
 * @param planVcr           planned video-completion-rate target ({@code null} if not planned)
 * @param planMaxFreq       planned maximum frequency cap for the tactic ({@code null} if not set)
 * @param topCreativeName   name of the best-performing creative for this tactic ({@code null} if none)
 * @param topCreativeImps   impressions delivered by the top creative ({@code null} if none)
 * @param topCreativeClicks clicks recorded by the top creative ({@code null} if none)
 */
public record Tactic(
		String name,
		String channel,
		String lineItemId,
		double spend,
		double imps,
		double clicks,
		double completions,
		Double ctr,
		Double vcr,
		Integer weekdays,
		Integer weekends,
		Double planSpend,
		Double planImps,
		Double planCtr,
		Double planVcr,
		Double planMaxFreq,
		String topCreativeName,
		Double topCreativeImps,
		Double topCreativeClicks
) {
	// required
}
