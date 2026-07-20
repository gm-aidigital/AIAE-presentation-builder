package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ClaudeCostCalculatorTest {

	@Test
	void shouldPriceEachTokenClassAtItsOwnRateTest() {
		// Given: one million of each token class, so the cost equals the sum of the four rates.
		ClaudeModelPrice price = new ClaudeModelPrice();
		price.setInputPerMtok(3d);
		price.setOutputPerMtok(15d);
		price.setCacheWritePerMtok(3.75d);
		price.setCacheReadPerMtok(0.30d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(price);

		// When:
		double cost = new ClaudeCostCalculator(pricing)
				.costUsd(1_000_000, 1_000_000, 1_000_000, 1_000_000, "claude-sonnet-4-6");

		// Then:
		assertThat(cost).isCloseTo(22.05d, within(0.0001d));
	}

	@Test
	void shouldMatchADatedModelIdAgainstItsUndatedPricingKeyTest() {
		// Given: pricing configured under the undated model id, and a job that recorded the dated one.
		ClaudeModelPrice fallback = new ClaudeModelPrice();
		fallback.setInputPerMtok(100d);
		ClaudeModelPrice haiku = new ClaudeModelPrice();
		haiku.setInputPerMtok(1d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(fallback);
		pricing.setByModel(Map.of("claude-haiku-4-5", haiku));

		// When:
		double cost = new ClaudeCostCalculator(pricing)
				.costUsd(1_000_000, 0, 0, 0, "claude-haiku-4-5-20251001");

		// Then: the dated id was priced at the Haiku rate, not the default.
		assertThat(cost).isCloseTo(1d, within(0.0001d));
	}

	@Test
	void shouldFallBackToDefaultRatesForAnUnknownOrMissingModelTest() {
		// Given:
		ClaudeModelPrice fallback = new ClaudeModelPrice();
		fallback.setInputPerMtok(3d);
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(fallback);
		ClaudeCostCalculator calculator = new ClaudeCostCalculator(pricing);

		// When:
		double unknown = calculator.costUsd(1_000_000, 0, 0, 0, "some-other-model");
		double missing = calculator.costUsd(1_000_000, 0, 0, 0, null);

		// Then: unknown models are priced at the default rather than silently costing nothing.
		assertThat(unknown).isCloseTo(3d, within(0.0001d));
		assertThat(missing).isCloseTo(3d, within(0.0001d));
	}
}
