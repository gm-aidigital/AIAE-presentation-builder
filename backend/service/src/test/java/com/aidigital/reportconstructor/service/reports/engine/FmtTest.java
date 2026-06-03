package com.aidigital.reportconstructor.service.reports.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FmtTest {

    private final Fmt fmt = new Fmt();

    @Test
    void intGroup_roundsAndGroups() {
        assertEquals("1,234,568", fmt.intGroup(1_234_567.8));
    }

    @Test
    void money_prefixesDollar() {
        assertEquals("$1,000", fmt.money(999.6));
    }

    @Test
    void pctOrDash_zeroIsEmDash() {
        assertEquals("\u2014", fmt.pctOrDash(0));
        assertEquals("12.34%", fmt.pctOrDash(12.34));
    }
}
