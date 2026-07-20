package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminTokenAggregatorTest {

	/**
	 * Builds a job carrying recorded token counts.
	 *
	 * @param createdAt when the job was created
	 * @param input     plain input tokens
	 * @param output    output tokens
	 * @return the job
	 */
	ReportJobEntity jobWithUsage(OffsetDateTime createdAt, long input, long output) {
		ReportJobEntity job = new ReportJobEntity();
		job.setCreatedAt(createdAt);
		job.setInputTokens(input);
		job.setOutputTokens(output);
		job.setCacheWriteTokens(0L);
		job.setCacheReadTokens(0L);
		job.setClaudeCalls(3);
		job.setClaudeModel("claude-sonnet-4-6");
		return job;
	}

	/**
	 * Builds an aggregator priced at $1/MTok input and $1/MTok output, so cost reads back as tokens.
	 *
	 * @return the aggregator under test
	 */
	AdminTokenAggregator aggregator() {
		ClaudeModelPrice price = new ClaudeModelPrice();
		price.setInputPerMtok(1d);
		price.setOutputPerMtok(1d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(price);
		return new AdminTokenAggregator(new JobTokenUsage(new ClaudeCostCalculator(pricing)));
	}

	@Test
	void shouldAverageOnlyOverReportsThatCarryUsageTest() {
		// Given: two accounted runs and one legacy job with no counts at all.
		OffsetDateTime now = OffsetDateTime.now();
		ReportJobEntity legacy = new ReportJobEntity();
		legacy.setCreatedAt(now);
		List<ReportJobEntity> all = List.of(
				jobWithUsage(now, 1_000, 100),
				jobWithUsage(now, 3_000, 300),
				legacy);

		// When:
		AdminTokenTotals totals = aggregator().totals(all, now);

		// Then: the legacy row neither counts as a report nor drags the mean toward zero.
		assertThat(totals.reportsWithUsage()).isEqualTo(2);
		assertThat(totals.claudeCalls()).isEqualTo(6);
		assertThat(totals.inputTokens()).isEqualTo(4_000);
		assertThat(totals.outputTokens()).isEqualTo(400);
		assertThat(totals.totalTokens()).isEqualTo(4_400);
		assertThat(totals.avgTokensPerReport()).isEqualTo(2_200);
		assertThat(totals.avgInputPerReport()).isEqualTo(2_000);
		assertThat(totals.avgOutputPerReport()).isEqualTo(200);
	}

	@Test
	void shouldReturnZeroTotalsWhenNothingHasBeenRecordedTest() {
		// Given:
		OffsetDateTime now = OffsetDateTime.now();
		ReportJobEntity legacy = new ReportJobEntity();
		legacy.setCreatedAt(now);

		// When:
		AdminTokenTotals totals = aggregator().totals(List.of(legacy), now);

		// Then: no division by zero, and the tab renders an honest empty state.
		assertThat(totals.reportsWithUsage()).isZero();
		assertThat(totals.avgTokensPerReport()).isZero();
		assertThat(totals.avgCostPerReportUsd()).isZero();
		assertThat(totals.costUsd()).isZero();
	}

	@Test
	void shouldCountOnlyTheCurrentCalendarMonthInTheMonthlyFiguresTest() {
		// Given: one run this month and one two months ago.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> all = List.of(
				jobWithUsage(now, 1_000_000, 0),
				jobWithUsage(now.minusMonths(2), 5_000_000, 0));

		// When:
		AdminTokenTotals totals = aggregator().totals(all, now);

		// Then:
		assertThat(totals.tokensThisMonth()).isEqualTo(1_000_000);
		assertThat(totals.costThisMonthUsd()).isCloseTo(1d, within(0.0001d));
		assertThat(totals.totalTokens()).isEqualTo(6_000_000);
	}

	@Test
	void shouldBuildSevenDaysOfTokenSpendOldestFirstTest() {
		// Given: one accounted run today.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> all = List.of(jobWithUsage(now, 1_000, 200));

		// When:
		List<AdminTokenDay> weekly = aggregator().weekly(all, now);

		// Then: seven points, today last, carrying the run's tokens.
		assertThat(weekly).hasSize(7);
		assertThat(weekly.getFirst().date()).isEqualTo(now.toLocalDate().minusDays(6));
		assertThat(weekly.getLast().date()).isEqualTo(now.toLocalDate());
		assertThat(weekly.getLast().totalTokens()).isEqualTo(1_200);
		assertThat(weekly.getFirst().totalTokens()).isZero();
	}
}
