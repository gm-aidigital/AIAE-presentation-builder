package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public ClaudeStrategic batchStrategic(CampaignData data, String brief) {
        return ClaudeStrategic.empty();
    }

    @Override
    public ClaudeTactical batchTactical(CampaignData data, String brief) {
        return ClaudeTactical.empty();
    }

    @Override
    public ClaudeResults batchResults(CampaignData data, String brief) {
        return ClaudeResults.empty();
    }

    @Override
    public String summarizeGeo(List<List<String>> geoRows) {
        return null;
    }
}
