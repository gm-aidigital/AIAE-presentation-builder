package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.ReportsEngineTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderClaudeGateImplTest {

	private final PlaceholderClaudeGateImpl gate = ReportsEngineTestSupport.placeholderClaudeGate();

	@Test
	void shouldNeedStrategicWhenAudienceAgeMissingTest() {
		GeneratePayload payload = payloadWithRows(
				List.of(List.of("Campaign:", "Spring")),
				List.of()
		);

		assertThat(gate.needStrategic(payload)).isTrue();
	}

	@Test
	void shouldNotNeedStrategicWhenManualValuesPresentTest() {
		GeneratePayload payload = payloadWithRows(
				List.of(
						List.of("Audience age:", "25-34"),
						List.of("Audience segments:", "Sports fans"),
						List.of("Proposal overview:", "Overview"),
						List.of("Strategic point 1:", "P1"),
						List.of("Strategic overview 1:", "O1"),
						List.of("Strategic point 2:", "P2"),
						List.of("Strategic overview 2:", "O2"),
						List.of("Strategic point 3:", "P3"),
						List.of("Strategic overview 3:", "O3"),
						List.of("Strategic point 4:", "P4"),
						List.of("Strategic overview 4:", "O4")
				),
				List.of()
		);

		assertThat(gate.needStrategic(payload)).isFalse();
	}

	@Test
	void shouldNeedGeoSummaryWhenGeoTabReferencedTest() {
		GeneratePayload payload = payloadWithRows(
				List.of(
						List.of("Geo", "Markets"),
						List.of("See Geo tab for details", "")
				),
				List.of()
		);

		assertThat(gate.needGeoSummary(payload)).isTrue();
	}

	@Test
	void shouldNotNeedGeoSummaryWhenGeoLocationsManualTest() {
		GeneratePayload payload = payloadWithRows(
				List.of(List.of("Geo locations:", "NYC")),
				List.of()
		);

		assertThat(gate.needGeoSummary(payload)).isFalse();
	}

	@Test
	void shouldNeedTacticalWhenTacticMaleMissingTest() {
		GeneratePayload payload = payloadWithRows(List.of(), List.of());
		CampaignData data = campaignWithTactic(1);

		assertThat(gate.needTactical(payload, data)).isTrue();
	}

	@Test
	void shouldNeedResultsWhenOnlyFrequencyNarrativeMissingTest() {
		// Given: every Batch C manual value present EXCEPT the frequency-narrative labels (no tactics)
		List<List<String>> rows = new java.util.ArrayList<>(List.of(
				List.of("Our results overview:", "Overview"),
				List.of("Thoughts on the performance:", "T1 | T2 | T3 | T4")));
		for (int i = 1; i <= 4; i++) {
			rows.add(List.of("Recommendation " + i + ":", "R" + i));
			rows.add(List.of("Recommendation " + i + " text:", "R" + i + " text"));
		}
		GeneratePayload payload = payloadWithRows(rows, List.of());

		// When-Then: the missing frequency labels alone keep Batch C required
		assertThat(gate.needResults(payload, null)).isTrue();
	}

	@Test
	void shouldNotNeedResultsWhenFrequencyNarrativePresentTest() {
		// Given: every Batch C manual value present, including the three frequency-narrative labels
		List<List<String>> rows = new java.util.ArrayList<>(List.of(
				List.of("Our results overview:", "Overview"),
				List.of("Thoughts on the performance:", "T1 | T2 | T3 | T4"),
				List.of("Frequency opportunity:", "Opportunity copy"),
				List.of("Frequency fact:", "Fact copy"),
				List.of("Frequency storytelling:", "Storytelling copy")));
		for (int i = 1; i <= 4; i++) {
			rows.add(List.of("Recommendation " + i + ":", "R" + i));
			rows.add(List.of("Recommendation " + i + " text:", "R" + i + " text"));
		}
		GeneratePayload payload = payloadWithRows(rows, List.of());

		// When-Then:
		assertThat(gate.needResults(payload, null)).isFalse();
	}

	private static GeneratePayload payloadWithRows(List<List<String>> sheet, List<List<String>> adj) {
		return new GeneratePayload("brief", "standard", sheet, adj, List.of(), List.of(), List.of(), List.of(), "");
	}

	private static CampaignData campaignWithTactic(int n) {
		return new CampaignData(
				"", "", "", "", "",
				new FlightDates(null, null),
				"", "", "", "", "",
				new Totals(0, 0, 0, 0, null, null),
				Map.of(n, new Tactic(
						"Display", "Display", "1",
						0, 0, 0, 0, null, null, null, null,
						null, null, null, null, null, null, null, null
				)),
				""
		);
	}
}
