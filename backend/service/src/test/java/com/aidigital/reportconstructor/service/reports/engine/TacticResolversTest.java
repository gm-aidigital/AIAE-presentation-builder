package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
		assertThat(r.value()).isEqualTo("45000.00");
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
	void resolveTacticImpsPlanShouldStayLiteralImpressionsForACpcTacticTest() {
		// Given: an EOM tactic bought on CPC — the planned clicks are the bought unit and the planned
		// impressions are the figure backed out of them and the CTR benchmark; each has its own column
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

		// Then: Impressions Plan is literal impressions, Clicks Plan carries the bought unit, and the
		// fact side stays literal impressions
		assertThat(impsPlan.value()).isEqualTo("144,000");
		assertThat(clicksPlan.value()).isEqualTo("12,000");
		assertThat(impsFact.value()).isEqualTo("4,900,000");
	}

	@Test
	void resolveTacticImpsPlanShouldStayLiteralImpressionsForACpvTacticTest() {
		// Given: an EOM tactic bought on CPV — the planned completions are the bought unit and the planned
		// impressions are the figure backed out of them and the VCR benchmark; each has its own column
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

		// Then: Impressions Plan is literal impressions, Completions Plan carries the bought unit, and the
		// fact side stays literal impressions
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
	void resolveTacticVcrPlan_formatsEstimatesVcrToTwoDecimals() {
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
		assertThat(r.value()).isEqualTo("95.70%");
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
	void shouldResolveUnitRateFromMatchingStepByTacticNumberTest() {
		// Given: two tactics share a display name, and each carries its own rate type / unit price
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "592884", 1, RateType.CPM, 6.0, 1500.0),
				new LineItemMapping("Programmatic Display", "592885", 2, RateType.CPC, 1.25, 1500.0));

		// When: the unit rate is resolved for each slot
		Resolved first = resolvers.resolveTacticUnitRate(1, List.of(), List.of(), mapping);
		Resolved second = resolvers.resolveTacticUnitRate(2, List.of(), List.of(), mapping);

		// Then: each slot gets its own price as a bare two-decimal number, joined on tacticNum rather
		// than on the name
		assertThat(first.value()).isEqualTo("6.00");
		assertThat(first.source()).isEqualTo("adj");
		assertThat(second.value()).isEqualTo("1.25");
	}

	@Test
	void shouldWriteUnitRateWithoutThousandsSeparatorTest() {
		// Given: a rate above a thousand, where a grouped "1,200.00" would land in the cell as text
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("CTV", "592886", 1, RateType.CPM, 1200.0, 5000.0));

		// When: the unit rate is resolved
		Resolved resolved = resolvers.resolveTacticUnitRate(1, List.of(), List.of(), mapping);

		// Then: it stays an ungrouped number Sheets can parse
		assertThat(resolved.value()).isEqualTo("1200.00");
	}

	@Test
	void shouldResolveRateTypeFromMatchingStepByTacticNumberTest() {
		// Given: two tactics with the same display name bought on different models
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "592884", 1, RateType.CPM, 6.0, 1500.0),
				new LineItemMapping("Programmatic Display", "592885", 2, RateType.CPV, 0.04, 1500.0));

		// When: the rate type is resolved for each slot
		Resolved first = resolvers.resolveTacticRateType(1, List.of(), List.of(), mapping);
		Resolved second = resolvers.resolveTacticRateType(2, List.of(), List.of(), mapping);

		// Then: each slot carries the buying model the user picked for it
		assertThat(first.value()).isEqualTo("CPM");
		assertThat(second.value()).isEqualTo("CPV");
	}

	@Test
	void shouldNotFindRateTypeWhenTacticHasNoRateEconomicsTest() {
		// Given: an EOC-style mapping with no rate type
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "592884", 1));

		// When: the rate type is resolved
		Resolved resolved = resolvers.resolveTacticRateType(1, List.of(), List.of(), mapping);

		// Then: it is not found, so the sheet cell renders as a dash
		assertThat(resolved.source()).isEqualTo("not_found");
		assertThat(resolved.value()).isNull();
	}

	@Test
	void shouldNotFindUnitRateWhenTacticHasNoRateEconomicsTest() {
		// Given: an EOC-style mapping with no rate type or unit price
		List<LineItemMapping> mapping = List.of(new LineItemMapping("Display", "592884", 1));

		// When: the unit rate is resolved
		Resolved resolved = resolvers.resolveTacticUnitRate(1, List.of(), List.of(), mapping);

		// Then: it is not found, so the sheet cell renders as a dash
		assertThat(resolved.source()).isEqualTo("not_found");
		assertThat(resolved.value()).isNull();
	}

	@Test
	void shouldPreferManualUnitRateOverrideOverMatchingStepTest() {
		// Given: an Adjustments row overrides the unit rate the matching step supplied
		List<List<String>> adj = List.of(List.of("Tactic 1 unit rate:", "4.20"));
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Display", "592884", 1, RateType.CPM, 6.0, 1500.0));

		// When: the unit rate is resolved
		Resolved resolved = resolvers.resolveTacticUnitRate(1, List.of(), adj, mapping);

		// Then: the manual override wins
		assertThat(resolved.value()).isEqualTo("4.20");
		assertThat(resolved.source()).isEqualTo("adj");
	}

	@Test
	void shouldDeriveEomFrequencyFromTheMediaPlansWeeklyFrequencyTest() {
		// Given: an EOM tactic whose media plan sets one exposure per week, reported over a 31-day month
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 1_000_000, 0, 0, null, null, null, null,
				null, 1_000_000.0, null, null, 10.0,
				null, null, null,
				null, null, 1.0
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), 1, 1, null
		);

		// When: frequency and reach are resolved
		Resolved freq = resolvers.resolveTacticFreq(1, List.of(), List.of(), data);
		Resolved reach = resolvers.resolveTacticReach(1, List.of(), List.of(), data);

		// Then: the weekly figure is scaled over 31/7 weeks and discounted by this tactic's fixed 13%,
		// and reach is impressions ÷ that same frequency, rounded to whole people
		assertThat(freq.value()).isEqualTo("3.85"); // 1.00 × 4.428571 × 0.87
		assertThat(freq.label()).contains("freq/week");
		assertThat(reach.value()).isEqualTo("259,740"); // 1,000,000 ÷ 3.85
	}

	@Test
	void shouldKeepMaxFrequencyDerivationWhenTheMediaPlanHasNoWeeklyColumnTest() {
		// Given: an EOM tactic whose media plan carries only the max-frequency cap
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 1_000_000, 0, 0, null, null, null, null,
				null, 1_000_000.0, null, null, 10.0,
				null, null, null,
				null, null, null
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), 1, 1, null
		);

		// When: the frequency is resolved
		Resolved freq = resolvers.resolveTacticFreq(1, List.of(), List.of(), data);

		// Then: it falls back to the max-frequency reduction unchanged
		assertThat(freq.value()).isEqualTo("9.00");
		assertThat(freq.label()).contains("max freq");
	}

	@Test
	void shouldIgnoreTheWeeklyFrequencyColumnForAnEocReportTest() {
		// Given: the same weekly figure on an EOC report, which has no reporting-month window
		Tactic tactic = new Tactic(
				"Display", "Display", null,
				0, 1_000_000, 0, 0, null, null, null, null,
				null, 1_000_000.0, null, null, 10.0,
				null, null, null,
				null, null, 1.0
		);
		CampaignData data = new CampaignData(
				null, null, null, null, null,
				new FlightDates(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null),
				Map.of(1, tactic), null
		);

		// When: the frequency is resolved
		Resolved freq = resolvers.resolveTacticFreq(1, List.of(), List.of(), data);

		// Then: EOC keeps deriving it from the max-frequency cap
		assertThat(freq.value()).isEqualTo("9.00");
	}

	@Test
	void freqFromWeeklyShouldBeDeterministicByTacticIndexTest() {
		// Given-When: the same tactic asks twice, and a neighbouring tactic asks once
		double first = tacticUtils.freqFromWeekly(1, 2.0, 4.0);
		double firstAgain = tacticUtils.freqFromWeekly(1, 2.0, 4.0);
		double second = tacticUtils.freqFromWeekly(2, 2.0, 4.0);

		// Then: the discount is stable per tactic (so freq and reach agree across Preview and Generate)
		// and always lands inside the 2-20% band
		assertThat(first).isEqualTo(firstAgain).isEqualTo(6.96); // 8.0 − 13%
		assertThat(second).isEqualTo(7.6); // 8.0 − 5%
		assertThat(first).isBetween(8.0 * 0.80, 8.0 * 0.98);
	}

	@Test
	void volumeCoefficient_resolvesExactKeywordAndDefault() {
		assertThat(tacticUtils.volumeCoefficient("Display")).isEqualTo(0.90);
		assertThat(tacticUtils.volumeCoefficient("CTV/OTT")).isEqualTo(0.70);
		assertThat(tacticUtils.volumeCoefficient("Open Exchange Display")).isEqualTo(0.90); // keyword fallback
		assertThat(tacticUtils.volumeCoefficient("Totally Unknown Channel")).isEqualTo(0.50); // default
	}

}
