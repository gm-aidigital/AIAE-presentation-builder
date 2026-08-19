package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
	void resolveRfpInfo_fallsBackToCampaignBrief() {
		Resolved r = resolvers.resolveRfpInfo(List.of(), List.of(), "Campaign brief text");
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("Campaign brief text");
	}

	@Test
	void resolveRfpInfo_manualAdjustmentWins() {
		List<List<String>> adj = labelRow("RFP info:", "Manual RFP override");
		Resolved r = resolvers.resolveRfpInfo(List.of(), adj, "Campaign brief text");
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("Manual RFP override");
	}

	@Test
	void resolveRfpInfo_notFoundWhenBriefBlank() {
		Resolved r = resolvers.resolveRfpInfo(List.of(), List.of(), "  ");
		assertThat(r.source()).isEqualTo("not_found");
		assertThat(r.value()).isNull();
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
	void resolveReach_usesSingleTacticReachWhenNoTotalRow() {
		// Given: a media plan with exactly one tactic and no totals row
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"));
		// When: resolving reach
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		// Then: the lone tactic's reach is used directly
		assertThat(r.value()).isEqualTo("900,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReach_sumsTacticReachesScaledWhenSeveralTacticsAndNoTotalRow() {
		// Given: several tactics and no totals row to read a campaign reach from
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"),
				List.of("Display", "8,000,000", "2,500,000"));
		// When: resolving reach
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		// Then: the tactic reaches are summed and de-duplicated by the 0.8 factor: (900k + 2.5M) * 0.8
		assertThat(r.value()).isEqualTo("2,720,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReach_prefersTotalRowOverScaledTacticSum() {
		// Given: several tactics plus a labelled totals row carrying a reach value
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"),
				List.of("Display", "8,000,000", "2,500,000"),
				List.of("Total", "9,800,000", "3,100,000"));
		// When: resolving reach
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		// Then: the totals row wins outright over the scaled tactic sum
		assertThat(r.value()).isEqualTo("3,100,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReach_treatsBlankMediaCellRowAsUnlabelledTotal() {
		// Given: several tactics and an unlabelled totals row (blank Media cell) carrying a reach value
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"),
				List.of("Display", "8,000,000", "2,500,000"),
				List.of("", "9,800,000", "3,100,000"));
		// When: resolving reach
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		// Then: the blank-name row is recognised as the total and wins over the scaled tactic sum
		assertThat(r.value()).isEqualTo("3,100,000");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void resolveReach_sumsTacticReachesWhenTotalRowHasNoReachValue() {
		// Given: several tactics and a totals row whose Reach cell is blank
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("CTV", "1,800,000", "900,000"),
				List.of("Display", "8,000,000", "2,500,000"),
				List.of("Total", "9,800,000", ""));
		// When: resolving reach
		Resolved r = resolvers.resolveReach(estimates, List.of(), List.of());
		// Then: with no usable total, the scaled tactic sum is used: (900k + 2.5M) * 0.8
		assertThat(r.value()).isEqualTo("2,720,000");
		assertThat(r.source()).isEqualTo("sheet");
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

	@Test
	void shouldResolveGeoFromColumnBelowHeaderAcrossSectionRowTest() {
		// Given: a real media-plan grid where the "Geo" header is followed by a section-title row,
		// then the actual locations across several line items
		List<List<String>> sheet = List.of(
				List.of("Flight Start", "Geo", "Media", "Goal"),
				List.of("Evergreen", "", "", ""),
				List.of("2026-06-08", "Texas", "Programmatic Display", "Awareness"),
				List.of("2026-06-08", "Oklahoma", "Google SEM", "Website Traffic"),
				List.of("2026-06-08", "Texas", "Meta", "Website Traffic"),
				List.of("Totals:", "", "", ""));

		// When: geo resolves without any Claude summary
		Resolved r = resolvers.resolveGeoLocations(sheet, List.of(), null);

		// Then: the distinct column values are collected in order, skipping the section row and totals
		assertThat(r.value()).isEqualTo("Texas, Oklahoma");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void shouldFallBackToClaudeGeoSummaryWhenColumnPointsAtTabTest() {
		// Given: every geo cell merely references the Geo tab
		List<List<String>> sheet = List.of(
				List.of("Geo", "Media"),
				List.of("See Geo Tab", "Programmatic Display"),
				List.of("See Geo Tab", "Google SEM"));

		// When: a Claude workbook summary is supplied
		Resolved r = resolvers.resolveGeoLocations(sheet, List.of(), "Texas, Oklahoma, Arkansas");

		// Then: the summary is used instead of the tab pointer
		assertThat(r.value()).isEqualTo("Texas, Oklahoma, Arkansas");
		assertThat(r.source()).isEqualTo("claude");
	}

	@Test
	void shouldResolveFunnelStagesFromGoalColumnDedupedTest() {
		// Given: a "Goal" column with repeated stages across line items, below a section row
		List<List<String>> sheet = List.of(
				List.of("Geo", "Media", "Goal"),
				List.of("", "", ""),
				List.of("Texas", "Programmatic Display", "Consideration & Engagement"),
				List.of("Texas", "Programmatic Display", "Consideration & Engagement"),
				List.of("Oklahoma", "Google SEM", "Website Traffic"),
				List.of("Totals:", "", ""));

		// When: funnel stages resolve without a Claude summary
		Resolved r = resolvers.resolveFunnelStages(sheet, List.of(), null);

		// Then: distinct stages are joined in first-seen order
		assertThat(r.value()).isEqualTo("Consideration & Engagement, Website Traffic");
		assertThat(r.source()).isEqualTo("sheet");
	}

	@Test
	void shouldFallBackToClaudeFunnelSummaryWhenNoColumnTest() {
		// Given: a media plan with no funnel/goal column
		List<List<String>> sheet = List.of(List.of("Campaign:", "Spring"));

		// When: a Claude funnel summary is supplied
		Resolved r = resolvers.resolveFunnelStages(sheet, List.of(), "Awareness, Consideration, Conversion");

		// Then: the summary is used
		assertThat(r.value()).isEqualTo("Awareness, Consideration, Conversion");
		assertThat(r.source()).isEqualTo("claude");
	}

	@Test
	void resolveResultsOverviews_blankSheetCellFallsBackToClaude() {
		// Sheet-as-source template lists the label with an EMPTY value cell (findLabelValue -> "").
		List<List<String>> sheet = labelRow("Our results overview 1:", "");
		Map<String, Resolved> r =
				resolvers.resolveResultsOverviews(sheet, List.of(), Map.of(1, "Claude group-1 narrative."));
		Resolved g1 = r.get("{{Our results overview 1}}");
		assertThat(g1.value()).isEqualTo("Claude group-1 narrative.");
		assertThat(g1.source()).isEqualTo("adj");
	}

	@Test
	void resolveResultsOverviews_manualValueWinsOverClaude() {
		List<List<String>> adj = labelRow("Our results overview 1:", "Hand-written overview.");
		Map<String, Resolved> r =
				resolvers.resolveResultsOverviews(List.of(), adj, Map.of(1, "Claude group-1 narrative."));
		Resolved g1 = r.get("{{Our results overview 1}}");
		assertThat(g1.value()).isEqualTo("Hand-written overview.");
	}

	@Test
	void resolveResultsOverviews_notFoundWhenNoManualAndNoClaude() {
		List<List<String>> sheet = labelRow("Our results overview 1:", "");
		Map<String, Resolved> r = resolvers.resolveResultsOverviews(sheet, List.of(), Map.of());
		Resolved g1 = r.get("{{Our results overview 1}}");
		assertThat(g1.value()).isNull();
		assertThat(g1.source()).isEqualTo("not_found");
	}

	private static List<List<String>> labelRow(String label, String value) {
		return List.of(List.of(label, value, "", ""));
	}

	@Test
	void summedPlanReachShouldAddUpTheReportedTacticsAndDeduplicateTest() {
		// Given: two reported tactics carrying 100,000 and 60,000 planned reach
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, planReachTactic("Display", 100000.0), 2, planReachTactic("CTV", 60000.0)), null
		);

		// When:
		Double reach = resolvers.summedPlanReach(data);

		// Then: the sum, scaled down once into the 0.72–0.88 de-duplication band
		assertThat(reach).isNotNull();
		assertThat(reach).isBetween(160000.0 * 0.72, 160000.0 * 0.88);
	}

	@Test
	void summedPlanReachShouldBeAbsentWhenNoReportedTacticCarriesReachTest() {
		// Given: tactics with planned spend but no Reach column in the plan
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, planSpendTactic("Display", 100000.0)), null
		);

		// When / Then: the caller falls back to the plan's own reach figure
		assertThat(resolvers.summedPlanReach(data)).isNull();
	}

	@Test
	void computeFrequenciesShouldReuseTheSummedReachForEveryReachPlaceholderTest() {
		// Given: a plan whose bottom row claims 400,000 reach while the reported tactics sum to 160,000
		List<List<String>> estimates = List.of(
				List.of("Media", "Impressions", "Reach"),
				List.of("Totals", "1,000,000", "400,000"));
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 1000000, 0, 0, null, null),
				Map.of(1, planReachTactic("Display", 100000.0), 2, planReachTactic("CTV", 60000.0)), null
		);

		// When:
		CampaignFrequencies freq = resolvers.computeFrequencies(estimates, List.of(), List.of(), data, null);

		// Then: the reach carried on the result is the summed one, and {{reach}} shows that same number
		assertThat(freq.reachPlan()).isBetween(160000.0 * 0.72, 160000.0 * 0.88);
		Resolved reach = resolvers.resolveReach(estimates, List.of(), List.of(), freq.reachPlan());
		assertThat(reach.value()).isEqualTo(String.format("%,d", Math.round(freq.reachPlan())));
		assertThat(reach.label()).contains("summed over the reported tactics");
	}

	/** A reported tactic carrying only planned spend. */
	private Tactic planSpendTactic(String name, double planSpend) {
		return new Tactic(name, name, null, 0, 0, 0, 0, null, null, null, null, planSpend, null, null, null, null,
				null, null, null, null, null, null, null);
	}

	/** A reported tactic carrying only planned impressions. */
	private Tactic planImpsTactic(double planImps) {
		return new Tactic("CTV", "CTV", null, 0, 0, 0, 0, null, null, null, null, null, planImps, null, null, null,
				null, null, null, null, null, null, null);
	}

	/** A reported tactic carrying only planned reach. */
	private Tactic planReachTactic(String name, double planReach) {
		return new Tactic(name, name, null, 0, 0, 0, 0, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, planReach);
	}

	@Test
	void shouldRenderTheImpressionsPaceAsASignedLiftWhenOverPlanTest() {
		// Given: 255,323 delivered against a 250,000 tactic plan
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 255_323, 0, 0, null, null),
				Map.of(1, planImpsTactic(250_000d)), null);

		// When:
		Resolved r = resolvers.resolveTotalImpsPace(List.of(), List.of(), data);

		// Then:
		assertThat(r.value()).isEqualTo("+2%");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void shouldRenderTheImpressionsPaceAsAShareOfPlanWhenAtOrBelowPlanTest() {
		// Given: 245,000 delivered against a 250,000 tactic plan
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 245_000, 0, 0, null, null),
				Map.of(1, planImpsTactic(250_000d)), null);

		// When:
		Resolved r = resolvers.resolveTotalImpsPace(List.of(), List.of(), data);

		// Then: a shortfall reads as the share of plan delivered, never as a negative number
		assertThat(r.value()).isEqualTo("98%");
	}

	@Test
	void shouldRenderTheImpressionsPaceAsExactlyOneHundredPercentOnPlanTest() {
		// Given: delivery exactly on plan
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 250_000, 0, 0, null, null),
				Map.of(1, planImpsTactic(250_000d)), null);

		// When:
		Resolved r = resolvers.resolveTotalImpsPace(List.of(), List.of(), data);

		// Then:
		assertThat(r.value()).isEqualTo("100%");
	}

	@Test
	void shouldNameASingleReportingMonthWithItsYearTest() {
		// Given: a reporting window covering August 2026 only
		CampaignData data = campaignDataWithWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

		// When:
		Resolved r = resolvers.resolveReportingMonth(List.of(), List.of(), data);

		// Then:
		assertThat(r.value()).isEqualTo("August 2026");
		assertThat(r.source()).isEqualTo("adj");
	}

	@Test
	void shouldNameATwoMonthReportingWindowAsARangeTest() {
		// Given: a reporting window spanning July and August of the same year
		CampaignData data = campaignDataWithWindow(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));

		// When:
		Resolved r = resolvers.resolveReportingMonth(List.of(), List.of(), data);

		// Then: the year is stated once, at the end
		assertThat(r.value()).isEqualTo("July - August 2026");
	}

	@Test
	void shouldNameAReportingWindowCrossingTheYearBoundaryWithBothYearsTest() {
		// Given: a reporting window running from December into January
		CampaignData data = campaignDataWithWindow(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31));

		// When:
		Resolved r = resolvers.resolveReportingMonth(List.of(), List.of(), data);

		// Then:
		assertThat(r.value()).isEqualTo("December 2025 - January 2026");
	}

	@Test
	void shouldReadTheFlightMonthTokensFromTheCampaignFlightFieldsTest() {
		// Given: the second month of a three-month flight
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), 1, 1, null, 2, 3, null);

		// When:
		Resolved number = resolvers.resolveCampaignMonthNumber(List.of(), List.of(), data);
		Resolved total = resolvers.resolveCampaignMonthsTotal(List.of(), List.of(), data);

		// Then:
		assertThat(number.value()).isEqualTo("2");
		assertThat(total.value()).isEqualTo("3");
	}

	@Test
	void shouldAbbreviateThePlannedAndDeliveredImpressionsForTheCoverTest() {
		// Given: a 1,150,000 plan against 987,000 delivered
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 987_000, 0, 0, null, null),
				Map.of(1, planImpsTactic(1_150_000d)), null);

		// When:
		Resolved planned = resolvers.resolveTotalPlannedImpsShort(List.of(), List.of(), data);
		Resolved fact = resolvers.resolveTotalFactImpsShort(List.of(), List.of(), data);

		// Then: upper-case suffixes, truncated rather than rounded
		assertThat(planned.value()).isEqualTo("1.1M");
		assertThat(fact.value()).isEqualTo("987K");
	}

	@Test
	void shouldPreferAManualOverrideForEveryCoverTokenTest() {
		// Given: the Adjustments tab restating each cover figure by hand
		List<List<String>> adj = List.of(
				List.of("Reporting month:", "September 2026"),
				List.of("Flight months total:", "6"),
				List.of("Flight month number:", "4"),
				List.of("Planned total impressions short:", "2.0M"),
				List.of("Fact total impressions short:", "1.9M"));
		CampaignData data = campaignDataWithWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

		// When-Then:
		assertThat(resolvers.resolveReportingMonth(List.of(), adj, data).value()).isEqualTo("September 2026");
		assertThat(resolvers.resolveCampaignMonthsTotal(List.of(), adj, data).value()).isEqualTo("6");
		assertThat(resolvers.resolveCampaignMonthNumber(List.of(), adj, data).value()).isEqualTo("4");
		assertThat(resolvers.resolveTotalPlannedImpsShort(List.of(), adj, data).value()).isEqualTo("2.0M");
		assertThat(resolvers.resolveTotalFactImpsShort(List.of(), adj, data).value()).isEqualTo("1.9M");
	}

	/** Campaign data carrying only the reporting window the cover's month label reads. */
	private CampaignData campaignDataWithWindow(LocalDate start, LocalDate end) {
		return new CampaignData(
				null, null, null, null, null, new FlightDates(start, end), null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);
	}

	@Test
	void resolvePacingTakeaways_fillsEverySlotAndPrefersTheSheetTest() {
		// Given: Claude wrote two takeaways and the user rewrote the first one in the workbook
		List<List<String>> sheet = labelRow("Pacing dash takeaway 1:", "Reviewed by hand");
		List<String> claude = List.of("Everything on pace.", "Display is 12% behind, budget shifted.");

		// When:
		Map<String, Resolved> out = resolvers.resolvePacingTakeaways(sheet, List.of(), claude);

		// Then: the sheet wins slot 1, Claude fills slot 2, and the unfilled slots stay dashes rather than
		// raw tokens on a slide that survives the trim
		assertThat(out.get("{{pacing dash takeaway 1}}").value()).isEqualTo("Reviewed by hand");
		assertThat(out.get("{{pacing dash takeaway 2}}").value())
				.isEqualTo("Display is 12% behind, budget shifted.");
		assertThat(out.get("{{pacing dash takeaway 3}}").value()).isNull();
		assertThat(out.get("{{pacing dash takeaway 4}}").source()).isEqualTo("not_found");
	}

	@Test
	void resolvePacingTakeaways_emptyOnAnEndOfCampaignRunTest() {
		// Given: the EOC flavour never asks for the field
		// When:
		Map<String, Resolved> out = resolvers.resolvePacingTakeaways(List.of(), List.of(), List.of());

		// Then: four dashed slots, no exception
		assertThat(out).hasSize(4);
		assertThat(out.values()).allMatch(r -> r.value() == null);
	}
}
