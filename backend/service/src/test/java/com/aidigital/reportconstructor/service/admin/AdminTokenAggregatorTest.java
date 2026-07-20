package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminTokenAggregatorTest {

	/**
	 * Builds a measured event.
	 *
	 * @param jobId     report job the call belongs to, or {@code null}
	 * @param label     batch tag
	 * @param createdAt when the call happened
	 * @param input     plain input tokens
	 * @param output    output tokens
	 * @return the event
	 */
	ClaudeUsageEventEntity measured(Long jobId, String label, OffsetDateTime createdAt, long input, long output) {
		ClaudeUsageEventEntity event = new ClaudeUsageEventEntity();
		event.setJobId(jobId);
		event.setLabel(label);
		event.setStatus(ClaudeUsageStatus.RECORDED.getCode());
		event.setCreatedAt(createdAt);
		event.setInputTokens(input);
		event.setOutputTokens(output);
		event.setModel("claude-sonnet-4-6");
		return event;
	}

	/**
	 * Builds an event for a call that was billed but whose reply never arrived.
	 *
	 * @param jobId          report job the call belongs to
	 * @param label          batch tag
	 * @param createdAt      when the call happened
	 * @param estimatedInput locally estimated prompt size
	 * @return the event
	 */
	ClaudeUsageEventEntity lost(Long jobId, String label, OffsetDateTime createdAt, long estimatedInput) {
		ClaudeUsageEventEntity event = measured(jobId, label, createdAt, estimatedInput, 0);
		event.setStatus(ClaudeUsageStatus.ESTIMATED.getCode());
		return event;
	}

	/**
	 * Builds an aggregator priced at $1/MTok for every token class, so cost reads back as tokens.
	 *
	 * @return the aggregator under test
	 */
	AdminTokenAggregator aggregator() {
		ClaudeModelPrice price = new ClaudeModelPrice();
		price.setInputPerMtok(1d);
		price.setOutputPerMtok(1d);
		price.setCacheWritePerMtok(1d);
		price.setCacheReadPerMtok(1d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(price);
		return new AdminTokenAggregator(new ClaudeCostCalculator(pricing));
	}

	@Test
	void shouldKeepLostCallsOutOfTheMeasuredTotalAndReportThemSeparatelyTest() {
		// Given: two measured BatchC calls that returned 200 output tokens each, and one that timed out
		// with a 4,000-token prompt.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(
				measured(1L, "BatchC", now, 1_000, 200),
				measured(1L, "BatchC", now, 1_000, 200),
				lost(1L, "BatchC", now, 4_000));

		// When:
		AdminTokenTotals totals = aggregator().totals(events, now);

		// Then: the headline total is the measured 2,400 only — the lost call is reported beside it,
		// its output predicted from what the two comparable calls actually returned.
		assertThat(totals.totalTokens()).isEqualTo(2_400);
		assertThat(totals.claudeCalls()).isEqualTo(2);
		assertThat(totals.unknownCalls()).isEqualTo(1);
		assertThat(totals.estimatedTokens()).isEqualTo(4_200);
		assertThat(totals.estimatedCostUsd()).isCloseTo(0.0042d, within(0.00001d));
	}

	@Test
	void shouldPredictNoOutputForALostCallWithNoComparableSuccessTest() {
		// Given: the only call with this batch tag never came back, so nothing says what it would have
		// returned. A prediction must stay a floor rather than an invention.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(lost(1L, "BatchGeo", now, 3_000));

		// When:
		AdminTokenTotals totals = aggregator().totals(events, now);

		// Then:
		assertThat(totals.estimatedTokens()).isEqualTo(3_000);
		assertThat(totals.totalTokens()).isZero();
	}

	@Test
	void shouldSeparateCallsThatBelongToNoReportFromThePerReportAveragesTest() {
		// Given: one report's call plus a line-item match made outside any report.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(
				measured(1L, "BatchC", now, 1_000, 200),
				measured(null, "LineItemMatch", now, 500, 100));

		// When:
		AdminTokenTotals totals = aggregator().totals(events, now);

		// Then: the unattributed call counts toward the team total but not toward "per report", which
		// would otherwise report a per-report cost no report ever incurred.
		assertThat(totals.totalTokens()).isEqualTo(1_800);
		assertThat(totals.unattributedCalls()).isEqualTo(1);
		assertThat(totals.unattributedTokens()).isEqualTo(600);
		assertThat(totals.reportsWithUsage()).isEqualTo(1);
		assertThat(totals.avgTokensPerReport()).isEqualTo(1_200);
	}

	@Test
	void shouldAverageOverDistinctReportsTest() {
		// Given: two calls for one report and one for another.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(
				measured(1L, "BatchA", now, 400, 100),
				measured(1L, "BatchC", now, 400, 100),
				measured(2L, "BatchA", now, 1_000, 0));

		// When:
		AdminTokenTotals totals = aggregator().totals(events, now);

		// Then:
		assertThat(totals.reportsWithUsage()).isEqualTo(2);
		assertThat(totals.avgTokensPerReport()).isEqualTo(1_000);
		assertThat(totals.avgOutputPerReport()).isEqualTo(100);
	}

	@Test
	void shouldReturnZeroTotalsWhenNothingHasBeenRecordedTest() {
		// Given / When:
		AdminTokenTotals totals = aggregator().totals(List.of(), OffsetDateTime.now());

		// Then: no division by zero, and the tab renders an honest empty state.
		assertThat(totals.reportsWithUsage()).isZero();
		assertThat(totals.avgTokensPerReport()).isZero();
		assertThat(totals.avgCostPerReportUsd()).isZero();
		assertThat(totals.costUsd()).isZero();
		assertThat(totals.unknownCalls()).isZero();
	}

	@Test
	void shouldCountOnlyTheCurrentCalendarMonthInTheMonthlyFiguresTest() {
		// Given: one call this month and one two months ago.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(
				measured(1L, "BatchC", now, 1_000_000, 0),
				measured(2L, "BatchC", now.minusMonths(2), 5_000_000, 0));

		// When:
		AdminTokenTotals totals = aggregator().totals(events, now);

		// Then:
		assertThat(totals.tokensThisMonth()).isEqualTo(1_000_000);
		assertThat(totals.costThisMonthUsd()).isCloseTo(1d, within(0.0001d));
		assertThat(totals.totalTokens()).isEqualTo(6_000_000);
	}

	@Test
	void shouldGroupSpendByPipelineStageMostExpensiveFirstTest() {
		// Given: a cheap batch, an expensive one, and a stage that lost a call.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(
				measured(1L, "BatchA", now, 100, 50),
				measured(1L, "BatchC", now, 5_000, 1_000),
				lost(1L, "BatchGeo", now, 900));

		// When:
		List<AdminTokenLabel> byLabel = aggregator().byLabel(events);

		// Then: stages are ranked by measured tokens, and a stage that only ever lost calls still
		// appears — with zero measured spend and its lost call counted.
		assertThat(byLabel.getFirst().label()).isEqualTo("BatchC");
		assertThat(byLabel.getFirst().totalTokens()).isEqualTo(6_000);
		assertThat(byLabel).anySatisfy(row -> {
			assertThat(row.label()).isEqualTo("BatchGeo");
			assertThat(row.calls()).isZero();
			assertThat(row.unknownCalls()).isEqualTo(1);
		});
	}

	@Test
	void shouldBuildSevenDaysOfTokenSpendOldestFirstTest() {
		// Given: one measured call today.
		OffsetDateTime now = OffsetDateTime.now();
		List<ClaudeUsageEventEntity> events = List.of(measured(1L, "BatchC", now, 1_000, 200));

		// When:
		List<AdminTokenDay> weekly = aggregator().weekly(events, now);

		// Then: seven points, today last, carrying the call's tokens.
		assertThat(weekly).hasSize(7);
		assertThat(weekly.getFirst().date()).isEqualTo(now.toLocalDate().minusDays(6));
		assertThat(weekly.getLast().date()).isEqualTo(now.toLocalDate());
		assertThat(weekly.getLast().totalTokens()).isEqualTo(1_200);
		assertThat(weekly.getFirst().totalTokens()).isZero();
	}
}
