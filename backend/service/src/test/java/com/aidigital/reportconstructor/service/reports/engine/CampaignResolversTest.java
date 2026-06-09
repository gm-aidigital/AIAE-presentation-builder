package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
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
            new CampaignData.Totals(0, 5000, 0, 0, null, null),
            Map.of(), null
        );
        Resolved r = resolvers.resolveTotalImps(List.of(), List.of(), data);
        assertThat(r.source()).isEqualTo("adj");
        assertThat(r.value()).isEqualTo("5,000");
    }

    private static List<List<String>> labelRow(String label, String value) {
        return List.of(List.of(label, value, "", ""));
    }
}
