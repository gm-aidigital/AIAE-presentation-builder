package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
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

    private static GeneratePayload payloadWithRows(List<List<String>> sheet, List<List<String>> adj) {
        return new GeneratePayload("brief", "standard", sheet, adj, List.of(), List.of(), List.of(), List.of(), "");
    }

    private static CampaignData campaignWithTactic(int n) {
        return new CampaignData(
            "", "", "", "", "",
            new FlightDates(null, null),
            "", "", "", "", "",
            new CampaignData.Totals(0, 0, 0, 0, null, null),
            Map.of(n, new CampaignData.Tactic(
                "Display", "Display", "1",
                0, 0, 0, 0, null, null, null, null,
                null, null, null, null, null, null, null, null
            )),
            ""
        );
    }
}
