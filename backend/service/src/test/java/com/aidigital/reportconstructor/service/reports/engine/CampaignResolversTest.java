package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
	void resolveReachFact_formatsGivenReachFactValue() {
		// Given: a reachFact value as computed once by computeFrequencies for this report
		Resolved r = resolvers.resolveReachFact(1_100_000d, List.of(), List.of());

		// Then:
		assertThat(r.value()).isEqualTo("1,100,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReachFact_notFoundWhenReachFactNull() {
		// Given: computeFrequencies could not compute a reachFact value
		Resolved r = resolvers.resolveReachFact(null, List.of(), List.of());

		// Then:
		assertThat(r.value()).isNull();
		assertThat(r.source()).isEqualTo("not_found");
	}

	@Test
	void resolveReachFact_manualAdjustmentWins() {
		// Given: a manual "Reach fact:" override in Adjustments
		List<List<String>> adj = labelRow("Reach fact:", "1.1M unique");

		// When:
		Resolved r = resolvers.resolveReachFact(1_100_000d, List.of(), adj);

		// Then:
		assertThat(r.value()).isEqualTo("1.1M unique");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveReachFactShort_compactsGivenReachFactValue() {
		// Given: a reachFact value as computed once by computeFrequencies for this report
		Resolved r = resolvers.resolveReachFactShort(1_200_000d, List.of(), List.of());

		// Then: 1,200,000 -> "1.2M"
		assertThat(r.value()).isEqualTo("1.2M");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReachFactShort_manualAdjustmentWins() {
		// Given: a manual "Reach fact short:" override in Adjustments
		List<List<String>> adj = labelRow("Reach fact short:", "1.1M");

		// When:
		Resolved r = resolvers.resolveReachFactShort(1_100_000d, List.of(), adj);

		// Then:
		assertThat(r.value()).isEqualTo("1.1M");
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

	@Test
	void computeFrequencies_planRoundedUpAndFactDerivedFromReachFact() {
		// Given: a spy whose reach-fact uplift is fixed at 1.10, 3M impressions, and a 1M reach
		CampaignResolvers spyResolvers = spy(ReportsEngineTestSupport.campaignResolvers());
		doReturn(1.10).when(spyResolvers).reachFactMultiplier();
		List<List<String>> estimates = List.of(
				List.of("Media", "Reach"),
				List.of("Total", "1,000,000"));
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 3_000_000, 0, 0, null, null), Map.of(), null);

		// When:
		CampaignFrequencies freq = spyResolvers.computeFrequencies(estimates, List.of(), List.of(), data, null);

		// Then: plan = ceil(3M / 1M) = 3, reach_f = 1M * 1.10 = 1.1M, fact = 3M / 1.1M = 2.73
		assertThat(freq.plan()).isEqualTo("3");
		assertThat(freq.fact()).isEqualTo("2.73");
		assertThat(freq.reachFact()).isEqualTo(1_100_000d);
	}

	@Test
	void computeFrequencies_planRoundsUpNonIntegerFrequency() {
		// Given: 3.2M impressions over 1M reach yields a fractional plan frequency
		CampaignResolvers spyResolvers = spy(ReportsEngineTestSupport.campaignResolvers());
		doReturn(1.05).when(spyResolvers).reachFactMultiplier();
		List<List<String>> estimates = List.of(
				List.of("Media", "Reach"),
				List.of("Total", "1,000,000"));
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 3_200_000, 0, 0, null, null), Map.of(), null);

		// When:
		CampaignFrequencies freq = spyResolvers.computeFrequencies(estimates, List.of(), List.of(), data, null);

		// Then: plan = ceil(3.2) = 4, reach_f = 1M * 1.05 = 1.05M, fact = 3.2M / 1.05M = 3.05
		assertThat(freq.plan()).isEqualTo("4");
		assertThat(freq.fact()).isEqualTo("3.05");
		assertThat(freq.reachFact()).isEqualTo(1_050_000d);
	}

	@Test
	void computeFrequencies_nullWhenReachMissing() {
		// Given: impressions present but no reach column anywhere
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions"),
				List.of("CTV", "1,800,000"));
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 3_000_000, 0, 0, null, null), Map.of(), null);

		// When:
		CampaignFrequencies freq = resolvers.computeFrequencies(estimates, List.of(), List.of(), data, null);

		// Then:
		assertThat(freq.plan()).isNull();
		assertThat(freq.fact()).isNull();
		assertThat(freq.reachFact()).isNull();
	}

	@Test
	void resolveFOpportunity_manualWinsOverClaude() {
		List<List<String>> adj = labelRow("Frequency opportunity:", "Manual opportunity copy.");
		Resolved r = resolvers.resolveFOpportunity(List.of(), adj, "Claude opportunity copy.");
		assertThat(r.value()).isEqualTo("Manual opportunity copy.");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveFFact_fallsBackToClaudeWhenNoManualValue() {
		Resolved r = resolvers.resolveFFact(List.of(), List.of(), "Actual frequency was 3.16.");
		assertThat(r.value()).isEqualTo("Actual frequency was 3.16.");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void resolveFStorytelling_notFoundWhenNoManualOrClaude() {
		Resolved r = resolvers.resolveFStorytelling(List.of(), List.of(), null);
		assertThat(r.value()).isNull();
		assertThat(r.source()).isEqualTo("not_found");
	}

	private static List<List<String>> labelRow(String label, String value) {
		return List.of(List.of(label, value, "", ""));
	}
}
