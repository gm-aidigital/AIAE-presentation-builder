package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider.ChartRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-op chart provider — the only candidate when no {@code @Primary} real chart
 * bean is registered (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON} is unset and
 * {@link RealChartProvider} stays conditional-excluded). Returns no errors so the
 * offline generation flow stays end-to-end runnable; the deck simply keeps its
 * empty chart placeholders.
 */
@Component
public class StubChartProvider implements ChartProvider {

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public List<String> buildCharts(ChartRequest request) {
        return List.of();
    }
}
