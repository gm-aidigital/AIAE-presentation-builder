package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticResolversTest {

    private TacticResolvers resolvers;
    private TacticUtils tacticUtils;

    @BeforeEach
    void setUp() {
        SheetUtils sheetUtils = new SheetUtils();
        tacticUtils = new TacticUtils();
        resolvers = new TacticResolvers(sheetUtils, new Fmt(), tacticUtils, new CampaignResolvers(sheetUtils, new Fmt(), tacticUtils));
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
        CampaignData.Tactic tactic = new CampaignData.Tactic(
            "Display", "Display", null,
            0, 100_000, 0, 0, null, null, null, null,
            null, 50_000.0, null, null, 10.0,
            null, null, null
        );
        CampaignData data = new CampaignData(
            null, null, null, null, null, null, null, null, null, null, null,
            new CampaignData.Totals(0, 0, 0, 0, null, null),
            Map.of(1, tactic), null
        );
        Resolved r = resolvers.resolveTacticFreq(1, List.of(), List.of(), data);
        assertThat(r.source()).isEqualTo("adj");
        assertThat(r.value()).isEqualTo("9.00");
        assertThat(r.label()).contains("10%");
    }
}
