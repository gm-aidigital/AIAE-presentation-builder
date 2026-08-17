package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImpressionContributionHelperImplTest {

	private ImpressionContributionHelperImpl newHelper() {
		return new ImpressionContributionHelperImpl(new ReportNumberParserImpl(), new Fmt());
	}

	@Test
	void fillContributions_shouldSplitEachTacticsShareAgainstTheCampaignTotalTest() {
		// Given: two tactics whose impressions are printed as the deck formats them, and the campaign total
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{total imps}}", "4,000,000");
		flat.put("{{tactic 1 imps}}", "3,000,000");
		flat.put("{{tactic 2 imps}}", "1,000,000");

		// When:
		newHelper().fillContributions(flat, 2);

		// Then: each tactic's share, and the remainder attributed to everything else
		assertThat(flat.get("{{tactic 1 contr}}")).isEqualTo("75.0%");
		assertThat(flat.get("{{tactic 1 other contr}}")).isEqualTo("25.0%");
		assertThat(flat.get("{{tactic 2 contr}}")).isEqualTo("25.0%");
		assertThat(flat.get("{{tactic 2 other contr}}")).isEqualTo("75.0%");
	}

	@Test
	void fillContributions_shouldKeepTheTwoLegendLinesAddingUpToOneHundredTest() {
		// Given: a share that does not round cleanly (1,370,000 / 4,000,000 = 34.25%)
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{total imps}}", "4,000,000");
		flat.put("{{tactic 1 imps}}", "1,370,000");

		// When:
		newHelper().fillContributions(flat, 1);

		// Then: the remainder is taken from the rounded share, so the legend reads 100.0% and not 100.1%
		assertThat(flat.get("{{tactic 1 contr}}")).isEqualTo("34.3%");
		assertThat(flat.get("{{tactic 1 other contr}}")).isEqualTo("65.7%");
	}

	@Test
	void fillContributions_shouldDashRatherThanPrintAMisleadingZeroTest() {
		// Given: a campaign whose total is missing and a tactic with no impressions
		Map<String, String> noTotal = new LinkedHashMap<>();
		noTotal.put("{{tactic 1 imps}}", "1,000");
		Map<String, String> noTactic = new LinkedHashMap<>();
		noTactic.put("{{total imps}}", "4,000");
		noTactic.put("{{tactic 1 imps}}", "—");

		// When:
		newHelper().fillContributions(noTotal, 1);
		newHelper().fillContributions(noTactic, 1);

		// Then: an em dash both times — a 0.0% share would read as a real, disastrous result
		assertThat(noTotal.get("{{tactic 1 contr}}")).isEqualTo("—");
		assertThat(noTotal.get("{{tactic 1 other contr}}")).isEqualTo("—");
		assertThat(noTactic.get("{{tactic 1 contr}}")).isEqualTo("—");
		assertThat(noTactic.get("{{tactic 1 other contr}}")).isEqualTo("—");
	}

	@Test
	void fillContributions_shouldNotOverwriteAValueTheReviewedSheetAlreadyCarriesTest() {
		// Given: the workbook already supplied a contribution for tactic 1
		Map<String, String> flat = new LinkedHashMap<>();
		flat.put("{{total imps}}", "4,000,000");
		flat.put("{{tactic 1 imps}}", "3,000,000");
		flat.put("{{tactic 1 contr}}", "80.0%");

		// When:
		newHelper().fillContributions(flat, 1);

		// Then: the sheet keeps winning, and only the missing side is computed
		assertThat(flat.get("{{tactic 1 contr}}")).isEqualTo("80.0%");
		assertThat(flat.get("{{tactic 1 other contr}}")).isEqualTo("25.0%");
	}
}
