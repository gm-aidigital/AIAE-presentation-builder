package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;

import java.util.List;

/**
 * Abstraction over the Anthropic Claude calls the report engine makes:
 * Batch A (strategic), Batch B (tactical), Batch C (results) plus a small
 * Geo-tab summarisation call.
 *
 * <p>The real client is only registered when {@code ANTHROPIC_API_KEY}
 * is set; otherwise the stub client (the only candidate) is injected
 * and every batch returns empty (so resolvers fall back to {@code "—"}).
 */
public interface ClaudeClient {

	/**
	 * @return true when the client is hitting the real Anthropic API.
	 */
	boolean isLive();

	/**
	 * Batch A — audience age/segments, proposal overview, 4 strategic insights.
	 */
	ClaudeStrategic batchStrategic(CampaignData data, String brief);

	/**
	 * Batch B — per-tactic gender split + weekday/weekend peak windows.
	 */
	ClaudeTactical batchTactical(CampaignData data, String brief);

	/**
	 * Batch C — results overview, thoughts on performance, tactic overviews, plus the frequency
	 * narrative ({@code {{f_oppartunity}} / {{f_fact}} / {{f_storytelling}}}) seeded with {@code frequencies}.
	 *
	 * @param data        parsed campaign data and actual-vs-plan tactic metrics
	 * @param brief       free-text campaign brief used to infer the client's industry
	 * @param frequencies pre-computed planned/actual frequency figures embedded in the frequency narrative
	 * @return the parsed, length-capped Batch C copy
	 */
	ClaudeResults batchResults(CampaignData data, String brief, CampaignFrequencies frequencies);

	/**
	 * Geo-tab → short ≤40-char comma-separated location string (or null).
	 */
	String summarizeGeo(List<List<String>> geoRows);
}
