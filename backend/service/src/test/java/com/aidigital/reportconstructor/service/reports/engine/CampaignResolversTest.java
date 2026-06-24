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

	private static List<List<String>> labelRow(String label, String value) {
		return List.of(List.of(label, value, "", ""));
	}
}
