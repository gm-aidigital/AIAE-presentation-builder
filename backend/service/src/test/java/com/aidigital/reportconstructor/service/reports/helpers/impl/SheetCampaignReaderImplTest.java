package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetCampaignReaderImplTest {

	private final SheetCampaignReaderImpl reader = new SheetCampaignReaderImpl(new ReportNumberParserImpl());

	@Test
	void shouldReconstructCampaignContextFromPlaceholdersTest() {
		// Given: a sheet-read placeholder map for one tactic, with formatted numbers and one blank metric
		Map<String, String> flat = Map.ofEntries(
				Map.entry("{{client_name}}", "Acme"),
				Map.entry("{{Campaign_name}}", "Spring"),
				Map.entry("{{geo_locations}}", "Texas"),
				Map.entry("{{primary_kpis}}", "CTR"),
				Map.entry("{{total imps}}", "1,500,000"),
				Map.entry("{{total_investment}}", "$15,000"),
				Map.entry("{{tactic 1}}", "Display"),
				Map.entry("{{tactic 1 spend}}", "$10,000"),
				Map.entry("{{tactic 1 imps}}", "1,000,000"),
				Map.entry("{{tactic 1 ctr}}", "0.20%"),
				Map.entry("{{tactic 1 vcr}}", ""));

		// When: the campaign context is reconstructed for one tactic
		CampaignData data = reader.read(flat, 1);

		// Then: campaign-level and total fields parse, and the tactic's metrics parse with blanks as null
		assertThat(data.client()).isEqualTo("Acme");
		assertThat(data.campaign()).isEqualTo("Spring");
		assertThat(data.geo()).isEqualTo("Texas");
		assertThat(data.primaryKpis()).isEqualTo("CTR");
		assertThat(data.totals().imps()).isEqualTo(1_500_000.0);
		Tactic tactic = data.tactics().get(1);
		assertThat(tactic.name()).isEqualTo("Display");
		assertThat(tactic.spend()).isEqualTo(10_000.0);
		assertThat(tactic.imps()).isEqualTo(1_000_000.0);
		assertThat(tactic.ctr()).isEqualTo(0.20);
		assertThat(tactic.vcr()).isNull();
	}

	@Test
	void shouldClampTacticCountAndTolerateMissingValuesTest() {
		// Given: a nearly empty map and an over-range tactic count

		// When: reconstructed with a count above the template maximum
		CampaignData data = reader.read(Map.of("{{client_name}}", "Acme"), 30);

		// Then: the count is clamped to 28 and missing numbers default to zero without failing
		assertThat(data.tactics()).hasSize(28);
		assertThat(data.totals().spend()).isZero();
		assertThat(data.tactics().get(1).spend()).isZero();
		assertThat(data.tactics().get(1).name()).isEmpty();
	}

	@Test
	void shouldReconstructFrequenciesFromSheetWithoutRandomUpliftTest() {
		// Given: a sheet-read map carrying total impressions, campaign reach, the reviewed actual frequency
		// and the addressable market volume (stored compactly)
		Map<String, String> flat = Map.ofEntries(
				Map.entry("{{total imps}}", "1,000,000"),
				Map.entry("{{reach}}", "250,000"),
				Map.entry("{{reach_f}}", "3.50"),
				Map.entry("{{market volume}}", "1M"));

		// When: the frequency figures are reconstructed from the sheet
		CampaignFrequencies freq = reader.readFrequencies(flat);

		// Then: plan is ceil(imps/reach), fact is the sheet's own value, and reachFact/remaining derive from
		// them deterministically — no random uplift
		assertThat(freq.plan()).isEqualTo("4");
		assertThat(freq.fact()).isEqualTo("3.50");
		assertThat(freq.reachFact()).isEqualTo(1_000_000.0 / 3.5);
		assertThat(freq.remainingAudience()).isEqualTo(Math.max(1_000_000.0 - 1_000_000.0 / 3.5, 0));
	}

	@Test
	void shouldReturnEmptyFrequenciesWhenImpressionsOrReachMissingTest() {
		// Given: a map without the impressions/reach inputs frequency derivation needs

		// When: frequencies are reconstructed
		CampaignFrequencies freq = reader.readFrequencies(Map.of("{{reach_f}}", "3.50"));

		// Then: every field is null rather than a bogus figure
		assertThat(freq.plan()).isNull();
		assertThat(freq.fact()).isNull();
		assertThat(freq.reachFact()).isNull();
		assertThat(freq.remainingAudience()).isNull();
	}
}
