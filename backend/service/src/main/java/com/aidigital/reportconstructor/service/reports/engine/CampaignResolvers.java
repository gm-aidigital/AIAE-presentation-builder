package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Campaign-level placeholder resolvers. Each returns a {@link Resolved}
 * ({@code label}, {@code value}, {@code source}); {@code value == null} ⇒
 * unresolved. Priority is always manual Adjustments → Media Plan → computed /
 * Claude. Claude outputs are passed in (the resolvers never call the API).
 */
@Component
public class CampaignResolvers {

	private final SheetRowHelper sheetUtils;
	private final Fmt fmt;
	private final TacticExtractionHelper tacticExtraction;

	/**
	 * Wires the collaborators used by every campaign-level resolver.
	 *
	 * @param sheetUtils       label/value lookups against Google Sheets export rows (Media Plan and Adjustments tabs)
	 * @param fmt              number/percentage/currency formatter for report display values
	 * @param tacticExtraction whitelist and display-name normalisation for media tactics
	 */
	public CampaignResolvers(SheetRowHelper sheetUtils, Fmt fmt, TacticExtractionHelper tacticExtraction) {
		this.sheetUtils = sheetUtils;
		this.fmt = fmt;
		this.tacticExtraction = tacticExtraction;
	}

	/**
	 * Generic single-label resolver: looks the label up in Adjustments first, then in the Media Plan sheet.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (take priority over the sheet)
	 * @param label     the exact row label to match (e.g. {@code "Flight dates:"})
	 * @return a {@link Resolved} with source {@code "adj"}, {@code "sheet"}, or a null-valued {@code "not_found"}
	 */
	public Resolved resolve(List<List<String>> sheetRows, List<List<String>> adjRows, String label) {

		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the campaign flight window, falling back to deriving it from the
	 * "Flight Start" / "Flight End" columns when no explicit label is present.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked before the sheet)
	 * @return a {@link Resolved} holding the flight-date range, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveFlightDates(List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Flight dates:");
		if (fromAdj != null) {
			return new Resolved("Flight dates:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Flight dates:");
		if (fromSheet != null) {
			return new Resolved("Flight dates:", fromSheet, "sheet");
		}

		String fromAdjAuto = sheetUtils.extractFlightDates(adjRows);
		if (fromAdjAuto != null) {
			return new Resolved("Flight Start / Flight End columns", fromAdjAuto, "adj");
		}
		String fromSheetAuto = sheetUtils.extractFlightDates(sheetRows);
		if (fromSheetAuto != null) {
			return new Resolved("Flight Start / Flight End columns", fromSheetAuto, "sheet");
		}
		return new Resolved("Flight Start / Flight End columns", null, "not_found");
	}

	/**
	 * Resolves the primary KPIs, auto-deriving them from the distinct Channel
	 * values (Display vs Video) under the "Channel" header when not labelled.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (also scanned for the Channel column)
	 * @return a {@link Resolved} KPI string such as {@code "Imps, CTR, R&F"} or {@code "Multiple tactics"}, or
	 * {@code "not_found"}
	 */
	public Resolved resolvePrimaryKpis(List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Primary KPIs:");
		if (fromAdj != null) {
			return new Resolved("Primary KPIs:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Primary KPIs:");
		if (fromSheet != null) {
			return new Resolved("Primary KPIs:", fromSheet, "sheet");
		}

		int headerRowIdx = -1;
		int channelCol = -1;
		outer:
		for (int i = 0; i < adjRows.size(); i++) {
			List<String> row = adjRows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).toLowerCase(Locale.ROOT).equals("channel")) {
					headerRowIdx = i;
					channelCol = j;
					break outer;
				}
			}
		}
		if (headerRowIdx < 0) {
			return new Resolved("Primary KPIs (auto: Channel)", null, "not_found");
		}

		Map<String, Boolean> channels = new LinkedHashMap<>();
		for (int i = headerRowIdx + 1; i < adjRows.size(); i++) {
			String val = cellAt(adjRows.get(i), channelCol).toLowerCase(Locale.ROOT);
			if (!val.isEmpty()) {
				channels.put(val, true);
			}
		}
		if (channels.isEmpty()) {
			return new Resolved("Primary KPIs (auto: Channel)", null, "not_found");
		}

