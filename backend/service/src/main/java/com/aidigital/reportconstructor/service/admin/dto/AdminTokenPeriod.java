package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDate;

/**
 * One bucket of a token-spend trend — a week or a month — carrying both its own figures and the
 * comparison with the bucket before it.
 *
 * <p>The previous period travels with the row rather than being left for the client to look up. A
 * chart that has to index backwards into its own series to draw a delta gets it wrong at the edges:
 * the first bucket has no predecessor, and a bucket in which nothing happened is missing from the
 * series entirely rather than present as a zero. Both cases are decided once, here, where the
 * calendar is known.
 *
 * <p>{@code prev*} and {@code *DeltaPct} are null for the first bucket, and a delta is also null
 * when the previous bucket was zero — a rise from nothing is not a percentage.
 *
 * @param key             stable bucket id: {@code 2026-W32} for a week, {@code 2026-08} for a month
 * @param start           first day of the bucket
 * @param label           short human label, e.g. {@code Aug 3} or {@code Aug 2026}
 * @param reports         reports created in the bucket
 * @param inputTokens     plain (uncached) input tokens
 * @param outputTokens    output tokens
 * @param cacheTokens     prompt-cache write + read tokens
 * @param totalTokens     every token above, summed
 * @param costUsd         estimated cost at the configured list prices
 * @param prevTotalTokens the previous bucket's tokens, or {@code null} when there is none
 * @param tokensDeltaPct  percentage change in tokens against the previous bucket
 * @param prevReports     the previous bucket's report count, or {@code null} when there is none
 * @param reportsDeltaPct percentage change in reports against the previous bucket
 * @param prevCostUsd     the previous bucket's cost, or {@code null} when there is none
 * @param costDeltaPct    percentage change in cost against the previous bucket
 */
public record AdminTokenPeriod(
		String key,
		LocalDate start,
		String label,
		int reports,
		long inputTokens,
		long outputTokens,
		long cacheTokens,
		long totalTokens,
		double costUsd,
		Long prevTotalTokens,
		Double tokensDeltaPct,
		Integer prevReports,
		Double reportsDeltaPct,
		Double prevCostUsd,
		Double costDeltaPct) {
}
