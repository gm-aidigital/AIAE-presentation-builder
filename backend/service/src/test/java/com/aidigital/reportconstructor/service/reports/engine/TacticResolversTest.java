package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TacticResolversTest {

	private TacticResolvers resolvers;
	private TacticExtractionHelper tacticUtils;

	@BeforeEach
	void setUp() {
		SheetRowHelper sheetUtils = ReportsEngineTestSupport.sheetRowHelper();
		tacticUtils = ReportsEngineTestSupport.tacticExtractionHelper();
		resolvers = new TacticResolvers(sheetUtils, new Fmt(), tacticUtils,
				new CampaignResolvers(sheetUtils, new Fmt(), tacticUtils, new RatePlanCalculator()),
				new RatePlanCalculator());
	}

	@Test
	void freqFromMax_isDeterministicByTacticIndex() {
		double f1a = tacticUtils.freqFromMax(1, 10.0);
		double f1b = tacticUtils.freqFromMax(1, 10.0);
		double f2 = tacticUtils.freqFromMax(2, 10.0);
		assertThat(f1a).isEqualTo(f1b).isEqualTo(9.0);
		assertThat(f2).isEqualTo(9.6);
		assertThat((int) Math.round((1 - f1a / 10.0) * 100)).isEqualTo(10);
	}

	@Test
	void resolveTacticFreq_usesSameDeterministicReduction() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 100_000, 0, 0, null, null, null, null,
				null, 50_000.0, null, null, 10.0,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticFreq(1, List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("9.00");
		assertThat(r.label()).contains("10%");
	}

	@Test
	void resolveTacticVolume_prefersManualAdjustmentOverride() {
		List<List<String>> adj = List.of(List.of("Tactic 1 volume:", "250K"));
		Resolved r = resolvers.resolveTacticVolume(1, "Display", "1000000", List.of(), adj);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("250K");
	}

	@Test
	void resolveTacticVolume_computesCoefficientTimesRandomTimesMarketVolume() {
		TacticResolvers spy = spy(resolvers);
		doReturn(1.0).when(spy).volumeMultiplier();
		Resolved r = spy.resolveTacticVolume(1, "Display", "1000000", List.of(), List.of());
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.label()).contains("auto");
		assertThat(r.value()).isEqualTo("900k"); // 0.90 * 1.0 * 1,000,000
	}

	@Test
	void resolveTacticVolume_clampsToMarketVolume() {
		TacticResolvers spy = spy(resolvers);
		doReturn(2.0).when(spy).volumeMultiplier();
		Resolved r = spy.resolveTacticVolume(1, "Display", "1000000", List.of(), List.of());
		assertThat(r.value()).isEqualTo("1.0M"); // 0.90 * 2.0 * 1,000,000 clamped to market volume
	}

	@Test
	void resolveTacticVolume_notFoundWhenMarketVolumeMissing() {
		Resolved r = resolvers.resolveTacticVolume(1, "Display", null, List.of(), List.of());
		assertThat(r.source()).isEqualTo("not_found");
		assertThat(r.value()).isNull();
	}

	@Test
	void resolveTacticKpiType_mapsDisplayToCtrAndVideoToVcr() {
		Resolved ctr = resolvers.resolveTacticKpiType(1, "Programmatic Display", List.of(), List.of());
		Resolved vcr = resolvers.resolveTacticKpiType(2, "Programmatic CTV", List.of(), List.of());
		assertThat(ctr.value()).isEqualTo("CTR");
		assertThat(vcr.value()).isEqualTo("VCR");
	}

	@Test
	void resolveTacticKpiType_audioMapsToAcr() {
		Resolved acr = resolvers.resolveTacticKpiType(1, "Programmatic Audio", List.of(), List.of());
		assertThat(acr.value()).isEqualTo("ACR");
	}

	@Test
	void resolveTacticBench_audioUsesAcrLabel() {
		Tactic audio = new Tactic(
				"Programmatic Audio", "Audio", null,
				0, 0, 0, 0, null, null, null, null,
				null, null, null, 80.0, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, audio), null
		);
		Resolved r = resolvers.resolveTacticBench(1, "Programmatic Audio", List.of(), List.of(), data);
		assertThat(r.value()).isEqualTo("ACR – 80%");
	}

	@Test
	void resolveTacticKpiType_prefersManualAdjustmentOverride() {
		List<List<String>> adj = List.of(List.of("Tactic 1 KPI type:", "VCR"));
		Resolved r = resolvers.resolveTacticKpiType(1, "Programmatic Display", List.of(), adj);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("VCR");
	}

	@Test
	void resolveTacticKpi_formatsCtrAsTwoDecimalPercent() {
		Tactic tactic = new Tactic(
				"Programmatic Display", "Display", null,
				0, 100_000, 2_530, 0, 2.53, null, null, null,
				null, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticKpi(1, "Programmatic Display", List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("2.53%");
	}

	@Test
	void resolveTacticKpi_usesVcrForVideoTactic() {
		Tactic tactic = new Tactic(
				"Programmatic CTV", "Video", null,
				0, 100_000, 0, 95_700, null, 95.7, null, null,
				null, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticKpi(1, "Programmatic CTV", List.of(), List.of(), data);
		assertThat(r.value()).isEqualTo("95.7%");
	}

	@Test
	void resolveTacticKpi_notFoundWhenRateMissing() {
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(), null
		);
		Resolved r = resolvers.resolveTacticKpi(1, "Programmatic Display", List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("not_found");
		assertThat(r.value()).isNull();
	}

	@Test
	void resolveTacticSpendPlan_fallsBackToEstimatesPlanSpend() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 0, 0, 0, null, null, null, null,
				45_000.0, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticSpendPlan(1, "Display", List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("$45,000");
	}

	@Test
	void resolveTacticSpendPlan_prefersManualAdjustmentOverride() {
		List<List<String>> adj = List.of(List.of("Tactic 1 spend plan:", "$50,000"));
		Resolved r = resolvers.resolveTacticSpendPlan(1, "Display", List.of(), adj, null);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("$50,000");
	}

	@Test
	void resolveTacticImpsPlan_fallsBackToEstimatesPlanImps() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 0, 0, 0, null, null, null, null,
				null, 250_000.0, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticImpsPlan(1, "Display", List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("250,000");
	}

	@Test
	void resolveTacticImpsPlanAndClicksPlanShouldReadTheirOwnFieldsForACpcTacticTest() {
		// Given: an EOM tactic bought on CPC — CampaignDataCollector already backed the literal
		// impressions figure out of the planned clicks and the Estimates CTR benchmark, alongside the
		// planned clicks themselves
		Tactic tactic = new Tactic(
				"Google SEM", "Search", null,
				0, 4_900_000, 3_429, 0, null, null, null, null,
				null, 144_000.0, null, null, null,
				null, null, null,
				12_000.0, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);

		// When:
		Resolved impsPlan = resolvers.resolveTacticImpsPlan(1, "Google SEM", List.of(), List.of(), data);
		Resolved clicksPlan = resolvers.resolveTacticClicksPlan(1, List.of(), List.of(), data);
		Resolved impsFact = resolvers.resolveTacticImps(1, "Google SEM", List.of(), List.of(), data);

		// Then: impressions plan/fact are always literal impressions, clicks plan is the bought unit
		assertThat(impsPlan.value()).isEqualTo("144,000");
		assertThat(clicksPlan.value()).isEqualTo("12,000");
		assertThat(impsFact.value()).isEqualTo("4,900,000");
	}

	@Test
	void resolveTacticImpsPlanAndCompletionsPlanShouldReadTheirOwnFieldsForACpvTacticTest() {
		// Given: an EOM tactic bought on CPV — CampaignDataCollector already backed the literal
		// impressions figure out of the planned completions and the Estimates VCR benchmark, alongside
		// the planned completions (views) themselves
		Tactic tactic = new Tactic(
				"YouTube", "Video", null,
				0, 3_200_000, 0, 6_000, null, null, null, null,
				null, 19_333.0, null, null, null,
				null, null, null,
				null, 5_800.0
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);

		// When:
		Resolved impsPlan = resolvers.resolveTacticImpsPlan(1, "YouTube", List.of(), List.of(), data);
		Resolved completionsPlan = resolvers.resolveTacticCompletionsPlan(1, List.of(), List.of(), data);
		Resolved impsFact = resolvers.resolveTacticImps(1, "YouTube", List.of(), List.of(), data);

		// Then: impressions plan/fact are always literal impressions, completions plan is the bought unit
		assertThat(impsPlan.value()).isEqualTo("19,333");
		assertThat(completionsPlan.value()).isEqualTo("5,800");
		assertThat(impsFact.value()).isEqualTo("3,200,000");
	}

	@Test
	void resolveTacticClicksPlanAndCompletionsPlanShouldNotFoundWhenTacticCarriesNeitherTest() {
		// Given: a CPM tactic with neither planClicks nor planViews set
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 500_000, 0, 0, null, null, null, null,
				null, 500_000.0, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);

		// When:
		Resolved clicksPlan = resolvers.resolveTacticClicksPlan(1, List.of(), List.of(), data);
		Resolved completionsPlan = resolvers.resolveTacticCompletionsPlan(1, List.of(), List.of(), data);

		// Then:
		assertThat(clicksPlan.source()).isEqualTo("not_found");
		assertThat(completionsPlan.source()).isEqualTo("not_found");
	}

	@Test
	void resolveTacticCtrPlan_formatsEstimatesCtrAsTwoDecimalPercent() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 0, 0, 0, null, null, null, null,
				null, null, 0.45, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticCtrPlan(1, "Display", List.of(), List.of(), data);
		assertThat(r.value()).isEqualTo("0.45%");
	}

	@Test
	void resolveTacticVcrPlan_roundsEstimatesVcr() {
		Tactic tactic = new Tactic(
				"CTV", "Video", null,
				0, 0, 0, 0, null, null, null, null,
				null, null, null, 95.7, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticVcrPlan(1, "CTV", List.of(), List.of(), data);
		assertThat(r.value()).isEqualTo("96%");
	}

	@Test
	void resolveTacticClicks_fallsBackToElevateRawDataClicks() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 100_000, 2_530, 0, null, null, null, null,
				null, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticClicks(1, List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("2,530");
	}

	@Test
	void resolveTacticCompletions_dashWhenTacticHasNoCompletions() {
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 100_000, 2_530, 0, null, null, null, null,
				null, null, null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);
		Resolved r = resolvers.resolveTacticCompletions(1, List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("adj");
		assertThat(r.value()).isEqualTo("—");
	}

	@Test
	void resolveTacticCompletions_notFoundWhenTacticMissing() {
		CampaignData data = new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(), null
		);
		Resolved r = resolvers.resolveTacticCompletions(1, List.of(), List.of(), data);
		assertThat(r.source()).isEqualTo("not_found");
		assertThat(r.value()).isNull();
	}

	@Test
	void volumeCoefficient_resolvesExactKeywordAndDefault() {
		assertThat(tacticUtils.volumeCoefficient("Display")).isEqualTo(0.90);
		assertThat(tacticUtils.volumeCoefficient("CTV/OTT")).isEqualTo(0.70);
		assertThat(tacticUtils.volumeCoefficient("Open Exchange Display")).isEqualTo(0.90); // keyword fallback
		assertThat(tacticUtils.volumeCoefficient("Totally Unknown Channel")).isEqualTo(0.50); // default
	}

}
