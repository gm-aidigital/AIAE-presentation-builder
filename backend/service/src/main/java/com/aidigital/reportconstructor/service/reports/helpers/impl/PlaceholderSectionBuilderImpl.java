package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.engine.CampaignResolvers;
import com.aidigital.reportconstructor.service.reports.engine.Resolved;
import com.aidigital.reportconstructor.service.reports.engine.TacticResolvers;
import com.aidigital.reportconstructor.service.reports.helpers.EffectiveTacticsHelper;
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

	/** Max tactics the deck template carries — one preview section per tactic slot. */
	private static final int MAX_TACTICS = 28;

	/** Em-dash written into the dayparting/gender tokens when their AI estimate is switched off. */
	private static final String DASH = "—"; // —

	private final CampaignResolvers campaignResolvers;
	private final TacticResolvers tacticResolvers;
	private final TacticExtractionHelper tacticExtraction;
	private final EffectiveTacticsHelper effectiveTactics;

	@Override
	public List<PreviewSection> buildSections(
			GeneratePayload payload,
			CampaignData data,
			ClaudeStrategic ccA,
			ClaudeTactical ccB,
			ClaudeResults ccC,
			String primaryKpis,
			String geoSummary,
			String funnelSummary,
			String briefDigest,
			CampaignFrequencies frequencies,
			int tacticCount
	) {
		List<List<String>> sheet = payload.sheetRows();
		List<List<String>> adj = payload.adjRows();
		String reportType = payload.reportType();
		// The tactics the report covers, not every row the plan carries: rows the user dropped at
		// matching time must not name a slide or appear in the campaign tactics list.
		List<String> mediaTactics = effectiveTactics.effectiveTactics(sheet, payload.lineItemMapping())
				.stream().map(PlanTactic::name).toList();

		List<PreviewSection> sections = new ArrayList<>();

		Map<String, Resolved> start = new LinkedHashMap<>();
		start.put("{{client_name}}", campaignResolvers.resolve(sheet, adj, "Client name:"));
		start.put("{{Campaign_name}}", campaignResolvers.resolve(sheet, adj, "Campaign:"));
		if (reportType != null && !reportType.isBlank()) {
			start.put("{{report_type}}", new Resolved("Report type (UI)", reportType, "sheet"));
		} else {
			start.put("{{report_type}}", campaignResolvers.resolve(sheet, adj, "Report type:"));
		}
		start.put("{{flight_dates}}", flightDatesResolved(data));
		start.put("{{total_investment}}", campaignResolvers.resolveTotalInvestment(sheet, adj, data));
		start.put("{{primary_kpis}}", campaignResolvers.resolvePrimaryKpis(sheet, adj, primaryKpis));
		start.put("{{audience_age}}", campaignResolvers.resolveAudienceAge(sheet, adj, ccA.audienceAge()));
		start.put("{{audience_segments}}", campaignResolvers.resolveAudienceSegments(sheet, adj,
				ccA.audienceSegments()));
		start.put("{{market volume}}", campaignResolvers.resolveMarketVolume(payload.marketVolume(), sheet, adj));
		start.put("{{geo_locations}}", campaignResolvers.resolveGeoLocations(sheet, adj, geoSummary));
		start.put("{{funnel_stages}}", campaignResolvers.resolveFunnelStages(sheet, adj, funnelSummary));
		start.put("{{tactics_list}}", campaignResolvers.resolveTacticsList(sheet, adj, mediaTactics));
		// The digest — not the raw brief — is what the sheet carries, so the slides-from-sheet step reads
		// back the same condensed context every batch of this step already ran on. It falls back to the raw
		// brief when Claude is stubbed or the digest call failed.
		String rfpBrief = briefDigest == null || briefDigest.isBlank() ? payload.brief() : briefDigest;
		start.put("{{RFP info}}", campaignResolvers.resolveRfpInfo(sheet, adj, rfpBrief));
		start.put("{{change log}}", campaignResolvers.resolveChangeLog(sheet, adj, payload.changeLog()));
		sections.add(buildPreviewSection("Start", start));

		Map<String, Resolved> overview = new LinkedHashMap<>();
		overview.put("{{proposal overview}}", campaignResolvers.resolveProposalOverview(sheet, adj,
				ccA.proposalOverview()));
		overview.putAll(campaignResolvers.resolveResultsOverviews(sheet, adj, ccC.resultsOverviews()));
		overview.putAll(campaignResolvers.resolveThoughtsOnPerformance(sheet, adj, ccC.thoughtsOnPerformance()));
		sections.add(buildPreviewSection("Overview Slides", overview));

		sections.add(buildPreviewSection("Strategic Insights",
				campaignResolvers.resolveStrategicInsights(sheet, adj, ccA.strategicInsights())));

		Map<String, Resolved> totals = new LinkedHashMap<>();
		// The reach the frequency was computed from, so every reach placeholder shows the same number.
		totals.put("{{reach}}", campaignResolvers.resolveReach(payload.estimatesRows(), sheet, adj,
				frequencies.reachPlan()));
		totals.put("{{reach_p}}", campaignResolvers.resolveReachShort(payload.estimatesRows(), sheet, adj,
				frequencies.reachPlan()));
		totals.put("{{reach_f}}", campaignResolvers.resolveReachFact(frequencies.reachFact(), sheet, adj));
		totals.put("{{reach_f_pres}}", campaignResolvers.resolveReachFactShort(frequencies.reachFact(), sheet, adj));
		totals.put("{{total imps}}", campaignResolvers.resolveTotalImps(sheet, adj, data));
		totals.put("{{total ctr}}", campaignResolvers.resolveTotalCtr(sheet, adj, data));
		totals.put("{{total vcr}}", campaignResolvers.resolveTotalVcr(sheet, adj, data));
		totals.put("{{total spend}}", campaignResolvers.resolveTotalInvestment(sheet, adj, data));

		// EOM-only pacing: only meaningful once the user has entered the flight-months total at matching
		// time, so an EOC report (or an EOM request with no flight-months-total entered) never picks up
		// these tokens.
		boolean eomPeriod = "EOM".equals(reportType) && data != null
				&& data.eomMonthNumber() != null && data.eomFlightMonthsTotal() != null;
		if (eomPeriod) {
			totals.put("{{total imps plan ctd}}", campaignResolvers.resolveTotalImpsPlanCtd(sheet, adj, data));
			totals.put("{{total imps pace}}", campaignResolvers.resolveTotalImpsPace(sheet, adj, data));
			totals.put("{{total_investment_plan_ctd}}",
					campaignResolvers.resolveTotalInvestmentPlanCtd(sheet, adj, data));
			totals.put("{{total_investment_pace}}", campaignResolvers.resolveTotalInvestmentPace(sheet, adj, data));
			totals.put("{{campaign pace status}}", campaignResolvers.resolveCampaignPaceStatus(sheet, adj, data));
			totals.put("{{eom_month_number}}", campaignResolvers.resolveEomMonthNumber(sheet, adj, data));
			totals.put("{{eom_flight_months_total}}", campaignResolvers.resolveEomFlightMonthsTotal(sheet, adj, data));
			totals.put("{{eom_report_month}}", campaignResolvers.resolveEomReportMonth(sheet, adj, data));
			totals.put("{{eom_next_month_number}}", campaignResolvers.resolveEomNextMonthNumber(sheet, adj, data));
			totals.put("{{eom_next_report_month}}", campaignResolvers.resolveEomNextReportMonth(sheet, adj, data));
		}
		sections.add(buildPreviewSection("Summary Metrics", totals));

		// A null flag means the caller predates the toggle, so the AI estimate stays on; only an explicit
		// FALSE switches dayparting/gender off and forces those tokens to a dash.
		boolean estimateDaypartGender = !Boolean.FALSE.equals(payload.estimateDaypartGender());
		int tacticLimit = Math.clamp(tacticCount, 1, MAX_TACTICS);
		for (int n = 1; n <= tacticLimit; n++) {
			sections.add(buildPreviewSection("Tactic " + n,
					buildFullTacticSection(n, sheet, adj, data, ccB, ccC, mediaTactics, payload.marketVolume(),
							estimateDaypartGender, eomPeriod, payload.lineItemMapping())));
		}

		sections.add(buildPreviewSection("Optimization Recommendations",
				campaignResolvers.resolveRecommendations(sheet, adj, ccC.recommendations())));

		Map<String, Resolved> frequency = new LinkedHashMap<>();
		frequency.put("{{f_oppartunity}}", campaignResolvers.resolveFOpportunity(sheet, adj, ccC.fOpportunity()));
		frequency.put("{{f_fact}}", campaignResolvers.resolveFFact(sheet, adj, ccC.fFact()));
		frequency.put("{{f_storytelling}}", campaignResolvers.resolveFStorytelling(sheet, adj, ccC.fStorytelling()));
		sections.add(buildPreviewSection("Frequency Story", frequency));

		return sections;
	}

	/**
	 * Builds the full placeholder map for one tactic slide. When {@code estimateDaypartGender} is
	 * {@code false} the four dayparting/gender tokens are forced to an em-dash instead of being resolved
	 * from the sheet or Claude, because those metrics are not always tracked reliably on the DSP side.
	 *
	 * @param n                     one-based tactic index
	 * @param sheet                 Media Plan grid rows
	 * @param adj                   manual Adjustments grid rows
	 * @param data                  aggregated campaign data for computed fallbacks
	 * @param ccB                   Claude Batch B per-tactic gender/daypart copy
	 * @param ccC                   Claude Batch C results copy (tactic overview)
	 * @param mediaTactics          tactic display names extracted from the Media column
	 * @param marketVolume          raw market-volume string used to derive tactic volume
	 * @param estimateDaypartGender whether the dayparting/gender tokens may be estimated ({@code false}
	 *                              forces them to a dash)
	 * @param eomPeriod             whether the EOM pacing tokens (plan ctd / proj / vs goal / cpm) should be
	 *                              resolved for this tactic, i.e. an EOM report with a flight-months-total entered
	 * @param lineItemMapping       the tactic-to-line-item mapping carrying the EOM rate type / unit price
	 *                              entered in the matching step
	 * @return the token-to-{@link Resolved} map for this tactic slide
	 */
	Map<String, Resolved> buildFullTacticSection(
			int n, List<List<String>> sheet, List<List<String>> adj, CampaignData data,
			ClaudeTactical ccB, ClaudeResults ccC, List<String> mediaTactics, String marketVolume,
			boolean estimateDaypartGender, boolean eomPeriod, List<LineItemMapping> lineItemMapping
	) {
		Resolved info = resolveTacticName(n, sheet, adj, mediaTactics);
		String tacticName = info.value() == null ? "" : info.value();

		Map<String, Resolved> m = new LinkedHashMap<>();
		m.put("{{tactic " + n + "}}", info);
		Resolved goal = tacticResolvers.resolveTacticGoal(n, sheet, adj);
		m.put("{{tactic " + n + " goal}}", goal);
		// The EOM funnel-stage badge is the exact same Media Plan "Goal" column value the EOC "goal"
		// token already reads — same data, same per-tactic row alignment, just a second slide slot.
		m.put("{{tactic " + n + " funnel stage}}", goal);
		m.put("{{tactic " + n + " overview}}", tacticResolvers.resolveTacticOverview(n, sheet, adj, ccC));
		m.put("{{tactic " + n + " spend}}", tacticResolvers.resolveTacticSpend(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " spend plan}}",
				tacticResolvers.resolveTacticSpendPlan(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " imps}}", tacticResolvers.resolveTacticImps(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " imps plan}}",
				tacticResolvers.resolveTacticImpsPlan(n, tacticName, sheet, adj, data));
		// The EOM summary table's "Unit rate" / "Rate type" columns: the price and buying model the user
		// entered per tactic in the matching step. Their template tokens are {{unit N rate}} and
		// {{rate type N}}, not "{{tactic N …}}" tokens.
		m.put("{{unit " + n + " rate}}", tacticResolvers.resolveTacticUnitRate(n, sheet, adj, lineItemMapping));
		m.put("{{rate type " + n + "}}", tacticResolvers.resolveTacticRateType(n, sheet, adj, lineItemMapping));
		m.put("{{tactic " + n + " reach}}", tacticResolvers.resolveTacticReach(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr}}", tacticResolvers.resolveTacticCtr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " ctr plan}}", tacticResolvers.resolveTacticCtrPlan(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " vcr}}", tacticResolvers.resolveTacticVcr(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " vcr plan}}", tacticResolvers.resolveTacticVcrPlan(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " clicks}}", tacticResolvers.resolveTacticClicks(n, sheet, adj, data));
		m.put("{{tactic " + n + " completions}}", tacticResolvers.resolveTacticCompletions(n, sheet, adj, data));
		m.put("{{tactic " + n + " clicks plan}}", tacticResolvers.resolveTacticClicksPlan(n, sheet, adj, data));
		m.put("{{tactic " + n + " completions plan}}",
				tacticResolvers.resolveTacticCompletionsPlan(n, sheet, adj, data));
		m.put("{{tactic " + n + " KPI type}}", tacticResolvers.resolveTacticKpiType(n, tacticName, sheet, adj));
		m.put("{{tactic " + n + " KPI}}", tacticResolvers.resolveTacticKpi(n, tacticName, sheet, adj, data));
		m.put("{{tactic " + n + " volume}}",
				tacticResolvers.resolveTacticVolume(n, tacticName, marketVolume, sheet, adj));
		m.put("{{tactic " + n + " \u2013 bench}}", tacticResolvers.resolveTacticBench(n, tacticName, sheet, adj,
				data));
		m.put("{{tactic " + n + " male}}", estimateDaypartGender
				? tacticResolvers.resolveTacticGender(n, "male", sheet, adj, ccB)
				: daypartGenderOff(n, "male"));
		m.put("{{tactic " + n + " female}}", estimateDaypartGender
				? tacticResolvers.resolveTacticGender(n, "female", sheet, adj, ccB)
				: daypartGenderOff(n, "female"));
		m.put("{{tactic " + n + " f}}", tacticResolvers.resolveTacticFreq(n, sheet, adj, data));
		m.put("{{tactic " + n + " weekdays}}", estimateDaypartGender
				? tacticResolvers.resolveTacticDaypart(n, "weekdays", sheet, adj, ccB)
				: daypartGenderOff(n, "weekdays"));
		m.put("{{tactic " + n + " weekends}}", estimateDaypartGender
				? tacticResolvers.resolveTacticDaypart(n, "weekends", sheet, adj, ccB)
				: daypartGenderOff(n, "weekends"));
		m.put("{{tactic " + n + " top creative name}}", tacticResolvers.resolveTacticTopCreativeName(n, sheet, adj,
				data));
		m.put("{{tactic " + n + " top creative imps}}", tacticResolvers.resolveTacticTopCreativeImps(n, sheet, adj,
				data));
		m.put("{{tactic " + n + " top creative clicks}}", tacticResolvers.resolveTacticTopCreativeClicks(n, sheet, adj
				, data));
		if (eomPeriod) {
			m.putAll(buildTacticPacingSection(n, sheet, adj, data));
		}
		return m;
	}

	/**
	 * Builds the EOM-only pacing tokens for tactic {@code n}: the prorated to-date goal, pace-based
	 * projection and variance for each metric, plus the actual CPM (a metric that never existed in EOC).
	 *
	 * @param n     one-based tactic index
	 * @param sheet Media Plan grid rows
	 * @param adj   manual Adjustments grid rows
	 * @param data  aggregated campaign data carrying the elapsed/total month counts and to-date actuals
	 * @return the pacing token-to-{@link Resolved} map for this tactic
	 */
	Map<String, Resolved> buildTacticPacingSection(int n, List<List<String>> sheet, List<List<String>> adj,
	                                               CampaignData data) {
		Map<String, Resolved> m = new LinkedHashMap<>();
		m.put("{{tactic " + n + " imps plan ctd}}", tacticResolvers.resolveTacticImpsPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " imps proj}}", tacticResolvers.resolveTacticImpsProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " imps vs goal}}", tacticResolvers.resolveTacticImpsVsGoal(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr plan ctd}}", tacticResolvers.resolveTacticCtrPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr proj}}", tacticResolvers.resolveTacticCtrProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " ctr vs goal}}", tacticResolvers.resolveTacticCtrVsGoal(n, sheet, adj, data));
		m.put("{{tactic " + n + " vcr plan ctd}}", tacticResolvers.resolveTacticVcrPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " vcr proj}}", tacticResolvers.resolveTacticVcrProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " vcr vs goal}}", tacticResolvers.resolveTacticVcrVsGoal(n, sheet, adj, data));
		m.put("{{tactic " + n + " completions plan ctd}}",
				tacticResolvers.resolveTacticCompletionsPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " completions proj}}",
				tacticResolvers.resolveTacticCompletionsProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " completions vs goal}}",
				tacticResolvers.resolveTacticCompletionsVsGoal(n, sheet, adj, data));
		m.put("{{tactic " + n + " reach plan ctd}}", tacticResolvers.resolveTacticReachPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " reach proj}}", tacticResolvers.resolveTacticReachProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " reach vs goal}}", tacticResolvers.resolveTacticReachVsGoal(n, sheet, adj, data));
		m.put("{{tactic " + n + " spend plan ctd}}", tacticResolvers.resolveTacticSpendPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " spend pace}}", tacticResolvers.resolveTacticSpendPace(n, sheet, adj, data));
		m.put("{{tactic " + n + " cpm}}", tacticResolvers.resolveTacticCpm(n, sheet, adj, data));
		m.put("{{tactic " + n + " cpm plan ctd}}", tacticResolvers.resolveTacticCpmPlanCtd(n, sheet, adj, data));
		m.put("{{tactic " + n + " cpm proj}}", tacticResolvers.resolveTacticCpmProj(n, sheet, adj, data));
		m.put("{{tactic " + n + " cpm vs goal}}", tacticResolvers.resolveTacticCpmVsGoal(n, sheet, adj, data));
		return m;
	}

	/**
	 * Builds the resolved entry used for a dayparting/gender token when its AI estimate is switched off:
	 * a hard em-dash that bypasses the sheet, Adjustments and Claude alike, tagged {@code adj} so the
	 * preview treats it as an intentionally resolved value rather than a missing one.
	 *
	 * @param n    one-based tactic index, used only to build the human-readable label
	 * @param part the token suffix ({@code male}, {@code female}, {@code weekdays} or {@code weekends})
	 * @return a {@link Resolved} carrying the em-dash for this token
	 */
	Resolved daypartGenderOff(int n, String part) {
		return new Resolved("Tactic " + n + " " + part + ": (estimate off)", DASH, "adj");
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

	/**
	 * Builds the {@code {{flight_dates}}} value from the report's confirmed date window, which the
	 * collector derives from the user-confirmed raw-data date filter (never the media plan).
	 *
	 * @param data the collected campaign data carrying the formatted flight-date range
	 * @return a resolved flight-date entry sourced from the raw data, or a {@code not_found} entry
	 */
	Resolved flightDatesResolved(CampaignData data) {
		String value = data == null ? null : data.flightDates();
		if (value == null || value.isBlank()) {
			return new Resolved("Raw-data date range (confirmed)", null, "not_found");
		}
		return new Resolved("Raw-data date range (confirmed)", value, "adj");
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
