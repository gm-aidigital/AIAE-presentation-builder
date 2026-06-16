package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import org.junit.jupiter.api.Test;

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

		CampaignData data = collector.collect(sheet, adj, List.of(), List.of(), List.of());

		assertThat(data.client()).isEqualTo("Adj Client");
		assertThat(data.campaign()).isEqualTo("Adj Campaign");

		CampaignData bqOnly = collector.collect(List.of(), bq, List.of(), List.of(), List.of());
		assertThat(bqOnly.totals()).isNotNull();
		assertThat(bqOnly.totals().imps()).isEqualTo(1000);
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
				List.of(new LineItemMapping("Display Tactic", "111", 1)));

		assertThat(data.tactics()).containsKey(1);
		assertThat(data.tactics().get(1).name()).contains("Display");
	}
}
