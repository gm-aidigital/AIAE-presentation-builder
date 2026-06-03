package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Pivot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChartSheetWriterTest {

    private final ChartSheetWriter writer = new ChartSheetWriter();

    @Test
    void dailyComboRateRatio_isDecimalNotPercentPoints() {
        assertThat(writer.dailyComboRateRatio(1000, 25)).isEqualTo(0.025);
        double legacyBugPercentPoints = (25.0 / 1000.0) * 100.0;
        assertThat(writer.dailyComboRateRatio(1000, 25)).isNotEqualTo(legacyBugPercentPoints);
    }

    @Test
    void shouldWriteDailyRateColumn_falseLeavesColumnCToTemplateFormula() {
        assertThat(writer.shouldWriteDailyRateColumn()).isFalse();
    }

    @Test
    void rateColumnValuesForPivot_emptyWhenTemplateOwnsFormula() {
        Pivot pivot = pivotRow(1000, 25, 0);
        assertThat(writer.rateColumnValuesForPivot(pivot, true)).isEmpty();
    }

    @Test
    void rateColumnValuesForPivot_whenForcedWriteUsesRatioNotTimes100() {
        Pivot pivot = pivotRow(1000, 25, 0);
        Optional<List<List<Object>>> rates = writer.rateColumnValuesForPivot(pivot, true, true);
        assertThat(rates).isPresent();
        assertThat(rates.get().getFirst().getFirst()).isEqualTo(0.025);
    }

    private static Pivot pivotRow(double imps, double clicks, double completions) {
        LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
        data.put("2026-01-01", new double[] {imps, clicks, completions});
        return new Pivot(data, clicks > 0, completions > 0);
    }
}
