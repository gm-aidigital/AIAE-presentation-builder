package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticInsight;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-tactic metric resolvers. Each resolver follows the same source priority:
 * manual Adjustments (adj) → Media Plan (sheet) → computed/Claude → not_found.
 */
@Component
public class TacticResolvers {

	private final SheetRowHelper sheetUtils;
	private final Fmt fmt;
	private final TacticExtractionHelper tacticExtraction;
	private final CampaignResolvers campaignResolvers;
	private final RatePlanCalculator pacing;

	/**
	 * Creates the resolver wiring the collaborators used to look up, format and auto-derive
	 * per-tactic metric values.
	 *
	 * @param sheetUtils        helper that scans Media Plan / Adjustments grids for a labelled value
	 * @param fmt               number/percentage formatter for report display values
	 * @param tacticExtraction  tactic-specific helpers (KPI-type lookup, frequency math)
	 * @param campaignResolvers shared adj-then-sheet manual resolver reused for gender/daypart rows
	 * @param pacing            EOM proration/projection math shared by the "plan ctd" / "proj" / "vs goal" resolvers
	 */
	public TacticResolvers(
			SheetRowHelper sheetUtils, Fmt fmt,
			TacticExtractionHelper tacticExtraction, CampaignResolvers campaignResolvers, RatePlanCalculator pacing) {
		this.sheetUtils = sheetUtils;
		this.fmt = fmt;
		this.tacticExtraction = tacticExtraction;
		this.campaignResolvers = campaignResolvers;
		this.pacing = pacing;
	}

	private static final String DASH = "\u2014"; // —
	private static final double VOLUME_RANDOM_MIN = 0.90;
	private static final double VOLUME_RANDOM_MAX = 1.10;

