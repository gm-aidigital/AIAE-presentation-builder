package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload.LineItemMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticLineItemGrouperTest {

    private final TacticLineItemGrouper grouper = new TacticLineItemGrouper();

    @Test
    void groupByTactic_groupsIdsByTacticNumberPreservingOrder() {
        Map<Integer, List<String>> grouped = grouper.groupByTactic(List.of(
            new LineItemMapping("Display", "100", 1),
            new LineItemMapping("Display", "101", 1),
            new LineItemMapping("Video", "200", 2)
        ));
        assertThat(grouped.get(1)).containsExactly("100", "101");
        assertThat(grouped.get(2)).containsExactly("200");
    }

    @Test
    void groupByTactic_skipsNonPositiveTacticNumberAndBlankId() {
        Map<Integer, List<String>> grouped = grouper.groupByTactic(List.of(
            new LineItemMapping("Zero", "300", 0),
            new LineItemMapping("NullNum", "400", null),
            new LineItemMapping("BlankId", "  ", 3),
            new LineItemMapping("Good", " 500 ", 4)
        ));
        assertThat(grouped).containsOnlyKeys(4);
        assertThat(grouped.get(4)).containsExactly("500");
    }

    @Test
    void groupByTactic_nullMappingReturnsEmpty() {
        assertThat(grouper.groupByTactic(null)).isEmpty();
    }
}
