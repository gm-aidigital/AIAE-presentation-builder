package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Headers;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Pivot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPivotTest {

    private ChartPivot pivot;

    @BeforeEach
    void setUp() {
        pivot = new ChartPivot(new SheetUtils());
    }

    @Test
    void parseBqHeaders_findsDateImpsClicksL1Naming() {
        List<List<String>> rows = List.of(
            List.of("foo", "bar"),
            List.of("Date", "Impressions", "Clicks", "Level 1 Naming")
        );
        Headers h = pivot.parseBqHeaders(rows);
        assertThat(h.valid()).isTrue();
        assertThat(h.dateCol()).isZero();
        assertThat(h.impsCol()).isEqualTo(1);
        assertThat(h.clicksCol()).isEqualTo(2);
        assertThat(h.l1NamingCol()).isEqualTo(3);
    }

    @Test
    void extractLiIdFromL1Naming_usesNinthSegment() {
        String naming = "a_b_c_d_e_f_g_h_12345_x";
        assertThat(pivot.extractLiIdFromL1Naming(naming)).isEqualTo("12345");
        assertThat(pivot.extractLiIdFromL1Naming("a_b_c")).isEmpty();
    }

    @Test
    void num_stripsCommasAndNonNumeric() {
        assertThat(pivot.num("1,234.5")).isEqualTo(1234.5);
        assertThat(pivot.num("n/a")).isZero();
    }

    @Test
    void buildDailyPivot_filtersByLineItemAndFlightWindow() {
        List<List<String>> rows = List.of(
            List.of("Date", "Impressions", "Clicks", "Level 1 Naming"),
            List.of("2026-03-01", "100", "5", "x_x_x_x_x_x_x_x_11111_y"),
            List.of("2026-03-02", "200", "10", "x_x_x_x_x_x_x_x_22222_y"),
            List.of("2026-03-15", "999", "99", "x_x_x_x_x_x_x_x_11111_y")
        );
        Headers h = pivot.parseBqHeaders(rows);
        FlightDates flight = new FlightDates(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        Pivot daily = pivot.buildDailyPivot(rows, List.of("11111"), h, flight);

        assertThat(daily.hasClicks()).isTrue();
        assertThat(daily.data()).hasSize(1);
        assertThat(daily.data().get("2026-03-01")[0]).isEqualTo(100.0);
        assertThat(daily.data().get("2026-03-01")[1]).isEqualTo(5.0);
    }

    @Test
    void buildMonthlyPivot_multiYearLabelsIncludeYear() {
        List<List<String>> rows = List.of(
            List.of("Date", "Impressions", "Completions", "Level 1 Naming"),
            List.of("2025-12-15", "50", "2", "x_x_x_x_x_x_x_x_99_y"),
            List.of("2026-01-10", "60", "3", "x_x_x_x_x_x_x_x_99_y")
        );
        Headers h = pivot.parseBqHeaders(rows);
        Pivot monthly = pivot.buildMonthlyPivot(rows, List.of("99"), h, null, true);

        assertThat(monthly.hasCompletions()).isTrue();
        assertThat(monthly.data().keySet()).containsExactly("December 2025", "January 2026");
    }

    @Test
    void isMultiYear_usesFlightDatesWhenPresent() {
        FlightDates flight = new FlightDates(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 1));
        assertThat(pivot.isMultiYear(List.of(), new Headers(0, 1, -1, -1, -1), flight)).isTrue();
    }
}
