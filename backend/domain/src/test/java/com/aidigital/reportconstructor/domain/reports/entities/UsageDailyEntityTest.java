package com.aidigital.reportconstructor.domain.reports.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UsageDailyEntityTest {

	/**
	 * Builds a fully populated rollup row.
	 *
	 * @return the row
	 */
	UsageDailyEntity row() {
		UsageDailyEntity row = new UsageDailyEntity();
		row.setDay(LocalDate.of(2026, 3, 10));
		row.setOwnerUserId("user_abc");
		row.setReportTypeCode("EOC");
		row.setTarget("SLIDES");
		row.setClaudeModel("claude-sonnet-4-6");
		row.setJobs(4);
		row.setJobsWithUsage(3);
		row.setFailedJobs(1);
		row.setClaudeCalls(120);
		row.setInputTokens(10_000);
		row.setOutputTokens(2_000);
		row.setCacheWriteTokens(500);
		row.setCacheReadTokens(9_000);
		row.setSlides(75);
		row.setJobsWithSlides(3);
		row.setGenerationSeconds(1_800);
		row.setRefreshedAt(OffsetDateTime.parse("2026-03-10T09:00:00Z"));
		return row;
	}

	@Test
	void shouldExposeItsGrainAndItsCountersTest() {
		UsageDailyEntity row = row();

		assertThat(row.getDay()).isEqualTo(LocalDate.of(2026, 3, 10));
		assertThat(row.getOwnerUserId()).isEqualTo("user_abc");
		assertThat(row.getReportTypeCode()).isEqualTo("EOC");
		assertThat(row.getTarget()).isEqualTo("SLIDES");
		assertThat(row.getClaudeModel()).isEqualTo("claude-sonnet-4-6");
		assertThat(row.getJobs()).isEqualTo(4);
		assertThat(row.getJobsWithUsage()).isEqualTo(3);
		assertThat(row.getFailedJobs()).isEqualTo(1);
		assertThat(row.getClaudeCalls()).isEqualTo(120);
		assertThat(row.getInputTokens()).isEqualTo(10_000);
		assertThat(row.getOutputTokens()).isEqualTo(2_000);
		assertThat(row.getCacheWriteTokens()).isEqualTo(500);
		assertThat(row.getCacheReadTokens()).isEqualTo(9_000);
		assertThat(row.getSlides()).isEqualTo(75);
		assertThat(row.getJobsWithSlides()).isEqualTo(3);
		assertThat(row.getGenerationSeconds()).isEqualTo(1_800);
		assertThat(row.getRefreshedAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T09:00:00Z"));
	}

	@Test
	void shouldCountEveryStoredValueAsANonNegativeNumberTest() {
		UsageDailyEntity fresh = new UsageDailyEntity();

		// A rollup row is written by an INSERT … SELECT that always supplies every counter, so an
		// unset row is all zeros rather than all nulls — the read side never has to null-check.
		assertThat(fresh.getJobs()).isZero();
		assertThat(fresh.getClaudeCalls()).isZero();
		assertThat(fresh.getSlides()).isZero();
		assertThat(fresh.getGenerationSeconds()).isZero();
	}

	@Test
	void shouldInheritIdAwareEqualitySemanticsTest() {
		UsageDailyEntity left = row();
		UsageDailyEntity right = new UsageDailyEntity();

		// Two rows of the same grain are still different rows until they share an id: the rollup is
		// rebuilt by delete-then-insert, so identity is the surrogate, never the grain.
		assertThat(left).isNotEqualTo(right);

		left.setId(7L);
		right.setId(7L);
		assertThat(left).isEqualTo(right);
		assertThat(left.hashCode()).isEqualTo(right.hashCode());
		assertThat(left).isNotEqualTo(null).isNotEqualTo("other");
	}
}
