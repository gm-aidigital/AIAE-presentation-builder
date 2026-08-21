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
 * @param planImps          planned impressions target for the tactic ({@code null} if not planned); EOM Plan
 *                          Units for a CPM-rated tactic
 * @param planCtr           planned click-through-rate target ({@code null} if not planned)
 * @param planVcr           planned video-completion-rate target ({@code null} if not planned)
 * @param planMaxFreq       planned maximum frequency cap for the tactic ({@code null} if not set)
 * @param topCreativeName   name of the best-performing creative for this tactic ({@code null} if none)
 * @param topCreativeImps   impressions delivered by the top creative ({@code null} if none)
 * @param topCreativeClicks clicks recorded by the top creative ({@code null} if none)
 * @param planClicks        EOM Plan Units for a CPC-rated tactic ({@code null} otherwise)
 * @param planViews         EOM Plan Units for a CPV-rated tactic ({@code null} otherwise)
 * @param planWeeklyFreq    planned frequency per week read from the media plan's "Frequency per week"
 *                          column ({@code null} when the column is absent or blank); EOM derives
 *                          {@code {{tactic n f}}} from it
 * @param planReach         planned reach read from the media plan's "Reach" column ({@code null} when the
 *                          column is absent or blank); the campaign reach is summed from these, so a plan
 *                          row the user excluded contributes none
 * @param planFlightSpend   full-flight planned spend from the Estimates tab's "Total Cost" column
 *                          ({@code null} when absent). Kept beside {@code planSpend} because an EOM report
 *                          replaces that field with the reporting month's budget, and the channel slide's
 *                          end-of-campaign column needs the flight figure the plan was booked on
 * @param planFlightImps    full-flight planned impressions from the Estimates tab's "Impressions" column
 *                          ({@code null} when absent); the flight counterpart of {@code planImps}
 * @param planFlightClicks  full-flight planned clicks from the Estimates tab's "Clicks" column
 *                          ({@code null} when the column is absent or blank)
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
		Double topCreativeClicks,
		Double planClicks,
		Double planViews,
		Double planWeeklyFreq,
		Double planReach,
		Double planFlightSpend,
		Double planFlightImps,
		Double planFlightClicks
) {

	/**
	 * Backward-compatible constructor for callers that predate the Estimates tab's full-flight figures;
	 * leaves them {@code null} so their behaviour is unchanged.
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
	 * @param planClicks        EOM Plan Units for a CPC-rated tactic ({@code null} otherwise)
	 * @param planViews         EOM Plan Units for a CPV-rated tactic ({@code null} otherwise)
	 * @param planWeeklyFreq    planned frequency per week ({@code null} when absent)
	 * @param planReach         planned reach from the media plan's "Reach" column ({@code null} when absent)
	 */
	public Tactic(
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
			Double topCreativeClicks,
			Double planClicks,
			Double planViews,
			Double planWeeklyFreq,
			Double planReach
	) {
		this(name, channel, lineItemId, spend, imps, clicks, completions, ctr, vcr, weekdays, weekends, planSpend,
				planImps, planCtr, planVcr, planMaxFreq, topCreativeName, topCreativeImps, topCreativeClicks,
				planClicks, planViews, planWeeklyFreq, planReach, null, null, null);
	}

	/**
	 * Backward-compatible constructor for callers that predate the media plan's Reach column; leaves it
	 * {@code null} so their behaviour is unchanged.
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
	 * @param planClicks        EOM Plan Units for a CPC-rated tactic ({@code null} otherwise)
	 * @param planViews         EOM Plan Units for a CPV-rated tactic ({@code null} otherwise)
	 * @param planWeeklyFreq    planned frequency per week ({@code null} when absent)
	 */
	public Tactic(
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
			Double topCreativeClicks,
			Double planClicks,
			Double planViews,
			Double planWeeklyFreq
	) {
		this(name, channel, lineItemId, spend, imps, clicks, completions, ctr, vcr, weekdays, weekends, planSpend,
				planImps, planCtr, planVcr, planMaxFreq, topCreativeName, topCreativeImps, topCreativeClicks,
				planClicks, planViews, planWeeklyFreq, null);
	}

	/**
	 * Backward-compatible constructor for callers that predate the media plan's weekly-frequency
	 * column; leaves it {@code null} so their behaviour is unchanged.
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
	 * @param planClicks        EOM Plan Units for a CPC-rated tactic ({@code null} otherwise)
	 * @param planViews         EOM Plan Units for a CPV-rated tactic ({@code null} otherwise)
	 */
	public Tactic(
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
			Double topCreativeClicks,
			Double planClicks,
			Double planViews
	) {
		this(name, channel, lineItemId, spend, imps, clicks, completions, ctr, vcr, weekdays, weekends, planSpend,
				planImps, planCtr, planVcr, planMaxFreq, topCreativeName, topCreativeImps, topCreativeClicks,
				planClicks, planViews, null, null);
	}

	/**
	 * Backward-compatible constructor for callers that predate the EOM CPC/CPV Plan Units fields;
	 * leaves them {@code null} so their behaviour is unchanged.
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
	public Tactic(
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
		this(name, channel, lineItemId, spend, imps, clicks, completions, ctr, vcr, weekdays, weekends, planSpend,
				planImps, planCtr, planVcr, planMaxFreq, topCreativeName, topCreativeImps, topCreativeClicks,
				null, null);
	}
}