	/**
	 * Resolves the spend value for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then the BigQuery-derived cost formatted as a dollar amount.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N spend:"} lookup label
	 * @param tacticName display name of the tactic (unused for spend; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the BigQuery-derived tactic cost fallback
	 * @return the resolved spend with its source tag, or a {@code not_found} placeholder when no value exists
	 */
	public Resolved resolveTacticSpend(int n, String tacticName, List<List<String>> sheetRows,
	                                   List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " spend:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t != null && t.spend() > 0) {
			return new Resolved(label + " (auto: BQ Cost)", "$" + fmt.intGroup(t.spend()), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the delivered impressions for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the matching grouped BigQuery count. Always literal
	 * impressions regardless of the tactic's EOM rate type — {@link #resolveTacticClicks} and
	 * {@link #resolveTacticCompletions} carry the bought unit (clicks/completions) for a CPC/CPV
	 * tactic separately, so this token never has to stand in for either.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N imps:"} lookup label
	 * @param tacticName display name of the tactic (unused for impressions; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the BigQuery-derived impressions fallback
	 * @return the resolved impressions with its source tag, or a {@code not_found} placeholder when no value exists
	 */
	public Resolved resolveTacticImps(int n, String tacticName, List<List<String>> sheetRows,
	                                  List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " imps:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t != null && t.imps() > 0) {
			return new Resolved(label + " (auto: BQ Impressions)", fmt.intGroup(t.imps()), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned spend target for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then the Estimates "Total Cost" figure joined onto the tactic.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N spend plan:"} lookup label
	 * @param tacticName display name of the tactic (unused for spend plan; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the Estimates-derived planned spend fallback
	 * @return the resolved planned spend with its source tag, or a {@code not_found} placeholder when no value exists
	 */
	public Resolved resolveTacticSpendPlan(int n, String tacticName, List<List<String>> sheetRows,
	                                       List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " spend plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planSpend = t == null ? null : t.planSpend();
		if (planSpend != null && planSpend > 0) {
			return new Resolved(label + " (auto: Estimates Total Cost)", fmt.moneyExact(planSpend), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned impressions target for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the Estimates-tab/rate-derived plan. Always literal
	 * impressions: for a CPC/CPV EOM tactic {@link CampaignDataCollector} backs this figure out of the
	 * planned clicks/completions and the Estimates CTR/VCR benchmark rather than leaving it unset, so
	 * every tactic's "Impressions Plan" carries a real number regardless of how it was bought.
	 * {@link #resolveTacticClicksPlan} and {@link #resolveTacticCompletionsPlan} carry the bought unit
	 * itself for a CPC/CPV tactic.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N imps plan:"} lookup label
	 * @param tacticName display name of the tactic (unused for impressions plan; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the planned-impressions fallback
	 * @return the resolved planned impressions with its source tag, or a {@code not_found} placeholder
	 * when no value exists
	 */
	public Resolved resolveTacticImpsPlan(int n, String tacticName, List<List<String>> sheetRows,
	                                      List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " imps plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planImps = t == null ? null : t.planImps();
		if (planImps != null && planImps > 0) {
			return new Resolved(label + " (auto: plan impressions)", fmt.intGroup(planImps), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned clicks target for tactic {@code n} — the bought unit for a CPC EOM tactic —
	 * preferring a manual Adjustments override, then the Media Plan sheet, then the rate/budget-derived
	 * plan.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N clicks plan:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned-clicks fallback
	 * @return the resolved planned clicks with its source tag, or a {@code not_found} placeholder when
	 * the tactic is not CPC-rated or carries no planned clicks figure
	 */
	public Resolved resolveTacticClicksPlan(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String label = "Tactic " + n + " clicks plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planClicks = t == null ? null : t.planClicks();
		if (planClicks != null && planClicks > 0) {
			return new Resolved(label + " (auto: plan clicks, from CPC rate/budget)", fmt.intGroup(planClicks), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned completions (video/audio views) target for tactic {@code n} — the bought
	 * unit for a CPV EOM tactic — preferring a manual Adjustments override, then the Media Plan sheet,
	 * then the rate/budget-derived plan.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N completions plan:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned-completions fallback
	 * @return the resolved planned completions with its source tag, or a {@code not_found} placeholder
	 * when the tactic is not CPV-rated or carries no planned completions figure
	 */
	public Resolved resolveTacticCompletionsPlan(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                             CampaignData data) {
		String label = "Tactic " + n + " completions plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planViews = t == null ? null : t.planViews();
		if (planViews != null && planViews > 0) {
			return new Resolved(label + " (auto: plan completions, from CPV rate/budget)",
					fmt.intGroup(planViews), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned click-through rate for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the Estimates "CTR" figure joined onto the tactic.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N ctr plan:"} lookup label
	 * @param tacticName display name of the tactic (unused for ctr plan; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the Estimates-derived planned CTR fallback
	 * @return the resolved planned CTR percentage with its source tag, or a {@code not_found} placeholder
	 * when no value exists
	 */
	public Resolved resolveTacticCtrPlan(int n, String tacticName, List<List<String>> sheetRows,
	                                     List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " ctr plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planCtr = t == null ? null : t.planCtr();
		if (planCtr != null) {
			return new Resolved(label + " (auto: Estimates CTR)", fmt.dec2(planCtr) + "%", "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the planned video-completion rate for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the Estimates "VCR" figure joined onto the tactic.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N vcr plan:"} lookup label
	 * @param tacticName display name of the tactic (unused for vcr plan; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the Estimates-derived planned VCR fallback
	 * @return the resolved planned VCR percentage with its source tag, or a {@code not_found} placeholder
	 * when no value exists
	 */
	public Resolved resolveTacticVcrPlan(int n, String tacticName, List<List<String>> sheetRows,
	                                     List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " vcr plan:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planVcr = t == null ? null : t.planVcr();
		if (planVcr != null) {
			return new Resolved(label + " (auto: Estimates VCR)", Math.round(planVcr) + "%", "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the achieved click count for tactic {@code n} (from the Elevate raw-data export),
	 * preferring a manual Adjustments override, then the Media Plan sheet, then the aggregated
	 * BigQuery click total for the tactic.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N clicks:"} lookup label
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the BigQuery-derived click total fallback
	 * @return the resolved click count with its source tag, or a {@code not_found} placeholder when no value exists
	 */
	public Resolved resolveTacticClicks(int n, List<List<String>> sheetRows,
	                                    List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " clicks:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t != null && t.clicks() > 0) {
			return new Resolved(label + " (auto: Elevate raw-data Clicks)", fmt.intGroup(t.clicks()), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the achieved completion count for tactic {@code n} (from the Elevate raw-data export),
	 * preferring a manual Adjustments override, then the Media Plan sheet, then the aggregated
	 * BigQuery completion total for the tactic; a present-but-completion-less tactic yields an
	 * em-dash placeholder.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N completions:"} lookup label
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the BigQuery-derived completion total fallback
	 * @return the resolved completion count with its source tag, an em-dash when the tactic exists but
	 * has no completions, or a {@code not_found} placeholder when the tactic is missing
	 */
	public Resolved resolveTacticCompletions(int n, List<List<String>> sheetRows,
	                                         List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " completions:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t != null && t.completions() > 0) {
			return new Resolved(label + " (auto: Elevate raw-data Completions)", fmt.intGroup(t.completions()),
					"adj");
		}
		if (t != null) {
			return new Resolved(label + " (auto: no completions)", DASH, "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the performance benchmark for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the planned CTR or VCR estimate chosen by the
	 * tactic's KPI type.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N benchmark:"} lookup label
	 * @param tacticName display name of the tactic, used to derive the KPI type (CTR vs VCR)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the planned CTR/VCR estimate fallback
	 * @return the resolved benchmark string with its source tag, or a {@code not_found} placeholder
	 * when neither the KPI type nor a planned estimate yields a value
	 */
	public Resolved resolveTacticBench(int n, String tacticName, List<List<String>> sheetRows,
	                                   List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " benchmark:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}

		String kpiType = tacticExtraction.getTacticKpiType(tacticName);
		Tactic t = tactic(data, n);
		if ("ctr".equals(kpiType)) {
			Double val = t == null ? null : t.planCtr();
			if (val != null) {
				return new Resolved(label + " (auto: Estimates CTR)", "CTR \u2013 " + fmt.dec2(val) + "%", "adj");
			}
			return new Resolved(label, null, "not_found");
		}
		if ("vcr".equals(kpiType)) {
			Double val = t == null ? null : t.planVcr();
			if (val != null) {
				String rate = tacticExtraction.getCompletionRateLabel(tacticName);
				return new Resolved(label + " (auto: Estimates " + rate + ")",
						rate + " \u2013 " + Math.round(val) + "%", "adj");
			}
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the achieved click-through rate for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the BigQuery-derived clicks/impressions ratio.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N CTR:"} lookup label
	 * @param tacticName display name of the tactic (unused for CTR; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the computed clicks/impressions CTR fallback
	 * @return the resolved CTR percentage with its source tag, or a {@code not_found} placeholder
	 * when no value exists
	 */
	public Resolved resolveTacticCtr(int n, String tacticName, List<List<String>> sheetRows,
	                                 List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " CTR:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double ctr = t == null ? null : t.ctr();
		if (ctr != null) {
			return new Resolved(label + " (auto: Clicks/Imps)", fmt.pctOrDash(ctr), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the video completion rate for tactic {@code n}, preferring a manual Adjustments
	 * override, then the Media Plan sheet, then the BigQuery-derived completions/impressions ratio;
	 * a present-but-completion-less tactic yields an em-dash placeholder.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N VCR:"} lookup label
	 * @param tacticName display name of the tactic (unused for VCR; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the computed completions/impressions VCR fallback
	 * @return the resolved VCR percentage with its source tag, an em-dash when the tactic exists but
	 * has no completions, or a {@code not_found} placeholder when the tactic is missing
	 */
	public Resolved resolveTacticVcr(int n, String tacticName, List<List<String>> sheetRows,
	                                 List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " VCR:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double vcr = t == null ? null : t.vcr();
		if (vcr != null) {
			return new Resolved(label + " (auto: Completions/Imps)", fmt.pctOrDash(vcr), "adj");
		}
		if (t != null) {
			return new Resolved(label + " (auto: no completions)", DASH, "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the KPI-type label for tactic {@code n} — {@code "CTR"} for click-led tactics,
	 * {@code "VCR"} for video/completion-led tactics — derived from the tactic name via the
	 * {@link TacticExtractionHelper#getTacticKpiType(String) channel mapping}, with manual
	 * Adjustments / Media Plan overrides taking precedence.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N KPI type:"} lookup label
	 * @param tacticName display name of the tactic, used to derive the KPI type (CTR vs VCR)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @return the resolved {@code "CTR"}/{@code "VCR"} label with its source tag, or a {@code not_found}
	 * placeholder when the tactic name maps to neither KPI type
	 */
	public Resolved resolveTacticKpiType(int n, String tacticName, List<List<String>> sheetRows,
	                                     List<List<String>> adjRows) {
		String label = "Tactic " + n + " KPI type:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		String kpiType = tacticExtraction.getTacticKpiType(tacticName);
		if ("ctr".equals(kpiType)) {
			return new Resolved(label + " (auto: tactic mapping)", "CTR", "adj");
		}
		if ("vcr".equals(kpiType)) {
			return new Resolved(label + " (auto: tactic mapping)",
					tacticExtraction.getCompletionRateLabel(tacticName), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the unified KPI value for tactic {@code n}: the achieved CTR for click-led tactics or
	 * the achieved VCR for video/completion-led tactics, chosen by the tactic-name KPI mapping. CTR is
	 * rendered with two decimals ({@code x.xx%}) and VCR with one decimal ({@code xx.x%}). Both rates are
	 * computed over the flight window (clicks/impressions or completions/impressions × 100) by the campaign
	 * aggregation. Manual Adjustments / Media Plan overrides take precedence.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N KPI:"} lookup label
	 * @param tacticName display name of the tactic, used to derive the KPI type (CTR vs VCR)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the computed CTR/VCR rate for the tactic
	 * @return the resolved {@code xx.x%} KPI value with its source tag, or a {@code not_found}
	 * placeholder when the mapped rate is not computable
	 */
	public Resolved resolveTacticKpi(int n, String tacticName, List<List<String>> sheetRows,
	                                 List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " KPI:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		String kpiType = tacticExtraction.getTacticKpiType(tacticName);
		Tactic t = tactic(data, n);
		if ("vcr".equals(kpiType)) {
			Double vcr = t == null ? null : t.vcr();
			if (vcr != null) {
				return new Resolved(label + " (auto: Completions/Imps)", fmt.pct1(vcr), "adj");
			}
			return new Resolved(label, null, "not_found");
		}
		if ("ctr".equals(kpiType)) {
			Double ctr = t == null ? null : t.ctr();
			if (ctr != null) {
				return new Resolved(label + " (auto: Clicks/Imps)", fmt.dec2(ctr) + "%", "adj");
			}
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the average frequency for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then a value derived from the planned max frequency (the auto label
	 * notes the percentage reduction applied below that cap).
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N f:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned max-frequency estimate used for the fallback
	 * @return the resolved frequency with its source tag, or a {@code not_found} placeholder when no
	 * positive planned max frequency exists
	 */
	public Resolved resolveTacticFreq(int n, List<List<String>> sheetRows,
	                                  List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " f:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double maxFreq = t == null ? null : t.planMaxFreq();
		if (maxFreq != null && maxFreq > 0) {
			double freq = tacticExtraction.freqFromMax(n, maxFreq);
			int pct = (int) Math.round((1 - freq / maxFreq) * 100);
			return new Resolved(label + " (auto: Estimates max freq \u2212 " + pct + "%)", fmt.dec2(freq), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the unique reach for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then a value computed as impressions divided by the derived
	 * frequency (falling back to planned impressions when actuals are missing).
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N reach:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data supplying frequency, actual impressions and planned impressions
	 * @return the resolved reach with its source tag, or a {@code not_found} placeholder when the
	 * frequency or impression inputs are non-positive
	 */
	public Resolved resolveTacticReach(int n, List<List<String>> sheetRows,
	                                   List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " reach:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double maxFreq = t == null ? null : t.planMaxFreq();
		if (maxFreq == null || maxFreq <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double freq = tacticExtraction.freqFromMax(n, maxFreq);
		if (freq <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double imps = t.imps();
		if (imps <= 0) {
			imps = t.planImps() == null ? 0.0 : t.planImps();
		}
		if (imps <= 0) {
			return new Resolved(label, null, "not_found");
		}
		long reach = Math.round(imps / freq);
		return new Resolved(label + " (auto: imps / freq)", fmt.intGroup(reach), "adj");
	}

	/**
	 * Resolves the campaign goal for tactic {@code n}: an Adjustments override wins, otherwise the
	 * Media Plan "Media"/"Goal" table is located, the n-th tactic row is read up to known stop-word
	 * rows, and its raw Goal cell is normalised to a canonical upper-case label
	 * (for example {@code "Consideration & Engagement"} maps to {@code "CONSIDERATION"}).
	 *
	 * @param n         one-based tactic index used both for the lookup label and to pick the n-th table row
	 * @param sheetRows Media Plan grid rows scanned for the Media/Goal header and tactic rows
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @return the resolved canonical goal label with its source tag, or a {@code not_found}
	 * placeholder when the table, the row or its Goal cell cannot be found
	 */
	public Resolved resolveTacticGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows) {

		String label = "Tactic " + n + " goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}

		int mediaRowIdx = -1;
		int mediaColIdx = -1;
		int goalColIdx = -1;
		for (int i = 0; i < sheetRows.size(); i++) {
			List<String> row = sheetRows.get(i);
			if (row == null) {
				continue;
			}
			boolean hasMedia = false;
			int mCol = -1;
			int gCol = -1;
			for (int j = 0; j < row.size(); j++) {
				String v = cell(row, j).toLowerCase(Locale.ROOT);
				if (v.equals("media")) {
					hasMedia = true;
					mCol = j;
				}
				if (v.equals("goal")) {
					gCol = j;
				}
			}
			if (hasMedia && mCol >= 0) {
				mediaRowIdx = i;
				mediaColIdx = mCol;
				goalColIdx = gCol;
				break;
			}
		}
		if (mediaRowIdx < 0 || goalColIdx < 0) {
			return new Resolved(label, null, "not_found");
		}

		String[] stopWords = {"added value", "totals", "please note", "total:"};
		List<List<String>> tacticRows = new ArrayList<>();
		for (int i = mediaRowIdx + 1; i < sheetRows.size(); i++) {
			List<String> row = sheetRows.get(i);
			String c = cellAt(row, mediaColIdx);
			String rowText = joinLower(row, 4);
			boolean stop = false;
			for (String sw : stopWords) {
				if (rowText.contains(sw)) {
					stop = true;
					break;
				}
			}
			if (stop) {
				break;
			}
			// Skip section-label rows (empty Media cell) and added-value/reporting
			// description rows (non-tactic Media cell) so the n-th tactic row stays
			// aligned with the Media-column tactic extraction.
			if (c.isEmpty() || !tacticExtraction.isKnownTactic(c)) {
				continue;
			}
			tacticRows.add(row);
		}

		if (n - 1 >= tacticRows.size()) {
			return new Resolved(label, null, "not_found");
		}
		List<String> tacticRow = tacticRows.get(n - 1);
		String rawGoal = cellAt(tacticRow, goalColIdx);
		if (rawGoal.isEmpty()) {
			return new Resolved(label, null, "not_found");
		}

		String key = rawGoal.toLowerCase(Locale.ROOT);
		String mapped = switch (key) {
			case "awareness" -> "AWARENESS";
			case "consideration & engagement" -> "CONSIDERATION";
			case "conversions", "conversion" -> "CONVERSIONS";
			case "website traffic" -> "WEBSITE TRAFFIC";
			default -> rawGoal.toUpperCase(Locale.ROOT);
		};
		return new Resolved(label + " (auto: Proposal Goal column)", mapped, "sheet");
	}

	/**
	 * Resolves the narrative overview for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then the Claude-generated overview text keyed by tactic index.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N overview:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param cc        Claude generation results whose per-tactic overview map provides the fallback copy
	 * @return the resolved overview text with its source tag, or a {@code not_found} placeholder when
	 * no manual, sheet or Claude value is available
	 */
	public Resolved resolveTacticOverview(int n, List<List<String>> sheetRows,
	                                      List<List<String>> adjRows, ClaudeResults cc) {
		String label = "Tactic " + n + " overview:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		String generated = cc == null || cc.tacticOverviews() == null ? null : cc.tacticOverviews().get(n);
		if (generated != null) {
			return new Resolved(label + " (auto: Claude)", generated, "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the name of the top-performing creative for tactic {@code n}, preferring a manual
	 * Adjustments override, then the Media Plan sheet, then the creative with the most BigQuery
	 * impressions.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N top creative name:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the BigQuery top-creative name fallback
	 * @return the resolved creative name with its source tag, or a {@code not_found} placeholder when
	 * no value exists
	 */
	public Resolved resolveTacticTopCreativeName(int n, List<List<String>> sheetRows,
	                                             List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " top creative name:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		String val = t == null ? null : t.topCreativeName();
		if (val != null) {
			return new Resolved(label + " (auto: BQ top imps)", val, "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the impression count of the top-performing creative for tactic {@code n}, preferring
	 * a manual Adjustments override, then the Media Plan sheet, then the grouped BigQuery
	 * top-creative impressions.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N top creative imps:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the BigQuery top-creative impression count fallback
	 * @return the resolved impression count with its source tag, or a {@code not_found} placeholder
	 * when no value exists
	 */
	public Resolved resolveTacticTopCreativeImps(int n, List<List<String>> sheetRows,
	                                             List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " top creative imps:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double val = t == null ? null : t.topCreativeImps();
		if (val != null) {
			return new Resolved(label + " (auto: BQ top imps)", fmt.intGroup(val), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the click count of the top-performing creative for tactic {@code n}, preferring a
	 * manual Adjustments override, then the Media Plan sheet, then the grouped BigQuery
	 * top-creative clicks.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N top creative clicks:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the BigQuery top-creative click count fallback
	 * @return the resolved click count with its source tag, or a {@code not_found} placeholder when
	 * no value exists
	 */
	public Resolved resolveTacticTopCreativeClicks(int n, List<List<String>> sheetRows,
	                                               List<List<String>> adjRows, CampaignData data) {
		String label = "Tactic " + n + " top creative clicks:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double val = t == null ? null : t.topCreativeClicks();
		if (val != null) {
			return new Resolved(label + " (auto: BQ top creative)", fmt.intGroup(val), "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the audience-gender split percentage for tactic {@code n}; {@code gender} is
	 * {@code "male"} or {@code "female"}; adj → sheet → Claude Batch B.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N <gender>:"} lookup label
	 * @param gender    the split to return, either {@code "male"} or {@code "female"}
	 * @param sheetRows Media Plan grid rows searched (via the shared manual resolver) for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param ccB       Claude Batch B tactical insights supplying the per-tactic male/female percentages
	 * @return the resolved gender percentage with its source tag, or a {@code not_found} placeholder
	 * when neither manual nor Claude data is available
	 */
	public Resolved resolveTacticGender(int n, String gender, List<List<String>> sheetRows,
	                                    List<List<String>> adjRows, ClaudeTactical ccB) {
		String label = "Tactic " + n + " " + gender + ":";
		Resolved manual = campaignResolvers.resolve(sheetRows, adjRows, label);
		if (manual.found()) {
			return manual;
		}
		TacticInsight ti = ccB == null ? null : ccB.get(n);
		if (ti != null) {
			int val = "male".equals(gender) ? ti.male() : ti.female();
			return new Resolved(label + " (auto: Claude)", val + "%", "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the day-part performance text for tactic {@code n}; {@code part} is
	 * {@code "weekdays"} or {@code "weekends"}; adj → sheet → Claude Batch B.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N <part>:"} lookup label
	 * @param part      the day-part to return, either {@code "weekdays"} or {@code "weekends"}
	 * @param sheetRows Media Plan grid rows searched (via the shared manual resolver) for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param ccB       Claude Batch B tactical insights supplying the per-tactic weekdays/weekends copy
	 * @return the resolved day-part text with its source tag, or a {@code not_found} placeholder when
	 * neither manual nor Claude data is available
	 */
	public Resolved resolveTacticDaypart(int n, String part, List<List<String>> sheetRows,
	                                     List<List<String>> adjRows, ClaudeTactical ccB) {
		String label = "Tactic " + n + " " + part + ":";
		Resolved manual = campaignResolvers.resolve(sheetRows, adjRows, label);
		if (manual.found()) {
			return manual;
		}
		TacticInsight ti = ccB == null ? null : ccB.get(n);
		if (ti != null) {
			String val = "weekdays".equals(part) ? ti.weekdays() : ti.weekends();
			if (val != null) {
				return new Resolved(label + " (auto: Claude)", val, "adj");
			}
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the maximum addressable-audience volume for tactic {@code n}, preferring a manual
	 * Adjustments override, then the Media Plan sheet, then a value derived from the channel
	 * coefficient. The auto value is {@code coefficient × random(0.90–1.10) × market volume},
	 * clamped to never exceed {@code {{market volume}}} (a channel cannot reach more than the whole
	 * addressable market), and rendered in compact {@code xxxK / xM} notation.
	 *
	 * @param n            one-based tactic index used to build the {@code "Tactic N volume:"} lookup label
	 * @param tacticName   the {@code {{tactic n}}} display name, used to pick the channel coefficient
	 * @param marketVolume the raw audience-volume string entered in the UI (may be {@code null} or blank)
	 * @param sheetRows    Media Plan grid rows searched for the labelled value and the market-volume override
	 * @param adjRows      manual Adjustments grid rows that take precedence over the sheet
	 * @return the resolved compact volume with its source tag, or a {@code not_found} placeholder when no
	 * market volume is available
	 */
	public Resolved resolveTacticVolume(int n, String tacticName, String marketVolume,
	                                    List<List<String>> sheetRows, List<List<String>> adjRows) {
		String label = "Tactic " + n + " volume:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Double marketVolumeNum = campaignResolvers.numericMarketVolume(marketVolume, sheetRows, adjRows);
		if (marketVolumeNum == null || marketVolumeNum <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double coefficient = tacticExtraction.volumeCoefficient(tacticName);
		double raw = coefficient * volumeMultiplier() * marketVolumeNum;
		double volume = Math.min(raw, marketVolumeNum);
		return new Resolved(label + " (auto: coef × rnd × market volume)", fmt.compact(volume), "adj");
	}

	/**
	 * Returns the per-report random multiplier applied to the channel coefficient, drawn fresh on every
	 * call so tactic volumes vary from deck to deck rather than repeating a fixed proportion.
	 *
	 * @return a factor in {@code [0.90, 1.10)}
	 */
	double volumeMultiplier() {
		return ThreadLocalRandom.current().nextDouble(VOLUME_RANDOM_MIN, VOLUME_RANDOM_MAX);
	}

	Tactic tactic(CampaignData data, int n) {

		Map<Integer, Tactic> tactics = data == null ? null : data.tactics();
		return tactics == null ? null : tactics.get(n);
	}

	/**
	 * Reads the {@code [elapsedMonths, totalMonths]} pair EOM pacing prorates by, shared by every
	 * "plan ctd" / "proj" / "vs goal" resolver below.
	 *
	 * @param data campaign data whose {@code eomMonthNumber()}/{@code eomFlightMonthsTotal()} carry the pair
	 * @return {@code [elapsedMonths, totalMonths]}, or {@code null} for EOC (or an EOM report with no
	 * flight-months-total entered)
	 */
	int[] elapsedAndTotalMonths(CampaignData data) {
		if (data == null || data.eomMonthNumber() == null || data.eomFlightMonthsTotal() == null) {
			return null;
		}
		return new int[]{data.eomMonthNumber(), data.eomFlightMonthsTotal()};
	}

	/**
	 * Resolves the prorated to-date impressions goal for tactic {@code n}, scaled by elapsedMonths /
	 * totalMonths, preferring a manual override. Always literal impressions — see
	 * {@link #resolveTacticImpsPlan} for why a CPC/CPV tactic still carries one.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N imps plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the full-flight plan and the elapsed/total month counts
	 * @return the resolved prorated goal with its source tag, or a {@code not_found} placeholder when EOC,
	 * no flight-months-total was entered, or no planned impressions figure is available
	 */
	public Resolved resolveTacticImpsPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                         CampaignData data) {
		String label = "Tactic " + n + " imps plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		Double planImps = t == null ? null : t.planImps();
		if (months == null || planImps == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planImps, months[0], months[1]);
		return new Resolved(label + " (auto: plan impressions × elapsed/total months)",
				fmt.intGroup(planCtd), "adj");
	}

	/**
	 * Resolves the pace-based end-of-flight impressions projection for tactic {@code n}: the to-date
	 * actual impressions run-rate extrapolated across the whole flight, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N imps proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual and the elapsed/total month counts
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when
	 * unavailable
	 */
	public Resolved resolveTacticImpsProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String label = "Tactic " + n + " imps proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		if (months == null || t == null) {
			return new Resolved(label, null, "not_found");
		}
		double proj = pacing.projection(t.imps(), months[0], months[1]);
		return new Resolved(label + " (auto: to-date run-rate × total months)", fmt.intGroup(proj), "adj");
	}

	/**
	 * Resolves the impressions variance of tactic {@code n}'s to-date actual against its prorated
	 * to-date goal, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N imps vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the plan, the to-date actual and the elapsed/total month counts
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticImpsVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String label = "Tactic " + n + " imps vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		Double planImps = t == null ? null : t.planImps();
		if (months == null || t == null || planImps == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planImps, months[0], months[1]);
		String variance = pacing.paceVariance(t.imps(), planCtd, false);
		if (variance == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual vs prorated goal)", variance, "adj");
	}

	/**
	 * Resolves the prorated to-date CTR goal for tactic {@code n}. Click-through rate is a target
	 * percentage rather than a volume, so it does not scale with elapsed time: the to-date goal is the
	 * same as the full-flight planned CTR, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N ctr plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the full-flight planned CTR
	 * @return the resolved goal with its source tag, or a {@code not_found} placeholder when no planned
	 * CTR is available
	 */
	public Resolved resolveTacticCtrPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String label = "Tactic " + n + " ctr plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planCtr = t == null ? null : t.planCtr();
		if (planCtr == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: Estimates CTR, rate target unaffected by elapsed time)",
				fmt.dec2(planCtr) + "%", "adj");
	}

	/**
	 * Resolves the projected end-of-flight CTR for tactic {@code n}. A rate metric's projection is
	 * simply the to-date observed rate carried forward (rates don't accumulate the way volumes do),
	 * preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N ctr proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual CTR
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when no
	 * to-date actual CTR is available
	 */
	public Resolved resolveTacticCtrProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     CampaignData data) {
		String label = "Tactic " + n + " ctr proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double ctr = t == null ? null : t.ctr();
		if (ctr == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual rate held flat)", fmt.dec2(ctr) + "%", "adj");
	}

	/**
	 * Resolves the CTR variance of tactic {@code n}'s to-date actual against its planned CTR (in
	 * percentage points), preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N ctr vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned CTR and the to-date actual CTR
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticCtrVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {
		String label = "Tactic " + n + " ctr vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planCtr = t == null ? null : t.planCtr();
		Double ctr = t == null ? null : t.ctr();
		if (planCtr == null || ctr == null) {
			return new Resolved(label, null, "not_found");
		}
		String variance = pacing.paceVariance(ctr, planCtr, true);
		return new Resolved(label + " (auto: to-date actual vs planned rate)", variance, "adj");
	}

	/**
	 * Resolves the prorated to-date VCR goal for tactic {@code n}; a rate target, unaffected by elapsed
	 * time, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N vcr plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the full-flight planned VCR
	 * @return the resolved goal with its source tag, or a {@code not_found} placeholder when no planned
	 * VCR is available
	 */
	public Resolved resolveTacticVcrPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String label = "Tactic " + n + " vcr plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planVcr = t == null ? null : t.planVcr();
		if (planVcr == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: Estimates VCR, rate target unaffected by elapsed time)",
				Math.round(planVcr) + "%", "adj");
	}

	/**
	 * Resolves the projected end-of-flight VCR for tactic {@code n}, carrying the to-date observed rate
	 * forward, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N vcr proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual VCR
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when no
	 * to-date actual VCR is available
	 */
	public Resolved resolveTacticVcrProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     CampaignData data) {
		String label = "Tactic " + n + " vcr proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double vcr = t == null ? null : t.vcr();
		if (vcr == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual rate held flat)", Math.round(vcr) + "%", "adj");
	}

	/**
	 * Resolves the VCR variance of tactic {@code n}'s to-date actual against its planned VCR (in
	 * percentage points), preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N vcr vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned VCR and the to-date actual VCR
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticVcrVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {
		String label = "Tactic " + n + " vcr vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		Double planVcr = t == null ? null : t.planVcr();
		Double vcr = t == null ? null : t.vcr();
		if (planVcr == null || vcr == null) {
			return new Resolved(label, null, "not_found");
		}
		String variance = pacing.paceVariance(vcr, planVcr, true);
		return new Resolved(label + " (auto: to-date actual vs planned rate)", variance, "adj");
	}

	/**
	 * Resolves the prorated to-date completions goal for tactic {@code n}, derived as planned
	 * impressions × planned VCR (no direct Estimates-tab completions figure exists), scaled by
	 * elapsedMonths / totalMonths, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N completions plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned impressions/VCR and the elapsed/total month counts
	 * @return the resolved prorated goal with its source tag, or a {@code not_found} placeholder when the
	 * inputs are unavailable
	 */
	public Resolved resolveTacticCompletionsPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                                CampaignData data) {
		String label = "Tactic " + n + " completions plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double planCompletions = planCompletions(data, n);
		if (months == null || planCompletions == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planCompletions, months[0], months[1]);
		return new Resolved(label + " (auto: planned imps × VCR, prorated)", fmt.intGroup(planCtd), "adj");
	}

	/**
	 * Resolves the pace-based end-of-flight completions projection for tactic {@code n}, preferring a
	 * manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N completions proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual completions and the elapsed/total month counts
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when
	 * unavailable
	 */
	public Resolved resolveTacticCompletionsProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                             CampaignData data) {
		String label = "Tactic " + n + " completions proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		if (months == null || t == null) {
			return new Resolved(label, null, "not_found");
		}
		double proj = pacing.projection(t.completions(), months[0], months[1]);
		return new Resolved(label + " (auto: to-date run-rate × total months)", fmt.intGroup(proj), "adj");
	}

	/**
	 * Resolves the completions variance of tactic {@code n}'s to-date actual against its prorated
	 * to-date goal, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N completions vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the plan inputs, the to-date actual and the elapsed/total month counts
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticCompletionsVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                               CampaignData data) {
		String label = "Tactic " + n + " completions vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		Double planCompletions = planCompletions(data, n);
		if (months == null || t == null || planCompletions == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planCompletions, months[0], months[1]);
		String variance = pacing.paceVariance(t.completions(), planCtd, false);
		if (variance == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual vs prorated goal)", variance, "adj");
	}

	/**
	 * Resolves the prorated to-date reach goal for tactic {@code n}, derived as planned impressions
	 * ÷ the same derived frequency {@link #resolveTacticReach} uses for the actual figure, scaled
	 * by elapsedMonths / totalMonths, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N reach plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned impressions/frequency and the elapsed/total month counts
	 * @return the resolved prorated goal with its source tag, or a {@code not_found} placeholder when the
	 * inputs are unavailable
	 */
	public Resolved resolveTacticReachPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String label = "Tactic " + n + " reach plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double planReach = planReach(data, n);
		if (months == null || planReach == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planReach, months[0], months[1]);
		return new Resolved(label + " (auto: planned imps ÷ freq, prorated)", fmt.intGroup(planCtd), "adj");
	}

	/**
	 * Resolves the pace-based end-of-flight reach projection for tactic {@code n}, using the same
	 * imps ÷ freq derivation as the actual reach figure, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N reach proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual impressions/frequency and the elapsed/total month counts
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when
	 * unavailable
	 */
	public Resolved resolveTacticReachProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {
		String label = "Tactic " + n + " reach proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double actualReach = actualReach(data, n);
		if (months == null || actualReach == null) {
			return new Resolved(label, null, "not_found");
		}
		double proj = pacing.projection(actualReach, months[0], months[1]);
		return new Resolved(label + " (auto: to-date run-rate × total months)", fmt.intGroup(proj), "adj");
	}

	/**
	 * Resolves the reach variance of tactic {@code n}'s to-date actual against its prorated to-date
	 * goal, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N reach vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the plan inputs, the to-date actual and the elapsed/total month counts
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticReachVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                         CampaignData data) {
		String label = "Tactic " + n + " reach vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double planReach = planReach(data, n);
		Double actualReach = actualReach(data, n);
		if (months == null || planReach == null || actualReach == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planReach, months[0], months[1]);
		String variance = pacing.paceVariance(actualReach, planCtd, false);
		if (variance == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual vs prorated goal)", variance, "adj");
	}

	/**
	 * Resolves the prorated to-date spend goal for tactic {@code n}, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N spend plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the full-flight planned spend and the elapsed/total month counts
	 * @return the resolved prorated goal with its source tag, or a {@code not_found} placeholder when
	 * unavailable
	 */
	public Resolved resolveTacticSpendPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String label = "Tactic " + n + " spend plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		Double planSpend = t == null ? null : t.planSpend();
		if (months == null || planSpend == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planSpend, months[0], months[1]);
		return new Resolved(label + " (auto: plan × elapsed/total months)", fmt.moneyExact(planCtd), "adj");
	}

	/**
	 * Resolves the spend pacing percentage of tactic {@code n}'s to-date actual against its prorated
	 * to-date budget goal, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N spend pace:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the plan inputs, the to-date actual spend and the elapsed/total month counts
	 * @return the resolved pacing percentage with its source tag, or a {@code not_found} placeholder when
	 * the goal or actual can't be computed
	 */
	public Resolved resolveTacticSpendPace(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {
		String label = "Tactic " + n + " spend pace:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Tactic t = tactic(data, n);
		Double planSpend = t == null ? null : t.planSpend();
		if (months == null || t == null || planSpend == null) {
			return new Resolved(label, null, "not_found");
		}
		double planCtd = pacing.planCtd(planSpend, months[0], months[1]);
		String variance = pacing.paceVariance(t.spend(), planCtd, false);
		if (variance == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: to-date actual vs prorated goal)", variance, "adj");
	}

	/**
	 * Resolves the actual cost-per-thousand-impressions for tactic {@code n}, a metric that never
	 * existed in EOC, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N cpm:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual spend/impressions
	 * @return the resolved CPM with its source tag, or a {@code not_found} placeholder when actuals are
	 * unavailable
	 */
	public Resolved resolveTacticCpm(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " cpm:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t == null || t.imps() <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double cpm = t.spend() / t.imps() * 1000;
		return new Resolved(label + " (auto: to-date spend / imps × 1000)", fmt.moneyExact(cpm), "adj");
	}

	/**
	 * Resolves the prorated to-date CPM goal for tactic {@code n}: the full-flight planned
	 * spend/impressions ratio, a cost-efficiency target unaffected by elapsed time, preferring a manual
	 * override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N cpm plan ctd:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the full-flight planned spend/impressions
	 * @return the resolved goal with its source tag, or a {@code not_found} placeholder when the planned
	 * spend/impressions can't be computed
	 */
	public Resolved resolveTacticCpmPlanCtd(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String label = "Tactic " + n + " cpm plan ctd:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Double planCpm = planCpm(data, n);
		if (planCpm == null) {
			return new Resolved(label, null, "not_found");
		}
		return new Resolved(label + " (auto: planned spend / imps × 1000, rate target unaffected by elapsed time)",
				fmt.moneyExact(planCpm), "adj");
	}

	/**
	 * Resolves the projected end-of-flight CPM for tactic {@code n}, carrying the to-date observed cost
	 * efficiency forward, preferring a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N cpm proj:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the to-date actual spend/impressions
	 * @return the resolved projection with its source tag, or a {@code not_found} placeholder when
	 * actuals are unavailable
	 */
	public Resolved resolveTacticCpmProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     CampaignData data) {
		String label = "Tactic " + n + " cpm proj:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Tactic t = tactic(data, n);
		if (t == null || t.imps() <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double cpm = t.spend() / t.imps() * 1000;
		return new Resolved(label + " (auto: to-date actual rate held flat)", fmt.moneyExact(cpm), "adj");
	}

	/**
	 * Resolves the CPM variance of tactic {@code n}'s to-date actual against its planned CPM, preferring
	 * a manual override.
	 *
	 * @param n         one-based tactic index used to build the {@code "Tactic N cpm vs goal:"} lookup label
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned and to-date actual spend/impressions
	 * @return the resolved variance with its source tag, or a {@code not_found} placeholder when the
	 * goal or actual can't be computed
	 */
	public Resolved resolveTacticCpmVsGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {
		String label = "Tactic " + n + " cpm vs goal:";
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		Double planCpm = planCpm(data, n);
		Tactic t = tactic(data, n);
		if (planCpm == null || t == null || t.imps() <= 0) {
			return new Resolved(label, null, "not_found");
		}
		double cpm = t.spend() / t.imps() * 1000;
		String variance = pacing.paceVariance(cpm, planCpm, true);
		return new Resolved(label + " (auto: to-date actual vs planned rate)", variance, "adj");
	}

	/**
	 * Derives tactic {@code n}'s planned completions as planned impressions × planned VCR: the
	 * Estimates tab carries no direct completions figure, but this combination of two plan fields it
	 * does carry gives an honest estimate rather than fabricating a number with no basis.
	 *
	 * @param data campaign data supplying the planned impressions/VCR
	 * @param n    one-based tactic index
	 * @return the derived planned completions, or {@code null} when either input is missing
	 */
	Double planCompletions(CampaignData data, int n) {

		Tactic t = tactic(data, n);
		if (t == null || t.planImps() == null || t.planVcr() == null) {
			return null;
		}
		return t.planImps() * t.planVcr() / 100;
	}

	/**
	 * Derives tactic {@code n}'s planned reach as planned impressions ÷ the same derived frequency
	 * {@link #resolveTacticFreq} uses for the actual figure, so plan and actual reach share one
	 * frequency assumption.
	 *
	 * @param data campaign data supplying the planned impressions/max frequency
	 * @param n    one-based tactic index
	 * @return the derived planned reach, or {@code null} when the inputs are missing or non-positive
	 */
	Double planReach(CampaignData data, int n) {

		Tactic t = tactic(data, n);
		if (t == null || t.planImps() == null || t.planMaxFreq() == null || t.planMaxFreq() <= 0) {
			return null;
		}
		double freq = tacticExtraction.freqFromMax(n, t.planMaxFreq());
		return freq > 0 ? t.planImps() / freq : null;
	}

	/**
	 * Derives tactic {@code n}'s to-date actual reach as to-date impressions ÷ the same derived
	 * frequency {@link #resolveTacticFreq} uses for the actual figure.
	 *
	 * @param data campaign data supplying the to-date impressions and the planned max frequency
	 * @param n    one-based tactic index
	 * @return the derived actual reach, or {@code null} when the inputs are missing or non-positive
	 */
	Double actualReach(CampaignData data, int n) {

		Tactic t = tactic(data, n);
		if (t == null || t.planMaxFreq() == null || t.planMaxFreq() <= 0 || t.imps() <= 0) {
			return null;
		}
		double freq = tacticExtraction.freqFromMax(n, t.planMaxFreq());
		return freq > 0 ? t.imps() / freq : null;
	}

	/**
	 * Derives tactic {@code n}'s planned CPM as planned spend ÷ planned impressions × 1000: the
	 * Estimates tab carries no direct CPM figure, but plan spend/impressions already exist.
	 *
	 * @param data campaign data supplying the planned spend/impressions
	 * @param n    one-based tactic index
	 * @return the derived planned CPM, or {@code null} when either input is missing or impressions is
	 * non-positive
	 */
	Double planCpm(CampaignData data, int n) {

		Tactic t = tactic(data, n);
		if (t == null || t.planSpend() == null || t.planImps() == null || t.planImps() <= 0) {
			return null;
		}
		return t.planSpend() / t.planImps() * 1000;
	}

	String cell(List<String> row, int idx) {

		if (row == null || idx < 0 || idx >= row.size() || row.get(idx) == null) {
			return "";
		}
		return row.get(idx).trim();
	}

	String cellAt(List<String> row, int idx) {

		return cell(row, idx);
	}

	String joinLower(List<String> row, int n) {

		if (row == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		int limit = Math.min(n, row.size());
		for (int i = 0; i < limit; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			String c = row.get(i);
			sb.append(c == null ? "" : c);
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}
}
