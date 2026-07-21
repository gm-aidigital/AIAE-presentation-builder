package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminJobTokenTotalsTest {

	/**
	 * Builds a job carrying stamped token counts.
	 *
	 * @param target     generation target wire code, or {@code null}
	 * @param typeCode   report type code
	 * @param createdAt  when the job was created
	 * @param input      plain input tokens, or {@code null} when the job carries no usage
	 * @param output     output tokens
	 * @param calls      Claude calls the run made
	 * @return the job
	 */
	ReportJobEntity job(
			String target, String typeCode, OffsetDateTime createdAt, Long input, long output, Integer calls) {
		ReportJobEntity job = new ReportJobEntity();
		job.setTarget(target);
		job.setReportTypeCode(typeCode);
		job.setCreatedAt(createdAt);
		job.setInputTokens(input);
		job.setOutputTokens(output);
		job.setClaudeCalls(calls);
		job.setClaudeModel("claude-sonnet-4-6");
		return job;
	}

	/**
	 * Builds the component under test priced at $1/MTok for every token class, so cost reads back
	 * as tokens divided by a million.
	 *
	 * @return the component under test
	 */
	AdminJobTokenTotals subject() {
		ClaudeModelPrice price = new ClaudeModelPrice();
		price.setInputPerMtok(1d);
		price.setOutputPerMtok(1d);
		price.setCacheWritePerMtok(1d);
		price.setCacheReadPerMtok(1d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(price);
		return new AdminJobTokenTotals(new JobTokenUsage(new ClaudeCostCalculator(pricing)), new ReportCountPolicy());
	}

	@Test
	void shouldSumPerJobStampsIntoHeadlineTotalsAndAverageOverReportsTest() {
		// Given: two standalone slide reports, one 1,000,000-in/200,000-out and one 3,000,000-in/400,000-out.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(
				job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 200_000, 4),
				job(GenerationTarget.SLIDES.name(), "EOM", now, 3_000_000L, 400_000, 6));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: totals are the summed stamps and the averages divide by the two reports.
		assertThat(totals.reportsWithUsage()).isEqualTo(2);
		assertThat(totals.claudeCalls()).isEqualTo(10);
		assertThat(totals.inputTokens()).isEqualTo(4_000_000);
		assertThat(totals.outputTokens()).isEqualTo(600_000);
		assertThat(totals.totalTokens()).isEqualTo(4_600_000);
		assertThat(totals.costUsd()).isCloseTo(4.6d, within(0.0001d));
		assertThat(totals.avgTokensPerReport()).isEqualTo(2_300_000);
		assertThat(totals.avgOutputPerReport()).isEqualTo(300_000);
	}

	@Test
	void shouldKeepAnIntermediateSheetStepsSpendButNotCountItAsAReportTest() {
		// Given: one slide-deck run split across its intermediate SHEET(EOC) step and the deck it feeds.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(
				job(GenerationTarget.SHEET.name(), "EOC", now, 1_000_000L, 100_000, 3),
				job(GenerationTarget.SLIDES_FROM_SHEET.name(), "EOC", now, 2_000_000L, 300_000, 5));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: both steps' tokens count, but the run averages as a single report.
		assertThat(totals.reportsWithUsage()).isEqualTo(1);
		assertThat(totals.totalTokens()).isEqualTo(3_400_000);
		assertThat(totals.claudeCalls()).isEqualTo(8);
		assertThat(totals.avgTokensPerReport()).isEqualTo(3_400_000);
	}

	@Test
	void shouldIgnoreJobsThatCarryNoUsageTest() {
		// Given: one report with usage and one that made no Claude call (null counts).
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(
				job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 200_000, 4),
				job(GenerationTarget.SLIDES.name(), "EOC", now, null, 0, null));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: only the report with usage is summed and counted.
		assertThat(totals.reportsWithUsage()).isEqualTo(1);
		assertThat(totals.totalTokens()).isEqualTo(1_200_000);
	}

	@Test
	void shouldReturnZeroTotalsWhenNoJobCarriesUsageTest() {
		// Given: a single report that made no Claude call.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(job(GenerationTarget.SLIDES.name(), "EOC", now, null, 0, null));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: every figure reads zero rather than dividing by no reports.
		assertThat(totals.reportsWithUsage()).isZero();
		assertThat(totals.totalTokens()).isZero();
		assertThat(totals.avgTokensPerReport()).isZero();
		assertThat(totals.avgCostPerReportUsd()).isZero();
	}

	@Test
	void shouldLeaveTheEstimatedAndUnattributedFiguresZeroBecauseTheStampCannotSplitThemTest() {
		// Given: a report with usage.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 200_000, 4));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: the measured/estimated split and unattributed figures have no per-job equivalent.
		assertThat(totals.unknownCalls()).isZero();
		assertThat(totals.estimatedTokens()).isZero();
		assertThat(totals.unattributedCalls()).isZero();
		assertThat(totals.unattributedTokens()).isZero();
	}

	@Test
	void shouldCountOnlyTheCurrentCalendarMonthInTheMonthlyFiguresTest() {
		// Given: one report this month and one from two months ago.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(
				job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 200_000, 4),
				job(GenerationTarget.SLIDES.name(), "EOC", now.minusMonths(2), 5_000_000L, 500_000, 7));

		// When:
		AdminTokenTotals totals = subject().totals(jobs, now);

		// Then: the monthly figures see only this month's report, the all-time totals see both.
		assertThat(totals.tokensThisMonth()).isEqualTo(1_200_000);
		assertThat(totals.costThisMonthUsd()).isCloseTo(1.2d, within(0.0001d));
		assertThat(totals.totalTokens()).isEqualTo(6_700_000);
	}

	@Test
	void shouldBuildSevenDaysOfTokenSpendOldestFirstTest() {
		// Given: two reports today and one three days ago.
		OffsetDateTime now = OffsetDateTime.now();
		List<ReportJobEntity> jobs = List.of(
				job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 0, 2),
				job(GenerationTarget.SLIDES.name(), "EOC", now, 1_000_000L, 0, 2),
				job(GenerationTarget.SLIDES.name(), "EOC", now.minusDays(3), 5_000_000L, 0, 3));

		// When:
		List<AdminTokenDay> weekly = subject().weekly(jobs, now);

		// Then: the series spans seven days oldest first, with the day totals landing on their days.
		assertThat(weekly).hasSize(7);
		assertThat(weekly.get(6).totalTokens()).isEqualTo(2_000_000);
		assertThat(weekly.get(3).totalTokens()).isEqualTo(5_000_000);
		assertThat(weekly.get(0).totalTokens()).isZero();
	}
}