		boolean hasDisplay = false;
		boolean hasVideo = false;
		for (String ch : channels.keySet()) {
			if (ch.contains("display")) {
				hasDisplay = true;
			}
			if (ch.contains("video")) {
				hasVideo = true;
			}
		}
		String kpiValue;
		if (hasDisplay && hasVideo) {
			kpiValue = "Multiple tactics";
		} else if (hasDisplay) {
			kpiValue = "Imps, CTR, R&F";
		} else if (hasVideo) {
			kpiValue = "Imps, VCR, R&F";
		} else {
			kpiValue = "Multiple tactics";
		}
		return new Resolved("Primary KPIs (auto: Channel)", kpiValue, "adj");
	}

	/**
	 * Resolves the target audience age range, falling back to a Claude-inferred
	 * value derived from the campaign brief.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param claudeAge Claude's age range pre-computed from the brief, used only when no sheet/adj value exists (may
	 *                  be null)
	 * @return a {@link Resolved} audience age, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveAudienceAge(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeAge) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Audience age:");
		if (fromAdj != null) {
			return new Resolved("Audience age:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Audience age:");
		if (fromSheet != null) {
			return new Resolved("Audience age:", fromSheet, "sheet");
		}
		if (claudeAge != null) {
			return new Resolved("Audience age (auto: Claude from brief)", claudeAge, "adj");
		}
		return new Resolved("Audience age:", null, "not_found");
	}

	/**
	 * Resolves the audience segments, falling back to a Claude summary of the
	 * Audience &amp; Inventory tab.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param claudeSegs Claude's segment summary derived from the Audience &amp; Inventory tab, used as last resort
	 *                   (may be null)
	 * @return a {@link Resolved} segments string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveAudienceSegments(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        String claudeSegs) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Audience segments:");
		if (fromAdj != null) {
			return new Resolved("Audience segments:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Audience segments:");
		if (fromSheet != null) {
			return new Resolved("Audience segments:", fromSheet, "sheet");
		}
		if (claudeSegs != null) {
			return new Resolved("Audience segments (auto: Claude from Audience&Inventory tab)", claudeSegs, "sheet");
		}
		return new Resolved("Audience segments:", null, "not_found");
	}

	/**
	 * Resolves the geo locations, preferring an explicit label, then the value
	 * directly below a "Geo" cell, and substituting a Claude summary when that
	 * value merely points at the Geo tab.
	 *
	 * <p>{@code geoSummary} is the Claude-summarised Geo-tab string, pre-computed by
	 * the orchestrator only when the "Geo" cell points at the Geo tab.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param geoSummary Claude summary of the Geo tab, used only when the sheet value references the Geo tab (may be
	 *                   null)
	 * @return a {@link Resolved} geo string (source {@code "claude"} when the summary is used), or a null-valued
	 * {@code "not_found"}
	 */
	public Resolved resolveGeoLocations(List<List<String>> sheetRows, List<List<String>> adjRows, String geoSummary) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Geo locations:");
		if (fromAdj != null) {
			return new Resolved("Geo locations:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Geo locations:");
		if (fromSheet != null) {
			return new Resolved("Geo locations:", fromSheet, "sheet");
		}

		String below = sheetUtils.findLabelValueBelow(sheetRows, "Geo");
		if (below != null) {
			String lc = below.toLowerCase(Locale.ROOT);
			if (lc.contains("see geo tab") || lc.contains("geo tab")) {
				if (geoSummary != null && !geoSummary.isBlank()) {
					return new Resolved("Geo (from Geo tab via Claude)", geoSummary, "claude");
				}
				return new Resolved("Geo (value below)", below, "sheet");
			}
			return new Resolved("Geo (value below)", below, "sheet");
		}
		return new Resolved("Geo locations:", null, "not_found");
	}

	/**
	 * Resolves the marketing funnel stages, falling back to the value directly
	 * below a "Goal" cell in the sheet.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} funnel-stages string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveFunnelStages(List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Funnel stages:");
		if (fromAdj != null) {
			return new Resolved("Funnel stages:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Funnel stages:");
		if (fromSheet != null) {
			return new Resolved("Funnel stages:", fromSheet, "sheet");
		}
		String below = sheetUtils.findLabelValueBelow(sheetRows, "Goal");
		if (below != null) {
			return new Resolved("Goal (value below)", below, "sheet");
		}
		return new Resolved("Funnel stages:", null, "not_found");
	}

	/**
	 * Resolves the tactics list by scanning up to 20 rows below the "Media"
	 * header, keeping only whitelisted tactics and de-duplicating by canonical name.
	 *
	 * @param sheetRows Media Plan tab rows scanned for the "Media" column
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} comma-joined list of normalised tactic display names, or a null-valued {@code
	 * "not_found"}
	 */
	public Resolved resolveTacticsList(List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Tactics list:");
		if (fromAdj != null) {
			return new Resolved("Tactics list:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Tactics list:");
		if (fromSheet != null) {
			return new Resolved("Tactics list:", fromSheet, "sheet");
		}

		Map<String, String> known = tacticExtraction.knownTacticsWhitelist();
		int mediaRowIdx = -1;
		int mediaColIdx = -1;
		outer:
		for (int i = 0; i < sheetRows.size(); i++) {
			List<String> row = sheetRows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).toLowerCase(Locale.ROOT).equals("media")) {
					mediaRowIdx = i;
					mediaColIdx = j;
					break outer;
				}
			}
		}
		if (mediaRowIdx < 0) {
			return new Resolved("Tactics list (auto: 20 rows below \"Media\")", null, "not_found");
		}

		Map<String, Boolean> seen = new LinkedHashMap<>();
		List<String> result = new ArrayList<>();
		int limit = Math.min(mediaRowIdx + 20, sheetRows.size() - 1);
		for (int i = mediaRowIdx + 1; i <= limit; i++) {
			String c = cellAt(sheetRows.get(i), mediaColIdx);
			if (c.isEmpty()) {
				continue;
			}
			String normalized = c.toLowerCase(Locale.ROOT);
			String canonical = known.get(normalized);
			if (canonical == null) {
				continue;
			}
			String canonicalKey = canonical.toLowerCase(Locale.ROOT);
			if (!seen.containsKey(canonicalKey)) {
				seen.put(canonicalKey, true);
				result.add(tacticExtraction.normalizeTacticDisplayName(canonical));
			}
		}
		if (result.isEmpty()) {
			return new Resolved("Tactics list (auto: 20 rows below \"Media\")", null, "not_found");
		}
		return new Resolved("Tactics list (auto: 20 rows below \"Media\")", String.join(", ", result), "sheet");
	}

	/**
	 * Resolves the proposal overview copy, falling back to Claude-generated text
	 * based on the brief plus media plan.
	 *
	 * @param sheetRows      Media Plan tab rows
	 * @param adjRows        manual Adjustments tab rows (checked first)
	 * @param claudeOverview Claude-authored proposal overview from the brief and media plan, used as last resort (may
	 *                       be null)
	 * @return a {@link Resolved} overview string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveProposalOverview(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        String claudeOverview) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Proposal overview:");
		if (fromAdj != null) {
			return new Resolved("Proposal overview:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Proposal overview:");
		if (fromSheet != null) {
			return new Resolved("Proposal overview:", fromSheet, "sheet");
		}
		if (claudeOverview != null) {
			return new Resolved("Proposal overview (auto: Claude from brief + media plan)", claudeOverview, "adj");
		}
		return new Resolved("Proposal overview:", null, "not_found");
	}

	/**
	 * Resolves the eight strategic-insight placeholders ({@code {{Strategic point N}}}
	 * and {@code {{Strategic overview N}}} for N = 1..4), preferring manual values and
	 * falling back to Claude's strategic insights.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param claude    Claude's per-index strategic insights (point + overview), one per slot, used when no manual
	 *                  value exists (may be null)
	 * @return a map keyed by placeholder ({@code {{Strategic point/overview N}}}) to its {@link Resolved}; values may
	 * be {@code "not_found"}
	 */
	public Map<String, Resolved> resolveStrategicInsights(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<StrategicInsight> claude) {

		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int i = 1; i <= 4; i++) {
			String mPoint = coalesce(sheetUtils.findLabelValue(adjRows, "Strategic point " + i + ":"),
					sheetUtils.findLabelValue(sheetRows, "Strategic point " + i + ":"));
			String mOver = coalesce(sheetUtils.findLabelValue(adjRows, "Strategic overview " + i + ":"),
					sheetUtils.findLabelValue(sheetRows, "Strategic overview " + i + ":"));
			StrategicInsight ci = claude != null && claude.size() >= i ? claude.get(i - 1) : null;

			String pointKey = "{{Strategic point " + i + "}}";
			if (mPoint != null) {
				result.put(pointKey, new Resolved("Strategic point " + i + ":", mPoint, "adj"));
			} else if (ci != null && notBlank(ci.point())) {
				result.put(pointKey, new Resolved("Strategic point " + i + " (auto: Claude)", ci.point(), "adj"));
			} else {
				result.put(pointKey, new Resolved("Strategic point " + i + ":", null, "not_found"));
			}

			String overKey = "{{Strategic overview " + i + "}}";
			if (mOver != null) {
				result.put(overKey, new Resolved("Strategic overview " + i + ":", mOver, "adj"));
			} else if (ci != null && notBlank(ci.overview())) {
				result.put(overKey, new Resolved("Strategic overview " + i + " (auto: Claude)", ci.overview(), "adj"));
			} else {
				result.put(overKey, new Resolved("Strategic overview " + i + ":", null, "not_found"));
			}
		}
		return result;
	}

	/**
	 * Resolves the eight optimization-recommendation placeholders ({@code {{recommendation N}}} and
	 * {@code {{recommendation N text}}} for N = 1..4), preferring manual values and falling back to Claude's
	 * forward-looking recommendations.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param claude    Claude's per-index recommendations (title + text), one per slot, used when no manual
	 *                  value exists (may be null)
	 * @return a map keyed by placeholder ({@code {{recommendation N}}} / {@code {{recommendation N text}}}) to
	 * its {@link Resolved}; values may be {@code "not_found"}
	 */
	public Map<String, Resolved> resolveRecommendations(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<Recommendation> claude) {

		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int i = 1; i <= 4; i++) {
			String mTitle = coalesce(sheetUtils.findLabelValue(adjRows, "Recommendation " + i + ":"),
					sheetUtils.findLabelValue(sheetRows, "Recommendation " + i + ":"));
			String mText = coalesce(sheetUtils.findLabelValue(adjRows, "Recommendation " + i + " text:"),
					sheetUtils.findLabelValue(sheetRows, "Recommendation " + i + " text:"));
			Recommendation ci = claude != null && claude.size() >= i ? claude.get(i - 1) : null;

			String titleKey = "{{recommendation " + i + "}}";
			if (mTitle != null) {
				result.put(titleKey, new Resolved("Recommendation " + i + ":", mTitle, "adj"));
			} else if (ci != null && notBlank(ci.title())) {
				result.put(titleKey, new Resolved("Recommendation " + i + " (auto: Claude)", ci.title(), "adj"));
			} else {
				result.put(titleKey, new Resolved("Recommendation " + i + ":", null, "not_found"));
			}

			String textKey = "{{recommendation " + i + " text}}";
			if (mText != null) {
				result.put(textKey, new Resolved("Recommendation " + i + " text:", mText, "adj"));
			} else if (ci != null && notBlank(ci.text())) {
				result.put(textKey, new Resolved("Recommendation " + i + " text (auto: Claude)", ci.text(), "adj"));
			} else {
				result.put(textKey, new Resolved("Recommendation " + i + " text:", null, "not_found"));
			}
		}
		return result;
	}

	/**
	 * Resolves the "Our results overview" copy, falling back to Claude-generated text.
	 *
	 * @param sheetRows      Media Plan tab rows
	 * @param adjRows        manual Adjustments tab rows (checked first)
	 * @param claudeOverview Claude-authored results overview, used as last resort (may be null)
	 * @return a {@link Resolved} results-overview string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveResultsOverview(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       String claudeOverview) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Our results overview:");
		if (fromAdj != null) {
			return new Resolved("Our results overview:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Our results overview:");
		if (fromSheet != null) {
			return new Resolved("Our results overview:", fromSheet, "sheet");
		}
		if (claudeOverview != null) {
			return new Resolved("Our results overview (auto: Claude)", claudeOverview, "adj");
		}
		return new Resolved("Our results overview:", null, "not_found");
	}

	/**
	 * Resolves the four {@code {{thoughts on the performance N}}} placeholders by
	 * splitting a single pipe-delimited manual value into four parts, or falling
	 * back to Claude's per-index performance commentary.
	 *
	 * @param sheetRows      Media Plan tab rows
	 * @param adjRows        manual Adjustments tab rows (the pipe-joined value is checked first)
	 * @param claudeThoughts Claude's performance thoughts, one entry per slot, used when no manual value exists (may
	 *                       be null)
	 * @return a map keyed by {@code {{thoughts on the performance N}}} (N = 1..4) to its {@link Resolved}; individual
	 * values may be null
	 */
	public Map<String, Resolved> resolveThoughtsOnPerformance(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<String> claudeThoughts) {

		String[] parts;
		String source;
		String label;
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Thoughts on the performance:");
		if (fromAdj != null) {
			parts = split4(fromAdj);
			source = "adj";
			label = "Thoughts on the performance:";
		} else {
			String fromSheet = sheetUtils.findLabelValue(sheetRows, "Thoughts on the performance:");
			if (fromSheet != null) {
				parts = split4(fromSheet);
				source = "sheet";
				label = "Thoughts on the performance:";
			} else if (claudeThoughts != null && !claudeThoughts.isEmpty()) {
				parts = new String[4];
				for (int i = 0; i < 4; i++) {
					parts[i] = i < claudeThoughts.size() ? claudeThoughts.get(i) : null;
				}
				source = "claude";
				label = "Thoughts on the performance (auto: Claude)";
			} else {
				parts = new String[4];
				source = "not_found";
				label = "Thoughts on the performance:";
			}
		}
		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int i = 1; i <= 4; i++) {
			result.put("{{thoughts on the performance " + i + "}}",
					new Resolved(label + " [" + i + "]", parts[i - 1], source));
		}
		return result;
	}

	/**
	 * Resolves total impressions, auto-computing from the BigQuery impression
	 * totals (group-formatted) when no manual value is present.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data whose totals supply the BigQuery impression count
	 * @return a {@link Resolved} impressions string, or a null-valued {@code "not_found"} when totals are non-positive
	 */
	public Resolved resolveTotalImps(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total imps:");
		if (fromAdj != null) {
			return new Resolved("Total imps:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total imps:");
		if (fromSheet != null) {
			return new Resolved("Total imps:", fromSheet, "sheet");
		}
		double imps = data.totals().imps();
		if (imps > 0) {
			return new Resolved("Total imps (auto: BQ Impressions)", fmt.intGroup(imps), "adj");
		}
		return new Resolved("Total imps (auto: BQ Impressions)", null, "not_found");
	}

	/**
	 * Resolves total investment, auto-computing from the BigQuery spend total
	 * (currency-formatted) when no manual value is present.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data whose totals supply the BigQuery spend amount
	 * @return a {@link Resolved} investment string, or a null-valued {@code "not_found"} when spend is non-positive
	 */
	public Resolved resolveTotalInvestment(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                       CampaignData data) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total investment:");
		if (fromAdj != null) {
			return new Resolved("Total investment:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total investment:");
		if (fromSheet != null) {
			return new Resolved("Total investment:", fromSheet, "sheet");
		}
		double spend = data.totals().spend();
		if (spend > 0) {
			return new Resolved("Total investment (auto: BQ spend)", fmt.money(spend), "adj");
		}
		return new Resolved("Total investment (auto: BQ spend)", null, "not_found");
	}

	/**
	 * Resolves total click-through rate, auto-computing from the BigQuery
	 * clicks-over-impressions total (percentage-formatted) when no manual value exists.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data whose totals supply the computed CTR
	 * @return a {@link Resolved} CTR string, or a null-valued {@code "not_found"} when CTR is unavailable
	 */
	public Resolved resolveTotalCtr(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total CTR:");
		if (fromAdj != null) {
			return new Resolved("Total CTR:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total CTR:");
		if (fromSheet != null) {
			return new Resolved("Total CTR:", fromSheet, "sheet");
		}
		Double ctr = data.totals().ctr();
		if (ctr != null) {
			return new Resolved("Total CTR (auto: Clicks / Imps)", fmt.pctOrDash(ctr), "adj");
		}
		return new Resolved("Total CTR (auto: Clicks / Imps)", null, "not_found");
	}

	/**
	 * Resolves total video completion rate, auto-computing from the BigQuery
	 * completions-over-impressions total (percentage-formatted) when no manual value exists.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data whose totals supply the computed VCR
	 * @return a {@link Resolved} VCR string, or a null-valued {@code "not_found"} when VCR is unavailable
	 */
	public Resolved resolveTotalVcr(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total VCR:");
		if (fromAdj != null) {
			return new Resolved("Total VCR:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total VCR:");
		if (fromSheet != null) {
			return new Resolved("Total VCR:", fromSheet, "sheet");
		}
		Double vcr = data.totals().vcr();
		if (vcr != null) {
			return new Resolved("Total VCR (auto: Completions / Imps)", fmt.pctOrDash(vcr), "adj");
		}
		return new Resolved("Total VCR (auto: Completions / Imps)", null, "not_found");
	}

	/**
	 * Resolves the campaign-level reach as the bottom-most populated value of the
	 * "Reach" column in the media-plan table (typically the totals row). The
	 * Estimates tab is read first; when it has no Reach column the Proposal (Media
	 * Plan) tab is used as the fallback, mirroring the user's manual lookup. A
	 * manual Adjustments / Media Plan {@code "Reach:"} label still wins over both.
	 *
	 * @param estimatesRows Estimates tab rows, the primary source for the Reach column
	 * @param sheetRows     Proposal / Media Plan tab rows, used for the manual label and as the Estimates fallback
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} reach string, or a null-valued {@code "not_found"} when no Reach column/value exists
	 */
	public Resolved resolveReach(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                             List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach:");
		if (fromAdj != null) {
			return new Resolved("Reach:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach:");
		if (fromSheet != null) {
			return new Resolved("Reach:", fromSheet, "sheet");
		}
		String fromEstimates = bottomReach(estimatesRows);
		if (fromEstimates != null) {
			return new Resolved("Reach (auto: Estimates Reach column, bottom row)", fromEstimates, "sheet");
		}
		String fromProposal = bottomReach(sheetRows);
		if (fromProposal != null) {
			return new Resolved("Reach (auto: Proposal Reach column, bottom row)", fromProposal, "sheet");
		}
		return new Resolved("Reach:", null, "not_found");
	}

	/**
	 * Resolves the campaign-level reach in compact notation (e.g. {@code "74k"},
	 * {@code "1.2M"}). It abbreviates the same bottom-row Reach value that
	 * {@link #resolveReach} reads (Estimates first, then Proposal), while a manual
	 * Adjustments / Media Plan {@code "Reach short:"} label wins over both.
	 *
	 * @param estimatesRows Estimates tab rows, the primary source for the Reach column
	 * @param sheetRows     Proposal / Media Plan tab rows, used for the manual label and as the Estimates fallback
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} compact reach string, or a null-valued {@code "not_found"} when no Reach value exists
	 */
	public Resolved resolveReachShort(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                                  List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach short:");
		if (fromAdj != null) {
			return new Resolved("Reach short:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach short:");
		if (fromSheet != null) {
			return new Resolved("Reach short:", fromSheet, "sheet");
		}
		Double fromEstimates = bottomReachValue(estimatesRows);
		if (fromEstimates != null) {
			return new Resolved("Reach short (auto: Estimates Reach column, bottom row)", fmt.compact(fromEstimates),
					"sheet");
		}
		Double fromProposal = bottomReachValue(sheetRows);
		if (fromProposal != null) {
			return new Resolved("Reach short (auto: Proposal Reach column, bottom row)", fmt.compact(fromProposal),
					"sheet");
		}
		return new Resolved("Reach short:", null, "not_found");
	}

	/**
	 * Resolves the maximum addressable audience volume in compact notation (e.g.
	 * {@code "74k"}, {@code "1.2M"}). The value is the figure the user enters in the
	 * UI from their DV360 audience estimate; it is parsed and abbreviated via
	 * {@link Fmt#compact}. A manual Adjustments / Media Plan {@code "Market volume:"}
	 * label still wins over the UI value and is used verbatim.
	 *
	 * @param marketVolume the raw audience-volume string entered in the UI (may be {@code null} or blank)
	 * @param sheetRows    Media Plan tab rows, scanned for a manual {@code "Market volume:"} override
	 * @param adjRows      manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} compact volume string, or a null-valued {@code "not_found"} when nothing parses
	 */
	public Resolved resolveMarketVolume(String marketVolume, List<List<String>> sheetRows,
	                                    List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Market volume:");
		if (fromAdj != null) {
			return new Resolved("Market volume:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Market volume:");
		if (fromSheet != null) {
			return new Resolved("Market volume:", fromSheet, "sheet");
		}
		Double parsed = parseReachCell(marketVolume);
		if (parsed != null) {
			return new Resolved("Market volume (UI)", fmt.compact(parsed), "sheet");
		}
		return new Resolved("Market volume:", null, "not_found");
	}

	/**
	 * Finds the "Reach" column in a media-plan grid and returns its bottom-most
	 * populated numeric value (the totals row) formatted with comma grouping.
	 *
	 * @param rows media-plan grid rows to scan for a Reach column
	 * @return the formatted bottom Reach value, or {@code null} when no Reach column or numeric value is present
	 */
	String bottomReach(List<List<String>> rows) {

		Double bottom = bottomReachValue(rows);
		return bottom == null ? null : fmt.intGroup(bottom);
	}

	/**
	 * Finds the "Reach" column in a media-plan grid and returns its bottom-most
	 * populated numeric value (the totals row) as a raw count.
	 *
	 * @param rows media-plan grid rows to scan for a Reach column
	 * @return the bottom Reach count, or {@code null} when no Reach column or numeric value is present
	 */
	Double bottomReachValue(List<List<String>> rows) {

		if (rows == null) {
			return null;
		}
		int[] header = findReachColumn(rows);
		if (header == null) {
			return null;
		}
		Double bottom = null;
		for (int i = header[0] + 1; i < rows.size(); i++) {
			Double v = parseReachCell(cellAt(rows.get(i), header[1]));
			if (v != null) {
				bottom = v;
			}
		}
		return bottom;
	}

	/**
	 * Locates the Reach column header, preferring an exact {@code "reach"} cell and
	 * otherwise the first header that merely contains "reach" (e.g. "Unique Reach"),
	 * skipping percentage/rate columns.
	 *
	 * @param rows media-plan grid rows to scan
	 * @return a two-element {@code [rowIndex, colIndex]}, or {@code null} when no Reach header is present
	 */
	int[] findReachColumn(List<List<String>> rows) {

		int[] loose = null;
		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				String v = cell(row, j).toLowerCase(Locale.ROOT);
				if (v.equals("reach")) {
					return new int[]{i, j};
				}
				if (loose == null && v.contains("reach") && !v.contains("%") && !v.contains("rate")) {
					loose = new int[]{i, j};
				}
			}
		}
		return loose;
	}

	/**
	 * Parses a Reach cell into a non-negative count, stripping grouping separators
	 * and any surrounding decoration.
	 *
	 * @param raw the raw cell text from the Reach column
	 * @return the parsed reach count, or {@code null} when the cell holds no usable number
	 */
	Double parseReachCell(String raw) {

		if (raw == null) {
			return null;
		}
		String c = raw.replace(",", "").replaceAll("[^0-9.]", "");
		if (c.isEmpty() || !c.matches("\\d*\\.?\\d+")) {
			return null;
		}
		try {
			return Double.parseDouble(c);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	String[] split4(String raw) {

		String[] out = new String[4];
		if (raw == null || raw.trim().isEmpty()) {
			return out;
		}
		String[] parts = raw.split(" \\| ");
		for (int i = 0; i < 4; i++) {
			String p = i < parts.length ? parts[i].trim() : null;
			out[i] = (p == null || p.isEmpty()) ? null : p;
		}
		return out;
	}

	String coalesce(String a, String b) {

		return a != null ? a : b;
	}

	boolean notBlank(String s) {

		return s != null && !s.isBlank();
	}

	String cell(List<String> row, int idx) {

		String v = row.get(idx);
		return v == null ? "" : v.trim();
	}

	String cellAt(List<String> row, int idx) {

		if (row == null || idx < 0 || idx >= row.size()) {
			return "";
		}
		return cell(row, idx);
	}
}
