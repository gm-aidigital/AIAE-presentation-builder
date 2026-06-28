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
		resolvers = new TacticResolvers(sheetUtils, new Fmt(), tacticUtils, new CampaignResolvers(sheetUtils,
				new Fmt(), tacticUtils));
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
	void volumeCoefficient_resolvesExactKeywordAndDefault() {
		assertThat(tacticUtils.volumeCoefficient("Display")).isEqualTo(0.90);
		assertThat(tacticUtils.volumeCoefficient("CTV/OTT")).isEqualTo(0.70);
		assertThat(tacticUtils.volumeCoefficient("Open Exchange Display")).isEqualTo(0.90); // keyword fallback
		assertThat(tacticUtils.volumeCoefficient("Totally Unknown Channel")).isEqualTo(0.50); // default
	}
}
