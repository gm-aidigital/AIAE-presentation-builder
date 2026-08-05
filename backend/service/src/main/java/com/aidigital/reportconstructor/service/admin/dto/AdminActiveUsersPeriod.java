package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDate;

/**
 * Active users in one bucket — a week or a month — with the comparison against the bucket before it.
 *
 * <p>"Active" means the user generated at least one report in the bucket, counted once no matter how
 * many they generated. The counts are not additive across buckets on purpose: a user active in both
 * January and February is one active user in each month and one — not two — in the quarter, which is
 * exactly why the count is taken per bucket from the underlying (day, user) pairs rather than summed
 * out of a smaller number.
 *
 * @param key         stable bucket id: {@code 2026-W32} for a week, {@code 2026-08} for a month
 * @param start       first day of the bucket
 * @param label       short human label, e.g. {@code Aug 3} or {@code Aug 2026}
 * @param activeUsers distinct users who generated something in the bucket
 * @param newUsers    of those, the ones who had never generated anything before this bucket
 * @param prevActive  the previous bucket's active users, or {@code null} when there is none
 * @param deltaPct    percentage change against the previous bucket, {@code null} when it was zero
 */
public record AdminActiveUsersPeriod(
		String key,
		LocalDate start,
		String label,
		int activeUsers,
		int newUsers,
		Integer prevActive,
		Double deltaPct) {
}
