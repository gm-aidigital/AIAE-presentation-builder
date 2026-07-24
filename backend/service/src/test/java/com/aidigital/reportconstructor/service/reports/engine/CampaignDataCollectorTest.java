package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
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

		CampaignData data = collector.collect(sheet, adj, List.of(), List.of(), List.of(), null);

		assertThat(data.client()).isEqualTo("Adj Client");
		assertThat(data.campaign()).isEqualTo("Adj Campaign");

		CampaignData bqOnly = collector.collect(List.of(), bq, List.of(), List.of(), List.of(), null);
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
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, List.of(), null);

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
				List.of(new LineItemMapping("Display Tactic", "111", 1)), null);

		assertThat(data.tactics()).containsKey(1);
		assertThat(data.tactics().get(1).name()).contains("Display");
	}

	@Test
	void collectWithReportPeriodShouldReAggregateActualsOverTheNarrowerWindowTest() {
		// Given: 10 days of delivery (Jan 1–10, 100 imps/day), a reporting period covering only the
		// first 5 days
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		List<List<String>> bq = new ArrayList<>();
		bq.add(List.of("Date", "Channel", "Cost", "Impressions", "Clicks"));
		for (int day = 1; day <= 10; day++) {
			bq.add(List.of("2026-01-%02d".formatted(day), "Display", "10", "100", "1"));
		}
		FlightDates reportPeriod = new FlightDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));

		// When:
		CampaignData data = collector.collect(sheet, bq, List.of(), List.of(), List.of(), null, reportPeriod);

		// Then: the full-flight totals still cover all 10 days (EOC behaviour unchanged)...
		assertThat(data.totals().imps()).isEqualTo(1000);
		// ...while the period totals cover only the first 5
		assertThat(data.periodTotals()).isNotNull();
		assertThat(data.periodTotals().imps()).isEqualTo(500);
		assertThat(data.reportPeriod()).isEqualTo(reportPeriod);
	}

	@Test
	void collectWithoutReportPeriodShouldLeavePeriodFieldsNullTest() {
		// Given/When: the plain EOC-equivalent overload
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks"),
				List.of("2026-03-01", "Display", "50", "1000", "10"));
		CampaignData data = collector.collect(List.of(), bq, List.of(), List.of(), List.of(), null);

		// Then:
		assertThat(data.reportPeriod()).isNull();
		assertThat(data.periodTotals()).isNull();
		assertThat(data.periodTactics()).isNull();
	}
}
