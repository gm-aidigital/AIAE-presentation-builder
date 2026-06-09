package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider.ChartRequest;
import com.google.api.client.http.HttpRequestInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Live {@link ChartProvider}: creates the per-request Google Drive/Sheets/Slides clients
 * (as the service account, or as the signed-in user when a user token is supplied) and
 * delegates the actual chart building and slide swapping to {@link TacticChartBuilder}.
 */
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealChartProvider implements ChartProvider {

    private final GoogleClientsFactory clients;
    private final TacticChartBuilder chartBuilder;

    public RealChartProvider(GoogleClientsFactory clients, TacticChartBuilder chartBuilder) {
        this.clients = clients;
        this.chartBuilder = chartBuilder;
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public List<String> buildCharts(ChartRequest req) {
        boolean asUser = req.userGoogleAccessToken() != null && !req.userGoogleAccessToken().isBlank();
        HttpRequestInitializer init = asUser
            ? clients.userInitializer(req.userGoogleAccessToken())
            : clients.serviceAccountInitializer();
        ChartClients chartClients = new ChartClients(
            clients.drive(init), clients.sheets(init), clients.slides(init));
        return chartBuilder.buildAllCharts(chartClients, req);
    }
}
