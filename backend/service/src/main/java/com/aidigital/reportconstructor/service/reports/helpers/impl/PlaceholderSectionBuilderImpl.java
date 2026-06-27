package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.engine.CampaignResolvers;
import com.aidigital.reportconstructor.service.reports.engine.Resolved;
import com.aidigital.reportconstructor.service.reports.engine.TacticResolvers;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderSectionBuilder;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring bean implementation of {@link PlaceholderSectionBuilder}.
 */
@Component
@RequiredArgsConstructor
public class PlaceholderSectionBuilderImpl implements PlaceholderSectionBuilder {

	private final CampaignResolvers campaignResolvers;
	private final TacticResolvers tacticResolvers;
	private final TacticExtractionHelper tacticExtraction;

	@Override
	public List<PreviewSection> buildSections(
			GeneratePayload payload,
			CampaignData data,
			ClaudeStrategic ccA,
			ClaudeTactical ccB,
			ClaudeResults ccC,
			String geoSummary,
			CampaignFrequencies frequencies
	) {
		List<List<String>> sheet = payload.sheetRows();
		List<List<String>> adj = payload.adjRows();
		String reportType = payload.reportType();
		List<String> mediaTactics = tacticExtraction.extractTacticsFromMedia(sheet);

		List<PreviewSection> sections = new ArrayList<>();

		Map<String, Resolved> start = new LinkedHashMap<>();
		start.put("{{client_name}}", campaignResolvers.resolve(sheet, adj, "Client name:"));
		start.put("{{Campaign_name}}", campaignResolvers.resolve(sheet, adj, "Campaign:"));
		if (reportType != null && !reportType.isBlank()) {
			start.put("{{report_type}}", new Resolved("Report type (UI)", reportType, "sheet"));
		} else {
			start.put("{{report_type}}", campaignResolvers.resolve(sheet, adj, "Report type:"));
		}
		start.put("{{flight_dates}}", campaignResolvers.resolveFlightDates(sheet, adj));
		start.put("{{total_investment}}", campaignResolvers.resolveTotalInvestment(sheet, adj, data));
		start.put("{{primary_kpis}}", campaignResolvers.resolvePrimaryKpis(sheet, adj));
		start.put("{{audience_age}}", campaignResolvers.resolveAudienceAge(sheet, adj, ccA.audienceAge()));
		start.put("{{audience_segments}}", campaignResolvers.resolveAudienceSegments(sheet, adj,
				ccA.audienceSegments()));
		start.put("{{market volume}}", campaignResolvers.resolveMarketVolume(payload.marketVolume(), sheet, adj));
		start.put("{{geo_locations}}", campaignResolvers.resolveGeoLocations(sheet, adj, geoSummary));
		start.put("{{funnel_stages}}", campaignResolvers.resolveFunnelStages(sheet, adj));
		start.put("{{tactics_list}}", campaignResolvers.resolveTacticsList(sheet, adj));
		sections.add(buildPreviewSection("Start", start));

		Map<String, Resolved> overview = new LinkedHashMap<>();
		overview.put("{{proposal overview}}", campaignResolvers.resolveProposalOverview(sheet, adj,
				ccA.proposalOverview()));
		overview.put("{{Our results overview}}", campaignResolvers.resolveResultsOverview(sheet, adj,
				ccC.resultsOverview()));
		overview.putAll(campaignResolvers.resolveThoughtsOnPerformance(sheet, adj, ccC.thoughtsOnPerformance()));
		sections.add(buildPreviewSection("Overview Slides", overview));

		sections.add(buildPreviewSection("Strategic Insights",
				campaignResolvers.resolveStrategicInsights(sheet, adj, ccA.strategicInsights())));

		Map<String, Resolved> totals = new LinkedHashMap<>();
		totals.put("{{reach}}", campaignResolvers.resolveReach(payload.estimatesRows(), sheet, adj));
		totals.put("{{reach_p}}", campaignResolvers.resolveReachShort(payload.estimatesRows(), sheet, adj));
		totals.put("{{reach_f}}", campaignResolvers.resolveReachFact(frequencies.reachFact(), sheet, adj));
		totals.put("{{reach_f_pres}}", campaignResolvers.resolveReachFactShort(frequencies.reachFact(), sheet, adj));
		totals.put("{{total imps}}", campaignResolvers.resolveTotalImps(sheet, adj, data));
		totals.put("{{total ctr}}", campaignResolvers.resolveTotalCtr(sheet, adj, data));
		totals.put("{{total vcr}}", campaignResolvers.resolveTotalVcr(sheet, adj, data));
		totals.put("{{total spend}}", campaignResolvers.resolveTotalInvestment(sheet, adj, data));
		sections.add(buildPreviewSection("Summary Metrics", totals));

		for (int n = 1; n <= 6; n++) {
			sections.add(buildPreviewSection("Tactic " + n,
					buildFullTacticSection(n, sheet, adj, data, ccB, ccC, mediaTactics, payload.marketVolume())));
		}
		sections.add(buildPreviewSection("Tactic 7",
				buildShortTacticSection(7, sheet, adj, data, ccB, ccC, mediaTactics)));

		sections.add(buildPreviewSection("Optimization Recommendations",
				campaignResolvers.resolveRecommendations(sheet, adj, ccC.recommendations())));

		Map<String, Resolved> frequency = new LinkedHashMap<>();
		frequency.put("{{f_oppartunity}}", campaignResolvers.resolveFOpportunity(sheet, adj, ccC.fOpportunity()));
		frequency.put("{{f_fact}}", campaignResolvers.resolveFFact(sheet, adj, ccC.fFact()));
		frequency.put("{{f_storytelling}}", campaignResolvers.resolveFStorytelling(sheet, adj, ccC.fStorytelling()));
		sections.add(buildPreviewSection("Frequency Story", frequency));

		return sections;
	}

