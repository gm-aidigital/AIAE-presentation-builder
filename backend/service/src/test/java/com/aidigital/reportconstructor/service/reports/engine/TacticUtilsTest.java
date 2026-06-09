package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticUtilsTest {

    private final TacticExtractionHelper tacticUtils = ReportsEngineTestSupport.tacticExtractionHelper();
    private final TacticCatalog catalog = ReportsEngineTestSupport.tacticCatalog();

    @Test
    void freqFromMax_isDeterministicByTacticIndex() {
        assertEquals(9.0, tacticUtils.freqFromMax(1, 10.0));
        assertEquals(9.6, tacticUtils.freqFromMax(2, 10.0));
    }

    @Test
    void getTacticKpiType_videoTacticsReturnVcr() {
        assertEquals("vcr", tacticUtils.getTacticKpiType("programmatic ctv"));
        assertEquals("ctr", tacticUtils.getTacticKpiType("programmatic display"));
    }

    @Test
    void extractTacticsFromMedia_stopsAtTotalsRow() {
        List<List<String>> rows = List.of(
            List.of("x", "Media", "y"),
            List.of("", "Programmatic Display", ""),
            List.of("", "Meta (CPM)", ""),
            List.of("Totals", "", "")
        );
        assertThat(tacticUtils.extractTacticsFromMedia(rows))
            .containsExactly("Programmatic Display", "Meta (CPM)");
    }

    @Test
    void normalizeTacticDisplayName_mapsCtvAlias() {
        assertThat(tacticUtils.normalizeTacticDisplayName("programmatic ctv")).isEqualTo("CTV");
        assertThat(catalog.displayFor("unknown tactic")).isEqualTo("unknown tactic");
    }

    @Test
    void countTacticsInMediaPlan_countsWhitelistMatchesOnly() {
        List<List<String>> rows = List.of(
            List.of("Media"),
            List.of("programmatic display"),
            List.of("not a tactic"),
            List.of("meta (cpm)")
        );
        assertThat(tacticUtils.countTacticsInMediaPlan(rows)).isEqualTo(2);
    }
}
