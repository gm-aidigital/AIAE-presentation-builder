package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignResolversTest {

	private CampaignResolvers resolvers;

	@BeforeEach
	void setUp() {
		resolvers = ReportsEngineTestSupport.campaignResolvers();
	}

	@Test
	void resolve_adjWinsOverSheet() {
		List<List<String>> sheet = labelRow("Client name:", "SheetCo");
		List<List<String>> adj = labelRow("Client name:", "AdjCo");
		Resolved r = resolvers.resolve(sheet, adj, "Client name:");
		assertThat(r.value()).isEqualTo("AdjCo");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveTotalImps_autoUsesAdjSourceTag() {
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 5000, 0, 0, null, null),
				Map.of(), null
		);
		Resolved r = resolvers.resolveTotalImps(List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("5,000");
	}

	@Test
	void resolveRecommendations_manualWinsOverClaude() {
		List<List<String>> adj = List.of(
				List.of("Recommendation 1:", "Scale CTV", "", ""),
				List.of("Recommendation 1 text:", "Shift budget to CTV evenings.", "", ""));
		List<Recommendation> claude = List.of(
				new Recommendation("Claude title", "Claude text"),
				new Recommendation("Refresh creative", "Rotate display creative monthly."),
				new Recommendation("", ""),
				new Recommendation("", ""));

		Map<String, Resolved> result = resolvers.resolveRecommendations(List.of(), adj, claude);

		assertThat(result.get("{{recommendation 1}}").value()).isEqualTo("Scale CTV");
		assertThat(result.get("{{recommendation 1}}").source()).isEqualTo("adj");
		assertThat(result.get("{{recommendation 1 text}}").value()).isEqualTo("Shift budget to CTV evenings.");
		assertThat(result.get("{{recommendation 2}}").value()).isEqualTo("Refresh creative");
		assertThat(result.get("{{recommendation 2 text}}").value()).isEqualTo("Rotate display creative monthly.");
		assertThat(result.get("{{recommendation 3}}").value()).isNull();
		assertThat(result.get("{{recommendation 3}}").source()).isEqualTo("not_found");
		assertThat(result.get("{{recommendation 4 text}}").value()).isNull();
	}

	@Test
	void resolveReach_usesBottomEstimatesReachValue() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"),
				List.of("Display", "8,000,000", "2,500,000"),
				List.of("Total", "9,800,000", "3,100,000"));
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		assertThat(r.value()).isEqualTo("3,100,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReach_fallsBackToProposalWhenEstimatesHasNoReachColumn() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions"),
				List.of("CTV", "1,800,000"));
		List<List<String>> proposal = List.of(
				List.of("Media", "Reach"),
				List.of("CTV", "900,000"),
				List.of("Total", "2,750,000"));
		Resolved r = resolvers.resolveReach(estimates, proposal, List.of());
		assertThat(r.value()).isEqualTo("2,750,000");
	}

	@Test
	void resolveReach_manualAdjustmentWins() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Reach"),
				List.of("Total", "3,100,000"));
		List<List<String>> adj = labelRow("Reach:", "4M unique");
		Resolved r = resolvers.resolveReach(estimates, List.of(), adj);
		assertThat(r.value()).isEqualTo("4M unique");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveReach_notFoundWhenNoReachColumnAnywhere() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions"),
				List.of("CTV", "1,800,000"));
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		assertThat(r.value()).isNull();
		assertThat(r.source()).isEqualTo("not_found");
	}

	@Test
	void resolveReachShort_compactsBottomEstimatesReachValue() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Reach"),
				List.of("CTV", "900,000"),
				List.of("Total", "1,234,567"));
		Resolved r = resolvers.resolveReachShort(estimates, List.of(), List.of());
		assertThat(r.value()).isEqualTo("1.2M");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReachShort_fallsBackToProposalWhenEstimatesHasNoReachColumn() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions"),
				List.of("CTV", "1,800,000"));
		List<List<String>> proposal = List.of(
				List.of("Media", "Reach"),
				List.of("Total", "702,431"));
		Resolved r = resolvers.resolveReachShort(estimates, proposal, List.of());
		assertThat(r.value()).isEqualTo("702k");
	}

	@Test
	void resolveReachShort_manualAdjustmentWins() {
		List<List<String>> estimates = List.of(
				List.of("Media", "Reach"),
				List.of("Total", "1,234,567"));
		List<List<String>> adj = labelRow("Reach short:", "1.3M");
		Resolved r = resolvers.resolveReachShort(estimates, List.of(), adj);
		assertThat(r.value()).isEqualTo("1.3M");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveMarketVolume_compactsUiValue() {
		Resolved r = resolvers.resolveMarketVolume("74,542", List.of(), List.of());
		assertThat(r.value()).isEqualTo("74k");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveMarketVolume_compactsMillionsUiValue() {
		Resolved r = resolvers.resolveMarketVolume("1234567", List.of(), List.of());
		assertThat(r.value()).isEqualTo("1.2M");
	}

	@Test
	void resolveMarketVolume_manualAdjustmentWins() {
		List<List<String>> adj = labelRow("Market volume:", "5M reachable");
		Resolved r = resolvers.resolveMarketVolume("74,542", List.of(), adj);
		assertThat(r.value()).isEqualTo("5M reachable");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveMarketVolume_notFoundWhenUiValueBlank() {
		Resolved r = resolvers.resolveMarketVolume("", List.of(), List.of());
		assertThat(r.value()).isNull();
		assertThat(r.source()).isEqualTo("not_found");
	}

	private static List<List<String>> labelRow(String label, String value) {
		return List.of(List.of(label, value, "", ""));
	}
}
