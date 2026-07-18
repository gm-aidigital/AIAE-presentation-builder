package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
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
	private final BreakdownChartBuilder breakdownChartBuilder;

	public RealChartProvider(
			GoogleClientsFactory clients, TacticChartBuilder chartBuilder,
			BreakdownChartBuilder breakdownChartBuilder) {
		this.clients = clients;
		this.chartBuilder = chartBuilder;
		this.breakdownChartBuilder = breakdownChartBuilder;
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public List<String> buildCharts(ChartRequest req) {
		return chartBuilder.buildAllCharts(chartClients(req.userGoogleAccessToken()), req);
	}

	@Override
	public List<String> buildBreakdownCharts(BreakdownChartRequest req) {
		return breakdownChartBuilder.buildBreakdownCharts(chartClients(req.userGoogleAccessToken()), req);
	}

	/**
	 * Builds the Drive/Sheets/Slides client bundle for one request, authenticated as the signed-in user
	 * when a token is supplied (so copies land in that user's Drive, matching where the deck was created)
	 * or as the service account otherwise.
	 *
	 * @param userGoogleAccessToken the signed-in user's Google token, or {@code null}/blank for the service account
	 * @return the three clients bundled for this request
	 */
	ChartClients chartClients(String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		HttpRequestInitializer init = asUser
				? clients.userInitializer(userGoogleAccessToken)
				: clients.serviceAccountInitializer();
		return new ChartClients(clients.drive(init), clients.sheets(init), clients.slides(init));
	}
}
