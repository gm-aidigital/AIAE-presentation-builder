package com.aidigital.reportconstructor.externalservices.anthropic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTokenEstimatorTest {

	@Test
	void shouldEstimateTokensAsCharactersRoundedUpTest() {
		// Given:
		PromptTokenEstimator estimator = new PromptTokenEstimator();

		// When-Then: a partial token still counts as one, and blank input costs nothing
		assertThat(estimator.estimateTokens("abcd")).isEqualTo(1);
		assertThat(estimator.estimateTokens("abcde")).isEqualTo(2);
		assertThat(estimator.estimateTokens("")).isZero();
		assertThat(estimator.estimateTokens(null)).isZero();
	}

	@Test
	void shouldReportWhetherPromptFitsTheBudgetTest() {
		// Given: a 2000-token budget and a prompt that is one character too long for it
		PromptTokenEstimator estimator = new PromptTokenEstimator();
		String exact = "x".repeat(estimator.maxCharsFor(2000));

		// When-Then:
		assertThat(estimator.fitsWithin(exact, 2000)).isTrue();
		assertThat(estimator.fitsWithin(exact + "x", 2000)).isFalse();
	}
}
