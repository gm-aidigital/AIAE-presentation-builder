package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignDataCollectorTest {

	private final CampaignDataCollector collector = ReportsEngineTestSupport.campaignDataCollector();

	@Test
	void collect_adjOverridesSheetForClientAndCampaign() {
		List<List<String>> sheet = List.of(
				List.of("Client name:", "Sheet Client"),
				List.of("Campaign:", "Sheet Campaign"),
				List.of("Media"),
				List.of("Programmatic Display")
		);
		List<List<String>> adj = List.of(
				List.of("Client name:", "Adj Client"),
				List.of("Campaign:", "Adj Campaign")
		);
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks"),
				List.of("2026-03-01", "Display", "50", "1000", "10")
		);

		CampaignData data = collector.collect(sheet, adj, List.of(), List.of(), List.of(), null, "EOC", null);

		assertThat(data.client()).isEqualTo("Adj Client");
		assertThat(data.campaign()).isEqualTo("Adj Campaign");

		CampaignData bqOnly = collector.collect(List.of(), bq, List.of(), List.of(), List.of(), null, "EOC", null);
		assertThat(bqOnly.totals()).isNotNull();
		assertThat(bqOnly.totals().imps()).isEqualTo(1000);
	}

	@Test
	void collect_repeatedTacticNameGetsItsOwnEstimatesLineItemInOrder() {
		// Given: a media plan repeating "Display" twice around one "Audio", and an Estimates tab carrying a
		// distinct planned spend/impressions per line item in the same top-to-bottom order
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio"),
				List.of("programmatic display")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions"),
				List.of("programmatic display", "100000", "500000"),
				List.of("programmatic audio", "200000", "700000"),
				List.of("programmatic display", "300000", "900000")
		);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, List.of(), null, "EOC", null);

		// Then: each occurrence keeps its own line item's figures rather than all Displays collapsing onto one
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(100000.0);
		assertThat(data.tactics().get(1).planImps()).isEqualTo(500000.0);
		assertThat(data.tactics().get(2).planSpend()).isEqualTo(200000.0);
		assertThat(data.tactics().get(2).planImps()).isEqualTo(700000.0);
		assertThat(data.tactics().get(3).planSpend()).isEqualTo(300000.0);
		assertThat(data.tactics().get(3).planImps()).isEqualTo(900000.0);
	}

	@Test
	void collect_buildsTacticMapFromMediaColumn() {
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("Tactic 1:", "Display Tactic")
		);
		CampaignData data = collector.collect(
				sheet, List.of(), List.of(), List.of(),
				List.of(new LineItemMapping("Display Tactic", "111", 1)), null, "EOC", null);

		assertThat(data.tactics()).containsKey(1);
		assertThat(data.tactics().get(1).name()).contains("Display");
	}

	@Test
	void collectForEomShouldResolvePlanFromRateAndMonthlyBudgetInsteadOfEstimatesTest() {
		// Given: a CPM tactic with a $3,100 monthly budget and $10 unit price, and a 3-month flight
		// total — the full-flight target is monthlyBudget × flightMonthsTotal, converted to imps
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		DateFilter dateFilter = new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 31));
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), dateFilter,
				"EOM", 3);

		// Then: Estimates-sourced fields stay null, and the rate/budget-derived spend/imps are set
		// to the full-flight target: 3100 * 3 = 9300 spend, 9300 / 10 * 1000 = 930,000 impressions
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(9300.0);
		assertThat(data.tactics().get(1).planImps()).isEqualTo(930_000.0);
		assertThat(data.tactics().get(1).planCtr()).isNull();
		assertThat(data.tactics().get(1).planVcr()).isNull();
		assertThat(data.eomMonthNumber()).isEqualTo(1);
		assertThat(data.eomFlightMonthsTotal()).isEqualTo(3);
	}

	@Test
	void collectForEocShouldIgnoreLineItemMappingRateFieldsTest() {
		// Given: the same rate/budget fields as the EOM case, but reportType is EOC
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		DateFilter dateFilter = new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 31));
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), dateFilter,
				"EOC", 3);

		// Then: no Estimates row exists for this tactic, so plan stays unresolved — the rate/budget
		// fields on the mapping are never consulted for EOC
		assertThat(data.tactics().get(1).planSpend()).isNull();
		assertThat(data.tactics().get(1).planImps()).isNull();
		assertThat(data.eomMonthNumber()).isNull();
		assertThat(data.eomFlightMonthsTotal()).isNull();
	}
}
