package com.aidigital.reportconstructor.domain.reports.projections;

import java.time.LocalDate;

/**
 * One day of aggregated usage for a single (report type, target, model) combination, as read back
 * out of the {@code usage_daily} rollup.
 *
 * <p>The user dimension is summed away — this is the shape the trend series are built from, and a
 * series does not need to know who generated what. The other three dimensions survive on purpose:
 * {@code claudeModel} is what lets cost be priced at read time, and {@code target} together with
 * {@code reportTypeCode} is what lets the read side decide whether these jobs count as reports,
 * rather than freezing that policy into the stored numbers.
 *
 * @param day               calendar day the summarised jobs were created on
 * @param reportTypeCode    report type code, uppercased; {@code OTHER} when unknown
 * @param target            generation target; {@code UNKNOWN} when the jobs carried none
 * @param claudeModel       model these jobs billed against; {@code UNKNOWN} when they made no call
 * @param jobs              jobs created that day in this combination
 * @param jobsWithUsage     of those, the ones carrying recorded token counts
 * @param failedJobs        of those, the ones that ended in error
 * @param claudeCalls       Anthropic API calls those jobs made
 * @param inputTokens       plain (uncached) input tokens
 * @param outputTokens      output tokens
 * @param cacheWriteTokens  input tokens written into the prompt cache
 * @param cacheReadTokens   input tokens served from the prompt cache
 * @param slides            slides the finished decks shipped with
 * @param jobsWithSlides    of those jobs, the ones that produced a measured deck
 * @param generationSeconds wall-clock seconds those runs took
 */
public record UsageDailyBucket(
		LocalDate day,
		String reportTypeCode,
		String target,
		String claudeModel,
		Long jobs,
		Long jobsWithUsage,
		Long failedJobs,
		Long claudeCalls,
		Long inputTokens,
		Long outputTokens,
		Long cacheWriteTokens,
		Long cacheReadTokens,
		Long slides,
		Long jobsWithSlides,
		Long generationSeconds) {
}
