package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetTacticCountHelperImplTest {

	private final SheetTacticCountHelperImpl helper = new SheetTacticCountHelperImpl();

	@Test
	void shouldCountTheLeadingNamedTacticsTest() {
		// Given: a workbook naming three tactics
		Map<String, String> values = new LinkedHashMap<>();
		values.put("{{tactic 1}}", "Display");
		values.put("{{tactic 2}}", "CTV");
		values.put("{{tactic 3}}", "Audio");

		// When-Then
		assertThat(helper.countFromPlaceholders(values)).isEqualTo(3);
	}

	@Test
	void shouldStopAtTheFirstGapTest() {
		// Given: a workbook with a hole at tactic 3 and a stray name after it
		Map<String, String> values = new LinkedHashMap<>();
		values.put("{{tactic 1}}", "Display");
		values.put("{{tactic 2}}", "  ");
		values.put("{{tactic 3}}", "Audio");

		// When-Then: counting stops at the gap, because the deck numbers tactics densely
		assertThat(helper.countFromPlaceholders(values)).isEqualTo(1);
	}

	@Test
	void shouldCountNothingForAWorkbookThatNamesNoTacticsTest() {
		// Given: a spreadsheet that is not a report workbook, and a null map

		// When-Then: zero, so callers can reject it rather than build a one-tactic report
		assertThat(helper.countFromPlaceholders(Map.of("{{client_name}}", "Acme"))).isZero();
		assertThat(helper.countFromPlaceholders(null)).isZero();
	}

	@Test
	void shouldNotCountBeyondTheTemplateMaximumTest() {
		// Given: a workbook naming more tactics than the template carries
		Map<String, String> values = new LinkedHashMap<>();
		for (int n = 1; n <= 40; n++) {
			values.put("{{tactic " + n + "}}", "Tactic " + n);
		}

		// When-Then
		assertThat(helper.countFromPlaceholders(values)).isEqualTo(28);
	}
}
