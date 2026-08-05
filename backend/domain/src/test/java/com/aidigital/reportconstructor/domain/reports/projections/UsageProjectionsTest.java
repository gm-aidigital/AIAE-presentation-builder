package com.aidigital.reportconstructor.domain.reports.projections;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregate shapes the dashboard reads.
 *
 * <p>These are constructed by Hibernate from JPQL constructor expressions, so their component order
 * is a contract with those queries rather than an implementation detail: reordering a component
 * compiles cleanly and silently swaps two columns. Pinning the order here is what turns that into a
 * failing test.
 */
class UsageProjectionsTest {

	@Test
	void shouldCarryADayBucketInTheOrderTheQueryBuildsItTest() {
		UsageDailyBucket bucket = new UsageDailyBucket(
				LocalDate.of(2026, 3, 10), "EOC", "SLIDES", "claude-sonnet-4-6",
				4L, 3L, 1L, 120L, 10_000L, 2_000L, 500L, 9_000L, 75L, 3L, 1_800L);

		assertThat(bucket.day()).isEqualTo(LocalDate.of(2026, 3, 10));
		assertThat(bucket.reportTypeCode()).isEqualTo("EOC");
		assertThat(bucket.target()).isEqualTo("SLIDES");
		assertThat(bucket.claudeModel()).isEqualTo("claude-sonnet-4-6");
		assertThat(bucket.jobs()).isEqualTo(4L);
		assertThat(bucket.jobsWithUsage()).isEqualTo(3L);
		assertThat(bucket.failedJobs()).isEqualTo(1L);
		assertThat(bucket.claudeCalls()).isEqualTo(120L);
		assertThat(bucket.inputTokens()).isEqualTo(10_000L);
		assertThat(bucket.outputTokens()).isEqualTo(2_000L);
		assertThat(bucket.cacheWriteTokens()).isEqualTo(500L);
		assertThat(bucket.cacheReadTokens()).isEqualTo(9_000L);
		assertThat(bucket.slides()).isEqualTo(75L);
		assertThat(bucket.jobsWithSlides()).isEqualTo(3L);
		assertThat(bucket.generationSeconds()).isEqualTo(1_800L);
	}

	@Test
	void shouldCarryAUserRowInTheOrderTheQueryBuildsItTest() {
		UsageDailyUserRow row = new UsageDailyUserRow(
				"user_abc", "EOM", "SLIDES_FROM_SHEET", "claude-sonnet-4-6",
				9L, 8L, 300L, 40_000L, 6_000L, 1_000L, 20_000L, 140L, 8L, 5_400L);

		assertThat(row.ownerUserId()).isEqualTo("user_abc");
		assertThat(row.jobs()).isEqualTo(9L);
		// Jobs carrying usage are a subset of the jobs themselves, counted in the same pass, so this
		// can never exceed it.
		assertThat(row.jobsWithUsage()).isEqualTo(8L).isLessThanOrEqualTo(row.jobs());
		assertThat(row.claudeCalls()).isEqualTo(300L);
		assertThat(row.outputTokens()).isEqualTo(6_000L);
		assertThat(row.slides()).isEqualTo(140L);
		assertThat(row.generationSeconds()).isEqualTo(5_400L);
	}

	@Test
	void shouldPairADayWithTheUserActiveOnItTest() {
		UsageActiveDay active = new UsageActiveDay(LocalDate.of(2026, 3, 10), "user_abc");

		assertThat(active.day()).isEqualTo(LocalDate.of(2026, 3, 10));
		assertThat(active.ownerUserId()).isEqualTo("user_abc");
		assertThat(active).isEqualTo(new UsageActiveDay(LocalDate.of(2026, 3, 10), "user_abc"));
	}

	@Test
	void shouldCarryAnOwnerWithTheirLastActivityTest() {
		JobOwner owner = new JobOwner(
				"user_abc", "someone@example.com", OffsetDateTime.parse("2026-03-10T09:00:00Z"));

		assertThat(owner.ownerUserId()).isEqualTo("user_abc");
		assertThat(owner.ownerEmail()).isEqualTo("someone@example.com");
		assertThat(owner.lastActivity()).isEqualTo(OffsetDateTime.parse("2026-03-10T09:00:00Z"));
	}

	@Test
	void shouldAllowAnOwnerWithNoRecordedEmailTest() {
		JobOwner legacy = new JobOwner("user_legacy", null, null);

		// Jobs predating the owner-email column carry none, and the per-user table falls back to the
		// internal id rather than dropping the row.
		assertThat(legacy.ownerEmail()).isNull();
		assertThat(legacy.lastActivity()).isNull();
	}

	@Test
	void shouldKeepStatusAndModelInTheLabelGrainTest() {
		ClaudeLabelUsage row = new ClaudeLabelUsage(
				"BatchC", "recorded", "claude-sonnet-4-6", 12L, 5_000L, 900L, 100L, 3_000L);

		// Status and model stay in the grain on purpose: measured and estimated spend must never be
		// summed together, and cost is priced per model at read time.
		assertThat(row.label()).isEqualTo("BatchC");
		assertThat(row.status()).isEqualTo("recorded");
		assertThat(row.model()).isEqualTo("claude-sonnet-4-6");
		assertThat(row.calls()).isEqualTo(12L);
		assertThat(row.inputTokens()).isEqualTo(5_000L);
		assertThat(row.outputTokens()).isEqualTo(900L);
		assertThat(row.cacheWriteTokens()).isEqualTo(100L);
		assertThat(row.cacheReadTokens()).isEqualTo(3_000L);
	}
}
