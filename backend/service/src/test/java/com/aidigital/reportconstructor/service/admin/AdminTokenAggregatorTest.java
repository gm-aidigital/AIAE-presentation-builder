package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenAggregatorTest {

	/**
	 * Builds a measured aggregate row, as the grouped query returns it.
	 *
	 * @param label  batch tag
	 * @param calls  how many calls the row covers
	 * @param input  plain input tokens
	 * @param output output tokens
	 * @return the aggregate row
	 */
	ClaudeLabelUsage measured(String label, long calls, long input, long output) {
		return new ClaudeLabelUsage(
				label, ClaudeUsageStatus.RECORDED.getCode(), "claude-sonnet-4-6",
				calls, input, output, 0L, 0L);
	}

	/**
	 * Builds an aggregate row for calls that were billed but whose replies never arrived.
	 *
	 * @param label          batch tag
	 * @param calls          how many such calls the row covers
	 * @param estimatedInput locally estimated prompt size
	 * @return the aggregate row
	 */
	ClaudeLabelUsage lost(String label, long calls, long estimatedInput) {
		return new ClaudeLabelUsage(
				label, ClaudeUsageStatus.ESTIMATED.getCode(), "claude-sonnet-4-6",
				calls, estimatedInput, 0L, 0L, 0L);
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
		List<ClaudeLabelUsage> rows = List.of(
				measured("BatchA", 1, 100, 50),
				measured("BatchC", 1, 5_000, 1_000),
				lost("BatchGeo", 1, 900));

		// When:
		List<AdminTokenLabel> byLabel = aggregator().byLabel(rows);

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
