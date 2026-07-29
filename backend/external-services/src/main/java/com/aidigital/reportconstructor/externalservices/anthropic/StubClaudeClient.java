package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeNarrative;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusion;
import com.aidigital.reportconstructor.service.reports.dto.TacticConclusionInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughts;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
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

	private final ReportClaudeDefaults claudeDefaults;

	public StubClaudeClient(ReportClaudeDefaults claudeDefaults) {
		this.claudeDefaults = claudeDefaults;
	}

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public boolean perSectionCallsEnabled() {
		return false;
	}

	@Override
	public List<String> publisherSection(CampaignData data, PublisherObservationInput input, String brief) {
		// No live model: no publisher copy to generate, so the tactic falls back to blank fields.
		return List.of();
	}

	@Override
	public List<String> creativeSection(CampaignData data, CreativeTakeawayInput input, String brief) {
		// No live model: no creative copy to generate, so the tactic falls back to blank fields.
		return List.of();
	}

	@Override
	public List<String> geoSection(CampaignData data, GeoInsightInput input, String brief) {
		// No live model: no geo copy to generate, so the tactic falls back to blank fields.
		return List.of();
	}

	@Override
	public List<String> audienceSection(CampaignData data, AudienceInsightInput input, String brief) {
		// No live model: no audience copy to generate, so the tactic falls back to blank fields.
		return List.of();
	}

	@Override
	public List<String> deviceSection(CampaignData data, DeviceInsightInput input, String brief) {
		// No live model: no device copy to generate, so the tactic falls back to blank fields.
		return List.of();
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
	public ClaudeNarrative batchAlignNarrative(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief) {
		// No live model: there is nothing to align, so echo the inputs back unchanged.
		return new ClaudeNarrative(strategic, results);
	}

	@Override
	public List<TacticConclusion> batchTacticConclusions(
			CampaignData data, List<TacticConclusionInput> inputs, String brief) {
		// No live model: no conclusions to generate, so every tactic falls back to sheet values.
		return List.of();
	}

	@Override
	public List<TacticThoughts> batchTacticThoughts(List<TacticThoughtsInput> inputs, String brief) {
		// No live model: no thoughts to generate, so the thoughts slides render blank.
		return List.of();
	}

	@Override
	public ClaudeResults batchCampaignResults(
			CampaignData data, String brief, CampaignFrequencies frequencies, List<TacticNarrativeDigest> perTactic) {
		return claudeDefaults.emptyResults();
	}

	@Override
	public ClaudeNarrative batchAlignCampaign(
			ClaudeStrategic strategic, ClaudeResults results, List<String> breakdownDigest, String brief) {
		// No live model: there is nothing to align, so echo the inputs back unchanged.
		return new ClaudeNarrative(strategic, results);
	}

	@Override
	public String summarizeGeo(List<List<String>> geoRows) {
		return null;
	}

	@Override
	public String summarizeFunnelStages(List<String> tacticGoals) {
		return null;
	}

	@Override
	public String digestBrief(String brief) {
		// No live model: the caller falls back to the raw brief, exactly as it behaved before the digest step.
		return null;
	}

	@Override
	public String summarizePrimaryKpis(CampaignData data) {
		return null;
	}
}
