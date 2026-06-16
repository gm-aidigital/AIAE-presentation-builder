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

	/**
	 * Creates the resolver wiring the collaborators used to look up, format and auto-derive
	 * per-tactic metric values.
	 *
	 * @param sheetUtils        helper that scans Media Plan / Adjustments grids for a labelled value
	 * @param fmt               number/percentage formatter for report display values
	 * @param tacticExtraction  tactic-specific helpers (KPI-type lookup, frequency math)
	 * @param campaignResolvers shared adj-then-sheet manual resolver reused for gender/daypart rows
	 */
	public TacticResolvers(
			SheetRowHelper sheetUtils, Fmt fmt,
			TacticExtractionHelper tacticExtraction, CampaignResolvers campaignResolvers) {
		this.sheetUtils = sheetUtils;
		this.fmt = fmt;
		this.tacticExtraction = tacticExtraction;
		this.campaignResolvers = campaignResolvers;
	}

	private static final String DASH = "\u2014"; // —

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
	 * Resolves the impressions value for tactic {@code n}, preferring a manual Adjustments override,
	 * then the Media Plan sheet, then the grouped BigQuery impression count.
	 *
	 * @param n          one-based tactic index used to build the {@code "Tactic N imps:"} lookup label
	 * @param tacticName display name of the tactic (unused for impressions; kept for resolver-signature parity)
	 * @param sheetRows  Media Plan grid rows searched for the labelled value
	 * @param adjRows    manual Adjustments grid rows that take precedence over the sheet
	 * @param data       campaign data providing the BigQuery-derived impression count fallback
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
				return new Resolved(label + " (auto: Estimates VCR)", "VCR \u2013 " + Math.round(val) + "%", "adj");
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
			if (c.isEmpty()) {
				break;
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

	Tactic tactic(CampaignData data, int n) {

		Map<Integer, Tactic> tactics = data == null ? null : data.tactics();
		return tactics == null ? null : tactics.get(n);
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
