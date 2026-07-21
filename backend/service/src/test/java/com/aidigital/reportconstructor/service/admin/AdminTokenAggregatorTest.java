package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
