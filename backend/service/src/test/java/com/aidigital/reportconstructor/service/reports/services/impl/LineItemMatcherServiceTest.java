package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;
import com.aidigital.reportconstructor.service.reports.helpers.impl.LineItemNamingHelperImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemMatcherServiceTest {

    private final LineItemMatcherServiceImpl matcher =
        new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl());

    @Test
    void extractLineItemId_readsIndexEight() {
        assertThat(matcher.extractLineItemId("a_b_c_d_e_f_g_h_42_tail")).isEqualTo("42");
        assertThat(matcher.extractLineItemId("short_name")).isNull();
    }

    @Test
    void extractTactics_skipsStopPhrasesAndNonWhitelist() {
        List<List<String>> plan = List.of(
            List.of("Media", "Comments"),
            List.of("Total media", ""),
            List.of("Programmatic Display", "note"),
            List.of("Not A Real Tactic", ""),
            List.of("Meta (CPM)", "")
        );
        assertThat(matcher.extractTactics(plan)).containsExactly("Programmatic Display", "Meta (CPM)");
    }

    @Test
    void match_autoWhenChannelHasExactlyOneId() {
        List<List<String>> bq = List.of(
            List.of("Level 1 Naming", "Channel", "Tactic"),
            List.of("x_x_x_x_x_x_x_x_99_y", "Display", "t1"),
            List.of("x_x_x_x_x_x_x_x_88_y", "Display", "t2")
        );
        List<List<String>> plan = List.of(
            List.of("Media"),
            List.of("Programmatic Display")
        );
        LineItemMatcherService.MatchResult result = matcher.match(bq, plan);
        assertThat(result.tactics()).hasSize(1);
        assertThat(result.tactics().getFirst().confidence()).isEqualTo("none");
        assertThat(result.uniqueIds()).containsExactly("88", "99");
    }
}
