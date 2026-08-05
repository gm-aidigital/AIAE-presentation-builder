package com.aidigital.reportconstructor.domain.reports.projections;

/**
 * One user's aggregated usage for a single (report type, target, model) combination, as read back
 * out of the {@code usage_daily} rollup.
 *
 * <p>The day dimension is summed away entirely. The remaining dimensions are kept for the same
 * reasons as in {@link UsageDailyBucket}: pricing and the countable-report decision both happen on
 * the read side.
 *
 * <p>{@code monthJobs} carries the current-month slice of the same group, so the table's "this
 * month" column costs no extra query and cannot disagree with the all-time column beside it.
 *
 * @param ownerUserId       internal id of the report owner
 * @param reportTypeCode    report type code, uppercased; {@code OTHER} when unknown
 * @param target            generation target; {@code UNKNOWN} when the jobs carried none
 * @param claudeModel       model these jobs billed against; {@code UNKNOWN} when they made no call
 * @param jobs              jobs the user created in this combination
 * @param monthJobs         of those, the ones created in the current calendar month
 * @param jobsWithUsage     of those, the ones carrying recorded token counts
 * @param claudeCalls       Anthropic API calls those jobs made
 * @param inputTokens       plain (uncached) input tokens
 * @param outputTokens      output tokens
 * @param cacheWriteTokens  input tokens written into the prompt cache
 * @param cacheReadTokens   input tokens served from the prompt cache
 * @param slides            slides the user's finished decks shipped with
 * @param jobsWithSlides    of those jobs, the ones that produced a measured deck
 * @param generationSeconds wall-clock seconds those runs took
 */
public record UsageDailyUserRow(
		String ownerUserId,
		String reportTypeCode,
		String target,
		String claudeModel,
		Long jobs,
		Long monthJobs,
		Long jobsWithUsage,
		Long claudeCalls,
		Long inputTokens,
		Long outputTokens,
		Long cacheWriteTokens,
		Long cacheReadTokens,
		Long slides,
		Long jobsWithSlides,
		Long generationSeconds) {
}
