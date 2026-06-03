package com.aidigital.reportconstructor.service.reports.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticUtilsTest {

    private final TacticUtils tacticUtils = new TacticUtils();

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
}