	Map<String, Resolved> buildFullTacticSection(
			int n, List<List<String>> sheet, List<List<String>> adj, CampaignData data,
			ClaudeTactical ccB, ClaudeResults ccC, List<String> mediaTactics, String marketVolume
	) {
		Resolved info = resolveTacticName(n, sheet, adj, mediaTactics);
		String tacticName = info.value() == null ? "" : info.value();

		Map<String, Resolved> m = new LinkedHashMap<>();
		m.put("{{tactic " + n + "}}", info);
		m.put("{{tactic " + n + " goal}}", tacticResolvers.resolveTacticGoal(n, sheet, adj));
		m.put("{{tactic " + n + " overview}}", tacticResolvers.resolveTacticOverview(n, sheet, adj, ccC));
		m.put("{{tactic " + n + " spend}}", tacticResolvers.resolveTacticSpend(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " imps}}", tacticResolvers.resolveTacticImps(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " reach}}", tacticResolvers.resolveTacticReach(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr}}", tacticResolvers.resolveTacticCtr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " vcr}}", tacticResolvers.resolveTacticVcr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " volume}}",
				tacticResolvers.resolveTacticVolume(n, tacticName, marketVolume, sheet, adj));
		m.put("{{tactic " + n + " \u2013 bench}}", tacticResolvers.resolveTacticBench(n, tacticName, sheet, adj,
				data));
		m.put("{{tactic " + n + " male}}", tacticResolvers.resolveTacticGender(n, "male", sheet, adj, ccB));
		m.put("{{tactic " + n + " female}}", tacticResolvers.resolveTacticGender(n, "female", sheet, adj, ccB));
		m.put("{{tactic " + n + " f}}", tacticResolvers.resolveTacticFreq(n, sheet, adj, data));
		m.put("{{tactic " + n + " weekdays}}", tacticResolvers.resolveTacticDaypart(n, "weekdays", sheet, adj, ccB));
		m.put("{{tactic " + n + " weekends}}", tacticResolvers.resolveTacticDaypart(n, "weekends", sheet, adj, ccB));
		m.put("{{tactic " + n + " top creative name}}", tacticResolvers.resolveTacticTopCreativeName(n, sheet, adj,
				data));
		m.put("{{tactic " + n + " top creative imps}}", tacticResolvers.resolveTacticTopCreativeImps(n, sheet, adj,
				data));
		m.put("{{tactic " + n + " top creative clicks}}", tacticResolvers.resolveTacticTopCreativeClicks(n, sheet, adj
				, data));
		return m;
	}

	Map<String, Resolved> buildShortTacticSection(
			int n, List<List<String>> sheet, List<List<String>> adj, CampaignData data,
			ClaudeTactical ccB, ClaudeResults ccC, List<String> mediaTactics
	) {
		Resolved info = resolveTacticName(n, sheet, adj, mediaTactics);
		String tacticName = info.value() == null ? "" : info.value();

		Map<String, Resolved> m = new LinkedHashMap<>();
		m.put("{{tactic " + n + "}}", info);
		m.put("{{tactic " + n + " goal}}", tacticResolvers.resolveTacticGoal(n, sheet, adj));
		m.put("{{tactic " + n + " overview}}", tacticResolvers.resolveTacticOverview(n, sheet, adj, ccC));
		m.put("{{tactic " + n + " spend}}", tacticResolvers.resolveTacticSpend(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " imps}}", tacticResolvers.resolveTacticImps(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " reach}}", tacticResolvers.resolveTacticReach(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr}}", tacticResolvers.resolveTacticCtr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " vcr}}", tacticResolvers.resolveTacticVcr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " \u2013 bench}}", tacticResolvers.resolveTacticBench(n, tacticName, sheet, adj,
				data));
		m.put("{{tactic " + n + " male}}", tacticResolvers.resolveTacticGender(n, "male", sheet, adj, ccB));
		m.put("{{tactic " + n + " female}}", tacticResolvers.resolveTacticGender(n, "female", sheet, adj, ccB));
		m.put("{{tactic " + n + " f}}", tacticResolvers.resolveTacticFreq(n, sheet, adj, data));
		m.put("{{tactic " + n + " weekdays}}", tacticResolvers.resolveTacticDaypart(n, "weekdays", sheet, adj, ccB));
		m.put("{{tactic " + n + " weekends}}", tacticResolvers.resolveTacticDaypart(n, "weekends", sheet, adj, ccB));
		return m;
	}

	Resolved resolveTacticName(
			int n, List<List<String>> sheet, List<List<String>> adj, List<String> mediaTactics
	) {
		Resolved manual = campaignResolvers.resolve(sheet, adj, "Tactic " + n + ":");
		if (manual.found()) {
			return manual;
		}
		int idx = n - 1;
		if (mediaTactics != null && idx < mediaTactics.size()
				&& mediaTactics.get(idx) != null && !mediaTactics.get(idx).isEmpty()) {
			return new Resolved("Tactic " + n + " (auto: Media column)",
					tacticExtraction.normalizeTacticDisplayName(mediaTactics.get(idx)), "sheet");
		}
		return new Resolved("Tactic " + n + ":", null, "not_found");
	}

	PreviewSection buildPreviewSection(String title, Map<String, Resolved> entries) {
		List<Placeholder> phs = new ArrayList<>();
		for (Map.Entry<String, Resolved> e : entries.entrySet()) {
			Resolved r = e.getValue();
			phs.add(new Placeholder(e.getKey(), r.label(), r.value(), r.source()));
		}
		return new PreviewSection(title, phs);
	}
}
