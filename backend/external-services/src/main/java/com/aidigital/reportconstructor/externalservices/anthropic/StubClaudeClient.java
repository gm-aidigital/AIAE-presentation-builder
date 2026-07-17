package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * No-op Claude client — the only candidate when {@code ANTHROPIC_API_KEY} is
 * unset and {@link RealClaudeClient} stays conditional-excluded.
 *
 * <p>Every batch returns the empty DTO (PHP returns {@code []} when no API key
 * is configured), so the resolvers fall back to manual/sheet values or
 * {@code "—"} — there are no fabricated AI insights.
 */
@Component
public class StubClaudeClient implements ClaudeClient {

	private final ReportClaudeDefaults claudeDefaults;

	public StubClaudeClient(ReportClaudeDefaults claudeDefaults) {
		this.claudeDefaults = claudeDefaults;
	}

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public ClaudeStrategic batchStrategic(CampaignData data, String brief) {
		return claudeDefaults.emptyStrategic();
	}

	@Override
	public ClaudeStrategic batchStrategicNarrative(CampaignData data, String brief) {
		return claudeDefaults.emptyStrategic();
	}

	@Override
	public ClaudeTactical batchTactical(CampaignData data, String brief) {
		return claudeDefaults.emptyTactical();
	}

	@Override
	public ClaudeSheetBatch batchSheet(CampaignData data, String brief) {
		return claudeDefaults.emptySheetBatch();
	}

	@Override
	public ClaudeResults batchResults(CampaignData data, String brief, CampaignFrequencies frequencies) {
		return claudeDefaults.emptyResults();
	}

	@Override
	public Map<Integer, List<String>> batchPublisherObservations(List<PublisherObservationInput> inputs, String brief) {
		return Map.of();
	}

	@Override
	public Map<Integer, List<String>> batchCreativeTakeaways(List<CreativeTakeawayInput> inputs, String brief) {
		return Map.of();
	}

	@Override
	public Map<Integer, List<String>> batchGeoInsights(List<GeoInsightInput> inputs, String brief) {
		return Map.of();
	}

	@Override
	public Map<Integer, List<String>> batchAudienceInsights(List<AudienceInsightInput> inputs, String brief) {
		return Map.of();
	}

	@Override
	public Map<Integer, List<String>> batchDeviceInsights(List<DeviceInsightInput> inputs, String brief) {
		return Map.of();
	}

	@Override
	public String summarizeGeo(List<List<String>> geoRows) {
		return null;
	}

	@Override
	public String summarizeFunnelStages(List<List<String>> geoRows) {
		return null;
	}

	@Override
	public String summarizePrimaryKpis(CampaignData data) {
		return null;
	}
}
