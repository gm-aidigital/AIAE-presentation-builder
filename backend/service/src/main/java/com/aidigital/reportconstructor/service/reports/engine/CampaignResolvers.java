package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Campaign-level placeholder resolvers. Each returns a {@link Resolved}
 * ({@code label}, {@code value}, {@code source}); {@code value == null} ⇒
 * unresolved. Priority is always manual Adjustments → Media Plan → computed /
 * Claude. Claude outputs are passed in (the resolvers never call the API).
 */
@Component
public class CampaignResolvers {

	/**
	 * De-duplication factor applied when a multi-tactic media plan has no totals row to read reach
	 * from: the per-tactic reaches are summed and scaled by this to approximate the unique campaign
	 * reach (the raw sum double-counts users hit by more than one tactic).
	 */
	private static final double MULTI_TACTIC_REACH_FACTOR = 0.8;

	/** Number of leading cells scanned for a "total" label when classifying a media-plan row. */
	private static final int TOTAL_LABEL_SCAN_COLUMNS = 5;

	/** Rows below the "Media" header scanned when auto-deriving the tactics list (covers up to 28 tactics). */
	private static final int TACTICS_LIST_SCAN_ROWS = 60;

	/** How many tactic names are listed verbatim before the {@code "+N more"} overflow suffix. */
	private static final int TACTICS_LIST_MAX_NAMED = 7;

	/** Number of {@code {{Our results overview N}}} slots — one per tactic group of up to 7 tactics. */
	private static final int RESULTS_OVERVIEW_GROUPS = 4;

	/**
	 * Slots on the campaign-level "Thoughts on the performance" slide: four analytical paragraphs plus the
	 * closing campaign story in slot 5.
	 */
	private static final int THOUGHT_SLOTS = 5;

	/** Pacing-dashboard takeaway slots the EOM template carries, one per dashboard slide. */
	private static final int PACING_TAKEAWAY_SLOTS = 4;

	/** Auto-derived label written next to the campaign-wide impressions pace. */
	private static final String TOTAL_IMPS_PACE_AUTO_LABEL = "Total imps pace (auto: fact vs planned impressions)";

	/** Auto-derived label written next to the cover's reporting-period name. */
	private static final String REPORTING_MONTH_AUTO_LABEL = "Reporting month (auto: selected date window)";

	/** Auto-derived label written next to the booked flight's month count. */
	private static final String FLIGHT_MONTHS_TOTAL_AUTO_LABEL = "Flight months total (auto: media-plan flight dates)";

	/** Auto-derived label written next to the reporting month's position in the flight. */
	private static final String FLIGHT_MONTH_NUMBER_AUTO_LABEL =
			"Flight month number (auto: reporting month within flight)";

	/** Auto-derived label written next to the abbreviated planned impressions. */
	private static final String PLANNED_IMPS_SHORT_AUTO_LABEL =
			"Planned total impressions short (auto: sum of tactic plans)";

	/** Auto-derived label written next to the abbreviated delivered impressions. */
	private static final String FACT_IMPS_SHORT_AUTO_LABEL = "Fact total impressions short (auto: delivered totals)";

	private final SheetRowHelper sheetUtils;
	private final Fmt fmt;
	private final TacticExtractionHelper tacticExtraction;
	private final RatePlanCalculator pacing;

	/**
	 * Wires the collaborators used by every campaign-level resolver.
	 *
	 * @param sheetUtils       label/value lookups against Google Sheets export rows (Media Plan and Adjustments tabs)
	 * @param fmt              number/percentage/currency formatter for report display values
	 * @param tacticExtraction whitelist and display-name normalisation for media tactics
	 * @param pacing           EOM proration/projection math shared by the campaign-wide "plan ctd" / "pace" resolvers
	 */
	public CampaignResolvers(
			SheetRowHelper sheetUtils, Fmt fmt, TacticExtractionHelper tacticExtraction, RatePlanCalculator pacing) {
		this.sheetUtils = sheetUtils;
		this.fmt = fmt;
		this.tacticExtraction = tacticExtraction;
		this.pacing = pacing;
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
	 * Resolves the RFP/Campaign Brief text, preferring a manual Adjustments override, then the Media
	 * Plan sheet, then the free-text brief ingested through the RFP/Campaign Brief UI.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (take priority over the sheet)
	 * @param brief     free-text campaign brief/RFP content submitted through the request payload
	 * @return a {@link Resolved} RFP info string, or a null-valued {@code not_found} entry when no value exists
	 */
	public Resolved resolveRfpInfo(List<List<String>> sheetRows, List<List<String>> adjRows, String brief) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "RFP info:");
		if (fromAdj != null) {
			return new Resolved("RFP info:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "RFP info:");
		if (fromSheet != null) {
			return new Resolved("RFP info:", fromSheet, "sheet");
		}
		if (notBlank(brief)) {
			return new Resolved("RFP info (auto: Campaign Brief)", brief, "adj");
		}
		return new Resolved("RFP info:", null, "not_found");
	}

	/**
	 * Resolves the campaign change-log text, preferring a manual Adjustments override, then the Media
	 * Plan sheet, then the optional free-text change log entered through the UI. Mirrors
	 * {@link #resolveRfpInfo} so a change log supplied directly in a source sheet still wins over the
	 * UI field.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (take priority over the sheet)
	 * @param changeLog optional free-text change-log content submitted through the request payload
	 * @return a {@link Resolved} change-log string, or a null-valued {@code not_found} entry when no value exists
	 */
	public Resolved resolveChangeLog(List<List<String>> sheetRows, List<List<String>> adjRows, String changeLog) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Change log:");
		if (fromAdj != null) {
			return new Resolved("Change log:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Change log:");
		if (fromSheet != null) {
			return new Resolved("Change log:", fromSheet, "sheet");
		}
		if (notBlank(changeLog)) {
			return new Resolved("Change log (auto: UI)", changeLog, "adj");
		}
		return new Resolved("Change log:", null, "not_found");
	}

	/**
	 * Resolves the primary KPIs, preferring a manual value, then Claude's per-tactic KPI line, and finally
	 * auto-deriving them from the distinct Channel values (Display vs Video) under the "Channel" header.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (also scanned for the Channel column)
	 * @param claudeKpis Claude-generated single-line KPI string derived from the media plan, used when no manual
	 *                   value exists (may be null)
	 * @return a {@link Resolved} KPI string such as {@code "Imps, CTR, R&F"} or {@code "Imps, CTR, VCR, R&F"}, or
	 * {@code "not_found"}
	 */
	public Resolved resolvePrimaryKpis(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeKpis) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Primary KPIs:");
		if (fromAdj != null) {
			return new Resolved("Primary KPIs:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Primary KPIs:");
		if (fromSheet != null) {
			return new Resolved("Primary KPIs:", fromSheet, "sheet");
		}
		if (notBlank(claudeKpis)) {
			return new Resolved("Primary KPIs (auto: Claude from media plan)", claudeKpis, "claude");
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
	 * Resolves the geo locations, preferring an explicit label, then the distinct literal values in the
	 * geo/location column of the media-plan grid, and finally a Claude workbook summary when the column
	 * is absent or merely points at another tab.
	 *
	 * <p>{@code geoSummary} is the Claude summary of the whole workbook, pre-computed by the orchestrator
	 * only when {@code needGeoSummary} fires (no manual value and no literal geo column).
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param geoSummary Claude workbook geo summary, used only when the column lists no literal locations (may be null)
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

		List<String> column = sheetUtils.collectColumnValuesBelow(sheetRows, MediaPlanColumn.GEO.getSynonyms());
		List<String> literals = column.stream().filter(v -> !sheetUtils.referencesGeoTab(v)).toList();
		if (!literals.isEmpty()) {
			return new Resolved("Geo (media-plan column)", String.join(", ", literals), "sheet");
		}
		// Column is empty or merely points at another tab: fall back to the Claude workbook summary.
		if (geoSummary != null && !geoSummary.isBlank()) {
			return new Resolved("Geo (from workbook via Claude)", geoSummary, "claude");
		}
		return new Resolved("Geo locations:", null, "not_found");
	}

	/**
	 * Resolves the marketing funnel stages, preferring an explicit label, then the distinct values in
	 * the "Goal"/"Funnel"/"Objective" column of the media-plan grid, and finally a Claude workbook
	 * summary when the column is absent.
	 *
	 * @param sheetRows     Media Plan tab rows
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param funnelSummary Claude summary of the funnel stages, used only when no manual value and no funnel column are
	 *                      present (may be {@code null})
	 * @return a {@link Resolved} funnel-stages string (source {@code "claude"} when the summary is used), or a
	 * null-valued {@code "not_found"}
	 */
	public Resolved resolveFunnelStages(List<List<String>> sheetRows, List<List<String>> adjRows,
			String funnelSummary) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Funnel stages:");
		if (fromAdj != null) {
			return new Resolved("Funnel stages:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Funnel stages:");
		if (fromSheet != null) {
			return new Resolved("Funnel stages:", fromSheet, "sheet");
		}
		List<String> column = sheetUtils.collectColumnValuesBelow(sheetRows, MediaPlanColumn.FUNNEL.getSynonyms());
		if (!column.isEmpty()) {
			return new Resolved("Funnel stages (media-plan column)", String.join(", ", column), "sheet");
		}
		if (funnelSummary != null && !funnelSummary.isBlank()) {
			return new Resolved("Funnel stages (from workbook via Claude)", funnelSummary, "claude");
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
		return resolveTacticsList(sheetRows, adjRows, List.of());
	}

	/**
	 * Resolves the tactics list from the tactics the report actually covers, falling back to a scan of
	 * the rows below the "Media" header when the caller has no such list (nothing matched yet).
	 *
	 * @param sheetRows      Media Plan tab rows scanned for the "Media" column
	 * @param adjRows        manual Adjustments tab rows (checked first)
	 * @param effectiveNames tactic names the report covers, in report order; empty to scan the plan
	 * @return a {@link Resolved} comma-joined list of normalised tactic display names, or a null-valued {@code
	 * "not_found"}
	 */
	public Resolved resolveTacticsList(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                   List<String> effectiveNames) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Tactics list:");
		if (fromAdj != null) {
			return new Resolved("Tactics list:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Tactics list:");
		if (fromSheet != null) {
			return new Resolved("Tactics list:", fromSheet, "sheet");
		}

		Map<String, String> known = tacticExtraction.knownTacticsWhitelist();
		if (effectiveNames != null && !effectiveNames.isEmpty()) {
			return new Resolved("Tactics list (auto: reported tactics)",
					joinDeduplicated(effectiveNames, known), "sheet");
		}
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
		int limit = Math.min(mediaRowIdx + TACTICS_LIST_SCAN_ROWS, sheetRows.size() - 1);
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
			return new Resolved("Tactics list (auto: rows below \"Media\")", null, "not_found");
		}
		return new Resolved("Tactics list (auto: rows below \"Media\")", joinTacticsList(result), "sheet");
	}

	/**
	 * De-duplicates tactic names by their canonical form and joins them for {@code {{tactics_list}}}.
	 * Names outside the whitelist keep their own spelling rather than being dropped — by the time a
	 * name reaches here it is already a tactic the report is built around.
	 *
	 * @param names the tactic names in report order
	 * @param known the canonical-name whitelist, keyed by lowercased name
	 * @return the comma-joined, de-duplicated display names
	 */
	String joinDeduplicated(List<String> names, Map<String, String> known) {

		Map<String, Boolean> seen = new LinkedHashMap<>();
		List<String> result = new ArrayList<>();
		for (String name : names) {
			if (name == null || name.isBlank()) {
				continue;
			}
			String canonical = known.getOrDefault(name.trim().toLowerCase(Locale.ROOT), name.trim());
			String canonicalKey = canonical.toLowerCase(Locale.ROOT);
			if (!seen.containsKey(canonicalKey)) {
				seen.put(canonicalKey, true);
				result.add(tacticExtraction.normalizeTacticDisplayName(canonical));
			}
		}
		return joinTacticsList(result);
	}

	/**
	 * Joins tactic display names for {@code {{tactics_list}}}: all names when there are at most
	 * {@link #TACTICS_LIST_MAX_NAMED}, otherwise the first {@code TACTICS_LIST_MAX_NAMED} names
	 * followed by a {@code " +N more"} overflow suffix (N = remaining count).
	 *
	 * @param names the de-duplicated tactic display names, in media-plan order
	 * @return the comma-joined list, with a {@code " +N more"} suffix when it overflows
	 */
	String joinTacticsList(List<String> names) {
		if (names.size() <= TACTICS_LIST_MAX_NAMED) {
			return String.join(", ", names);
		}
		String named = String.join(", ", names.subList(0, TACTICS_LIST_MAX_NAMED));
		return named + " +" + (names.size() - TACTICS_LIST_MAX_NAMED) + " more";
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
	 * Resolves the EOM north-star headline ({@code {{our north star}}}), preferring a hand-entered value and
	 * falling back to the Claude-authored one.
	 *
	 * @param sheetRows       Media Plan tab rows
	 * @param adjRows         manual Adjustments tab rows (checked first)
	 * @param claudeNorthStar Claude-authored north star from the brief and media plan (may be null)
	 * @return a {@link Resolved} north-star string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveNorthStar(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 String claudeNorthStar) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "North star:");
		if (fromAdj != null) {
			return new Resolved("North star:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "North star:");
		if (fromSheet != null) {
			return new Resolved("North star:", fromSheet, "sheet");
		}
		if (claudeNorthStar != null) {
			return new Resolved("North star (auto: Claude from brief + media plan)", claudeNorthStar, "adj");
		}
		return new Resolved("North star:", null, "not_found");
	}

	/**
	 * Resolves the EOM north-star supporting paragraph ({@code {{extended north star}}}) — the objective
	 * unpacked into geos, audiences and channels — preferring a hand-entered value.
	 *
	 * @param sheetRows      Media Plan tab rows
	 * @param adjRows        manual Adjustments tab rows (checked first)
	 * @param claudeExtended Claude-authored extended north star (may be null)
	 * @return a {@link Resolved} extended north-star string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveExtendedNorthStar(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                         String claudeExtended) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Extended north star:");
		if (fromAdj != null) {
			return new Resolved("Extended north star:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Extended north star:");
		if (fromSheet != null) {
			return new Resolved("Extended north star:", fromSheet, "sheet");
		}
		if (claudeExtended != null) {
			return new Resolved("Extended north star (auto: Claude from brief + media plan)", claudeExtended, "adj");
		}
		return new Resolved("Extended north star:", null, "not_found");
	}

	/**
	 * Resolves the EOM horizon block ({@code {{horizon}}}) — when the campaign runs, for how long and with
	 * what delivery shape — preferring a hand-entered value.
	 *
	 * @param sheetRows     Media Plan tab rows
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param claudeHorizon Claude-authored horizon copy (may be null)
	 * @return a {@link Resolved} horizon string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveHorizon(List<List<String>> sheetRows, List<List<String>> adjRows,
	                               String claudeHorizon) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Horizon:");
		if (fromAdj != null) {
			return new Resolved("Horizon:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Horizon:");
		if (fromSheet != null) {
			return new Resolved("Horizon:", fromSheet, "sheet");
		}
		if (claudeHorizon != null) {
			return new Resolved("Horizon (auto: Claude from brief + media plan)", claudeHorizon, "adj");
		}
		return new Resolved("Horizon:", null, "not_found");
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
	 * Resolves the four EOM pacing-dashboard takeaways ({@code {{pacing dash takeaway 1..4}}}), one per
	 * dashboard slide, preferring a manual value and falling back to Claude's.
	 *
	 * <p>All four slots are emitted even when the campaign fills fewer of them: the dashboards above the
	 * tactic count are deleted from the deck during the trim, and a slot that survives with no takeaway
	 * renders as a dash rather than as a raw token.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param claude    Claude's per-slide takeaways in slide order, used when no manual value exists
	 *                  (may be {@code null})
	 * @return a map keyed by {@code {{pacing dash takeaway N}}} to its {@link Resolved}; values may be
	 * {@code "not_found"}
	 */
	public Map<String, Resolved> resolvePacingTakeaways(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<String> claude) {

		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int i = 1; i <= PACING_TAKEAWAY_SLOTS; i++) {
			String label = "Pacing dash takeaway " + i + ":";
			String manual = coalesce(sheetUtils.findLabelValue(adjRows, label),
					sheetUtils.findLabelValue(sheetRows, label));
			String fromClaude = claude != null && claude.size() >= i ? claude.get(i - 1) : null;
			String key = "{{pacing dash takeaway " + i + "}}";
			if (manual != null) {
				result.put(key, new Resolved(label, manual, "adj"));
			} else if (notBlank(fromClaude)) {
				result.put(key, new Resolved("Pacing dash takeaway " + i + " (auto: Claude)", fromClaude, "adj"));
			} else {
				result.put(key, new Resolved(label, null, "not_found"));
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
	 * Resolves the four {@code {{Our results overview N}}} placeholders (N = 1..4), one per tactic
	 * group of up to 7 tactics (group 1 → tactics 1–7, group 2 → 8–14, …). Each prefers a manual
	 * {@code "Our results overview N:"} value (group 1 also accepts the legacy unsuffixed
	 * {@code "Our results overview:"}), then falls back to Claude's per-group narrative. Groups with
	 * no Claude text and no manual value resolve to {@code "not_found"} (their slide is trimmed away).
	 *
	 * @param sheetRows        Media Plan tab rows
	 * @param adjRows          manual Adjustments tab rows (checked first)
	 * @param claudeOverviews  Claude per-group results overviews keyed by 1-based group number (may be null)
	 * @return a map keyed by placeholder ({@code {{Our results overview N}}}) to its {@link Resolved}
	 */
	public Map<String, Resolved> resolveResultsOverviews(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                                     Map<Integer, String> claudeOverviews) {

		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int g = 1; g <= RESULTS_OVERVIEW_GROUPS; g++) {
			String label = "Our results overview " + g + ":";
			// The sheet-as-source template lists these labels with empty value cells, so findLabelValue
			// returns "" (label present, no value) rather than null. Treat a blank cell as "no manual
			// override" so the Claude per-group narrative below is used instead of an empty "—".
			String manual = firstNonBlank(sheetUtils.findLabelValue(adjRows, label),
					sheetUtils.findLabelValue(sheetRows, label));
			if (manual == null && g == 1) {
				manual = firstNonBlank(sheetUtils.findLabelValue(adjRows, "Our results overview:"),
						sheetUtils.findLabelValue(sheetRows, "Our results overview:"));
			}
			String claude = claudeOverviews == null ? null : claudeOverviews.get(g);

			String key = "{{Our results overview " + g + "}}";
			if (manual != null) {
				result.put(key, new Resolved(label, manual, "adj"));
			} else if (notBlank(claude)) {
				result.put(key, new Resolved("Our results overview " + g + " (auto: Claude)", claude, "adj"));
			} else {
				result.put(key, new Resolved(label, null, "not_found"));
			}
		}
		return result;
	}

	/**
	 * Resolves the {@code {{f_oppartunity}}} frequency-opportunity copy, preferring a manual
	 * {@code "Frequency opportunity:"} value and falling back to the Claude-generated text.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param claudeText Claude-authored frequency-opportunity copy, used as last resort (may be null)
	 * @return a {@link Resolved} frequency-opportunity string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveFOpportunity(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                    String claudeText) {
		return resolveClaudeNarrative(sheetRows, adjRows, "Frequency opportunity:", claudeText);
	}

	/**
	 * Resolves the {@code {{f_fact}}} actual-frequency copy, preferring a manual {@code "Frequency fact:"}
	 * value and falling back to the Claude-generated text.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param claudeText Claude-authored actual-frequency copy, used as last resort (may be null)
	 * @return a {@link Resolved} actual-frequency string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveFFact(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeText) {
		return resolveClaudeNarrative(sheetRows, adjRows, "Frequency fact:", claudeText);
	}

	/**
	 * Resolves the {@code {{f_storytelling}}} frequency-storytelling copy, preferring a manual
	 * {@code "Frequency storytelling:"} value and falling back to the Claude-generated text.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param claudeText Claude-authored frequency-storytelling copy, used as last resort (may be null)
	 * @return a {@link Resolved} frequency-storytelling string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveFStorytelling(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     String claudeText) {
		return resolveClaudeNarrative(sheetRows, adjRows, "Frequency storytelling:", claudeText);
	}

	/**
	 * Shared manual-first / Claude-fallback resolution for a single-label narrative placeholder.
	 *
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (checked first)
	 * @param label      the exact manual override label (e.g. {@code "Frequency opportunity:"})
	 * @param claudeText Claude-authored copy used when no manual value exists (may be null)
	 * @return a {@link Resolved} carrying the manual or Claude value, or a null-valued {@code "not_found"}
	 */
	Resolved resolveClaudeNarrative(List<List<String>> sheetRows, List<List<String>> adjRows, String label,
	                                String claudeText) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		if (fromSheet != null) {
			return new Resolved(label, fromSheet, "sheet");
		}
		if (notBlank(claudeText)) {
			return new Resolved(label + " (auto: Claude)", claudeText, "adj");
		}
		return new Resolved(label, null, "not_found");
	}

	/**
	 * Resolves the five {@code {{thoughts on the performance N}}} placeholders by
	 * splitting a single pipe-delimited manual value into five parts, or falling
	 * back to Claude's per-index performance commentary.
	 *
	 * <p>Slots 1–4 are the analytical paragraphs; slot 5 is the campaign story, which Claude writes as its
	 * own field and the client appends as the last entry of {@code claudeThoughts}. A manual override still
	 * carries all five in one pipe-joined cell, so an editor who supplies only four leaves the story slot
	 * empty rather than shifting the paragraphs.
	 *
	 * @param sheetRows      Media Plan tab rows
	 * @param adjRows        manual Adjustments tab rows (the pipe-joined value is checked first)
	 * @param claudeThoughts Claude's performance thoughts, one entry per slot, used when no manual value exists (may
	 *                       be null)
	 * @return a map keyed by {@code {{thoughts on the performance N}}} (N = 1..5) to its {@link Resolved}; individual
	 * values may be null
	 */
	public Map<String, Resolved> resolveThoughtsOnPerformance(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<String> claudeThoughts) {

		String[] parts;
		String source;
		String label;
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Thoughts on the performance:");
		if (fromAdj != null) {
			parts = splitThoughts(fromAdj);
			source = "adj";
			label = "Thoughts on the performance:";
		} else {
			String fromSheet = sheetUtils.findLabelValue(sheetRows, "Thoughts on the performance:");
			if (fromSheet != null) {
				parts = splitThoughts(fromSheet);
				source = "sheet";
				label = "Thoughts on the performance:";
			} else if (claudeThoughts != null && !claudeThoughts.isEmpty()) {
				parts = new String[THOUGHT_SLOTS];
				for (int i = 0; i < THOUGHT_SLOTS; i++) {
					parts[i] = i < claudeThoughts.size() ? claudeThoughts.get(i) : null;
				}
				source = "claude";
				label = "Thoughts on the performance (auto: Claude)";
			} else {
				parts = new String[THOUGHT_SLOTS];
				source = "not_found";
				label = "Thoughts on the performance:";
			}
		}
		Map<String, Resolved> result = new LinkedHashMap<>();
		for (int i = 1; i <= THOUGHT_SLOTS; i++) {
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
	 * (currency-formatted, keeping cents when the amount is fractional) when no manual value is present.
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
			return new Resolved("Total investment (auto: BQ spend)", fmt.moneyExact(spend), "adj");
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
	 * Reads the {@code [elapsedMonths, totalMonths]} pair EOM pacing prorates by, shared by every
	 * campaign-wide "plan ctd" / "pace" resolver below.
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
	 * Sums the full-campaign planned impressions across every tactic (the rate/budget or Estimates-tab
	 * figure each tactic resolver already reads), giving the campaign-level goal the EOM pacing tokens
	 * prorate from. There is no separate campaign-level plan figure in the source data.
	 *
	 * @param data campaign data whose tactics carry the per-tactic planned impressions
	 * @return the summed planned impressions, or {@code null} when no tactic has one
	 */
	Double totalPlanImps(CampaignData data) {
		if (data == null || data.tactics() == null) {
			return null;
		}
		double sum = 0;
		boolean any = false;
		for (Tactic t : data.tactics().values()) {
			if (t.planImps() != null) {
				sum += t.planImps();
				any = true;
			}
		}
		return any ? sum : null;
	}

	/**
	 * Sums the full-campaign planned spend across every tactic, the campaign-level counterpart to
	 * {@link #totalPlanImps}.
	 *
	 * @param data campaign data whose tactics carry the per-tactic planned spend
	 * @return the summed planned spend, or {@code null} when no tactic has one
	 */
	Double totalPlanSpend(CampaignData data) {
		if (data == null || data.tactics() == null) {
			return null;
		}
		double sum = 0;
		boolean any = false;
		for (Tactic t : data.tactics().values()) {
			if (t.planSpend() != null) {
				sum += t.planSpend();
				any = true;
			}
		}
		return any ? sum : null;
	}

	/**
	 * Resolves the campaign-wide prorated to-date impressions goal: the summed full-campaign tactic
	 * plans scaled by elapsedMonths / totalMonths, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the per-tactic plans and the elapsed/total month counts
	 * @return a {@link Resolved} prorated goal, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalImpsPlanCtd(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                        CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total imps plan ctd:");
		if (fromAdj != null) {
			return new Resolved("Total imps plan ctd:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total imps plan ctd:");
		if (fromSheet != null) {
			return new Resolved("Total imps plan ctd:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double totalPlan = totalPlanImps(data);
		if (months == null || totalPlan == null) {
			return new Resolved("Total imps plan ctd (auto: sum of tactic plans, prorated)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		return new Resolved("Total imps plan ctd (auto: sum of tactic plans, prorated)", fmt.intGroup(planCtd), "adj");
	}

	/**
	 * Resolves the campaign-wide impressions pace of the actual against the plan, preferring a manual
	 * override. See {@link #impsPace} for the formula.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the summed plan and the actual impressions
	 * @return a {@link Resolved} pace figure, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalImpsPace(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                     CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total imps pace:");
		if (fromAdj != null) {
			return new Resolved("Total imps pace:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total imps pace:");
		if (fromSheet != null) {
			return new Resolved("Total imps pace:", fromSheet, "sheet");
		}
		Double totalPlan = totalPlanImps(data);
		if (totalPlan == null || totalPlan <= 0 || data == null || data.totals() == null) {
			return new Resolved(TOTAL_IMPS_PACE_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(TOTAL_IMPS_PACE_AUTO_LABEL, impsPace(data.totals().imps(), totalPlan), "adj");
	}

	/**
	 * Resolves the campaign-wide prorated to-date investment goal, the budget counterpart to
	 * {@link #resolveTotalImpsPlanCtd}, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the per-tactic plans and the elapsed/total month counts
	 * @return a {@link Resolved} prorated goal, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalInvestmentPlanCtd(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                              CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total investment plan ctd:");
		if (fromAdj != null) {
			return new Resolved("Total investment plan ctd:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total investment plan ctd:");
		if (fromSheet != null) {
			return new Resolved("Total investment plan ctd:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double totalPlan = totalPlanSpend(data);
		if (months == null || totalPlan == null) {
			return new Resolved("Total investment plan ctd (auto: sum of tactic plans, prorated)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		return new Resolved("Total investment plan ctd (auto: sum of tactic plans, prorated)",
				fmt.moneyExact(planCtd), "adj");
	}

	/**
	 * Resolves the campaign-wide investment pacing variance of the to-date actual against the prorated
	 * to-date goal, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the plan, the to-date actual and the elapsed/total month counts
	 * @return a {@link Resolved} pacing variance, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalInvestmentPace(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                           CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Total investment pace:");
		if (fromAdj != null) {
			return new Resolved("Total investment pace:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Total investment pace:");
		if (fromSheet != null) {
			return new Resolved("Total investment pace:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double totalPlan = totalPlanSpend(data);
		if (months == null || totalPlan == null || data.totals() == null) {
			return new Resolved("Total investment pace (auto: to-date actual vs prorated goal)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		String variance = pacing.paceVariance(data.totals().spend(), planCtd, false);
		return new Resolved("Total investment pace (auto: to-date actual vs prorated goal)", variance,
				variance == null ? "not_found" : "adj");
	}

	/** Percentage-point threshold beyond which the campaign is deemed ahead of / behind pace. */
	private static final double CAMPAIGN_PACE_STATUS_THRESHOLD_PCT = 5.0;

	/**
	 * Resolves the campaign's headline pacing verdict ({@code "AHEAD OF PACE"} / {@code "ON PACE"} /
	 * {@code "BEHIND PACE"}) from the impressions pacing variance, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the plan, the to-date actual and the elapsed/total month counts
	 * @return a {@link Resolved} pacing verdict, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveCampaignPaceStatus(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Campaign pace status:");
		if (fromAdj != null) {
			return new Resolved("Campaign pace status:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Campaign pace status:");
		if (fromSheet != null) {
			return new Resolved("Campaign pace status:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		Double totalPlan = totalPlanImps(data);
		if (months == null || totalPlan == null || data.totals() == null) {
			return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", null, "not_found");
		}
		double planCtd = pacing.planCtd(totalPlan, months[0], months[1]);
		if (planCtd <= 0) {
			return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", null, "not_found");
		}
		double pct = (data.totals().imps() - planCtd) / planCtd * 100;
		String status = pct >= CAMPAIGN_PACE_STATUS_THRESHOLD_PCT ? "AHEAD OF PACE"
				: pct <= -CAMPAIGN_PACE_STATUS_THRESHOLD_PCT ? "BEHIND PACE" : "ON PACE";
		return new Resolved("Campaign pace status (auto: imps pace vs prorated goal)", status, "adj");
	}

	/**
	 * Resolves the reporting month's 1-based index within the flight (e.g. the second calendar month
	 * of a 3-month flight resolves to {@code 2}), preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code eomMonthNumber()}
	 * @return a {@link Resolved} month index, or a null-valued {@code "not_found"} when EOC or unset
	 */
	public Resolved resolveEomMonthNumber(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom month number:");
		if (fromAdj != null) {
			return new Resolved("Eom month number:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom month number:");
		if (fromSheet != null) {
			return new Resolved("Eom month number:", fromSheet, "sheet");
		}
		if (data == null || data.eomMonthNumber() == null) {
			return new Resolved("Eom month number (auto: calendar months since flight start)", null, "not_found");
		}
		return new Resolved("Eom month number (auto: calendar months since flight start)",
				String.valueOf(data.eomMonthNumber()), "adj");
	}

	/**
	 * Resolves the total number of calendar months the flight spans, preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code eomFlightMonthsTotal()}
	 * @return a {@link Resolved} month count, or a null-valued {@code "not_found"} when EOC or unset
	 */
	public Resolved resolveEomFlightMonthsTotal(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                            CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom flight months total:");
		if (fromAdj != null) {
			return new Resolved("Eom flight months total:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom flight months total:");
		if (fromSheet != null) {
			return new Resolved("Eom flight months total:", fromSheet, "sheet");
		}
		if (data == null || data.eomFlightMonthsTotal() == null) {
			return new Resolved("Eom flight months total (auto: Data Inputs Flight dates)", null, "not_found");
		}
		return new Resolved("Eom flight months total (auto: Data Inputs Flight dates)",
				String.valueOf(data.eomFlightMonthsTotal()), "adj");
	}

	/**
	 * Resolves the reporting month's calendar name and year (e.g. {@code "October 2025"}), preferring a
	 * manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the flight window whose end is the report cut-off
	 * @return a {@link Resolved} month label, or a null-valued {@code "not_found"} when no flight window is set
	 */
	public Resolved resolveEomReportMonth(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom report month:");
		if (fromAdj != null) {
			return new Resolved("Eom report month:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom report month:");
		if (fromSheet != null) {
			return new Resolved("Eom report month:", fromSheet, "sheet");
		}
		if (data == null || data.flightTs() == null) {
			return new Resolved("Eom report month (auto: flight window end)", null, "not_found");
		}
		return new Resolved("Eom report month (auto: flight window end)",
				pacing.monthLabel(data.flightTs().end()), "adj");
	}

	/**
	 * Resolves the next month's 1-based index within the flight, preferring a manual override. Empty
	 * when the reporting month is already the flight's last calendar month (there is no next month).
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the elapsed/total month counts
	 * @return a {@link Resolved} next month index, or a null-valued {@code "not_found"} when unavailable
	 * or the reporting month is the flight's last
	 */
	public Resolved resolveEomNextMonthNumber(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom next month number:");
		if (fromAdj != null) {
			return new Resolved("Eom next month number:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom next month number:");
		if (fromSheet != null) {
			return new Resolved("Eom next month number:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		if (months == null || months[0] >= months[1]) {
			return new Resolved("Eom next month number (auto: month number + 1)", null, "not_found");
		}
		return new Resolved("Eom next month number (auto: month number + 1)", String.valueOf(months[0] + 1), "adj");
	}

	/**
	 * Resolves the next calendar month's name (no year, e.g. {@code "November"}), preferring a manual
	 * override. Empty when the reporting month is already the flight's last calendar month.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the flight window and the elapsed/total month counts
	 * @return a {@link Resolved} next month name, or a null-valued {@code "not_found"} when unavailable
	 * or the reporting month is the flight's last
	 */
	public Resolved resolveEomNextReportMonth(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Eom next report month:");
		if (fromAdj != null) {
			return new Resolved("Eom next report month:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Eom next report month:");
		if (fromSheet != null) {
			return new Resolved("Eom next report month:", fromSheet, "sheet");
		}
		int[] months = elapsedAndTotalMonths(data);
		if (months == null || months[0] >= months[1] || data.flightTs() == null) {
			return new Resolved("Eom next report month (auto: flight window end + 1 month)", null, "not_found");
		}
		LocalDate nextMonth = data.flightTs().end().plusMonths(1);
		return new Resolved("Eom next report month (auto: flight window end + 1 month)",
				pacing.monthNameOnly(nextMonth), "adj");
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
		return resolveReach(estimatesRows, sheetRows, adjRows, null);
	}

	/**
	 * Resolves the campaign reach, preferring the figure summed from the reported tactics.
	 *
	 * @param estimatesRows Estimates tab rows
	 * @param sheetRows     Media Plan / Proposal tab rows
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param reachPlan     the reach computed once by {@link #computeFrequencies}, or {@code null}
	 * @return a {@link Resolved} reach string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveReach(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                             List<List<String>> adjRows, Double reachPlan) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach:");
		if (fromAdj != null) {
			return new Resolved("Reach:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach:");
		if (fromSheet != null) {
			return new Resolved("Reach:", fromSheet, "sheet");
		}
		if (reachPlan != null && reachPlan > 0) {
			return new Resolved("Reach (auto: summed over the reported tactics)", fmt.intGroup(reachPlan), "sheet");
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
		return resolveReachShort(estimatesRows, sheetRows, adjRows, null);
	}

	/**
	 * Resolves the compact campaign reach, preferring the figure summed from the reported tactics.
	 *
	 * @param estimatesRows Estimates tab rows
	 * @param sheetRows     Media Plan / Proposal tab rows
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param reachPlan     the reach computed once by {@link #computeFrequencies}, or {@code null}
	 * @return a {@link Resolved} compact reach string, or a null-valued {@code "not_found"}
	 */
	public Resolved resolveReachShort(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                                  List<List<String>> adjRows, Double reachPlan) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach short:");
		if (fromAdj != null) {
			return new Resolved("Reach short:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach short:");
		if (fromSheet != null) {
			return new Resolved("Reach short:", fromSheet, "sheet");
		}
		if (reachPlan != null && reachPlan > 0) {
			return new Resolved("Reach short (auto: summed over the reported tactics)", fmt.compact(reachPlan),
					"sheet");
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
	 * Resolves the campaign-level actual ("fact") reach from the single {@code reachFact} value computed once
	 * by {@link #computeFrequencies} for this report, so the placeholder matches the exact number that seeded
	 * the Claude {@code {{f_fact}}} narrative rather than drawing its own random uplift. A manual Adjustments
	 * / Media Plan {@code "Reach fact:"} label still wins over the computed value.
	 *
	 * @param reachFact the actual reach computed by {@link #computeFrequencies} for this report, or
	 *                  {@code null} when not computable
	 * @param sheetRows Proposal / Media Plan tab rows, scanned for the manual {@code "Reach fact:"} label
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} actual-reach string, or a null-valued {@code "not_found"} when no Reach value exists
	 */
	public Resolved resolveReachFact(Double reachFact, List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach fact:");
		if (fromAdj != null) {
			return new Resolved("Reach fact:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach fact:");
		if (fromSheet != null) {
			return new Resolved("Reach fact:", fromSheet, "sheet");
		}
		if (reachFact != null) {
			return new Resolved("Reach fact (auto: computeFrequencies reachFact)", fmt.intGroup(reachFact), "sheet");
		}
		return new Resolved("Reach fact:", null, "not_found");
	}

	/**
	 * Resolves the campaign-level actual ("fact") reach in compact notation (e.g. {@code "74k"},
	 * {@code "1.2M"}). It abbreviates the same {@code reachFact} value that {@link #resolveReachFact} resolves,
	 * while a manual Adjustments / Media Plan {@code "Reach fact short:"} label wins over the computed value.
	 *
	 * @param reachFact the actual reach computed by {@link #computeFrequencies} for this report, or
	 *                  {@code null} when not computable
	 * @param sheetRows Proposal / Media Plan tab rows, scanned for the manual {@code "Reach fact short:"} label
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @return a {@link Resolved} compact actual-reach string, or a null-valued {@code "not_found"} when no Reach
	 * value exists
	 */
	public Resolved resolveReachFactShort(Double reachFact, List<List<String>> sheetRows,
	                                      List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reach fact short:");
		if (fromAdj != null) {
			return new Resolved("Reach fact short:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reach fact short:");
		if (fromSheet != null) {
			return new Resolved("Reach fact short:", fromSheet, "sheet");
		}
		if (reachFact != null) {
			return new Resolved("Reach fact short (auto: computeFrequencies reachFact)", fmt.compact(reachFact),
					"sheet");
		}
		return new Resolved("Reach fact short:", null, "not_found");
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
	 * Resolves the maximum addressable audience volume as a plain number, honouring the same manual
	 * Adjustments / Media Plan {@code "Market volume:"} override as {@link #resolveMarketVolume}, so
	 * {@link #computeFrequencies} can derive the remaining in-market audience from the exact same figure
	 * shown in {@code {{market volume}}}.
	 *
	 * @param marketVolume the raw audience-volume string entered in the UI (may be {@code null} or blank)
	 * @param sheetRows    Media Plan tab rows, scanned for a manual {@code "Market volume:"} override
	 * @param adjRows      manual Adjustments tab rows (checked first)
	 * @return the parsed market volume, or {@code null} when nothing parses
	 */
	Double numericMarketVolume(String marketVolume, List<List<String>> sheetRows, List<List<String>> adjRows) {

		String fromAdj = sheetUtils.findLabelValue(adjRows, "Market volume:");
		if (fromAdj != null) {
			return parseReachCell(fromAdj);
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Market volume:");
		if (fromSheet != null) {
			return parseReachCell(fromSheet);
		}
		return parseReachCell(marketVolume);
	}

	/**
	 * Derives the campaign reach from a media-plan grid's "Reach" column (see
	 * {@link #bottomReachValue}) and formats it with comma grouping.
	 *
	 * @param rows media-plan grid rows to scan for a Reach column
	 * @return the formatted Reach value, or {@code null} when no Reach column or numeric value is present
	 */
	String bottomReach(List<List<String>> rows) {

		Double bottom = bottomReachValue(rows);
		return bottom == null ? null : fmt.intGroup(bottom);
	}

	/**
	 * Finds the "Reach" column in a media-plan grid and derives the campaign reach from it.
	 *
	 * <p>The Reach column rarely has a value on the very bottom row, so rather than blindly
	 * taking the bottom-most cell this classifies each populated row below the header into a
	 * totals row or a tactic row (see {@link #isTotalRow}), then:
	 * <ul>
	 *   <li>a totals row with a Reach value wins outright (single- or multi-tactic plans);</li>
	 *   <li>otherwise a single tactic row supplies the reach directly;</li>
	 *   <li>otherwise (several tactics, no usable total) the tactic reaches are summed and scaled
	 *       by {@link #MULTI_TACTIC_REACH_FACTOR} to approximate de-duplicated unique reach.</li>
	 * </ul>
	 *
	 * @param rows media-plan grid rows to scan for a Reach column
	 * @return the derived Reach count, or {@code null} when no Reach column or numeric value is present
	 */
	Double bottomReachValue(List<List<String>> rows) {

		if (rows == null) {
			return null;
		}
		int[] header = findReachColumn(rows);
		if (header == null) {
			return null;
		}
		int mediaCol = findMediaColumn(rows);
		Double totalReach = null;
		List<Double> tacticReaches = new ArrayList<>();
		for (int i = header[0] + 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			Double v = parseReachCell(cellAt(row, header[1]));
			if (v == null) {
				continue;
			}
			if (isTotalRow(row, mediaCol)) {
				totalReach = v;
			} else {
				tacticReaches.add(v);
			}
		}
		if (totalReach != null) {
			return totalReach;
		}
		if (tacticReaches.isEmpty()) {
			return null;
		}
		if (tacticReaches.size() == 1) {
			return tacticReaches.get(0);
		}
		double sum = 0;
		for (Double r : tacticReaches) {
			sum += r;
		}
		return sum * MULTI_TACTIC_REACH_FACTOR;
	}

	/**
	 * Reports whether a media-plan row is a totals/subtotals row rather than a single tactic line.
	 * Totals rows are not always labelled, so two signals are used: an explicit "total" token in a
	 * leading label cell (e.g. "Total", "Grand Total"), or — when the Media/name column is known —
	 * a populated metric row whose Media cell is blank (the common unlabelled totals layout).
	 *
	 * @param row      the media-plan row to inspect
	 * @param mediaCol the Media/name column index, or {@code -1} when no Media column was found
	 * @return {@code true} when the row holds campaign-level totals, {@code false} otherwise
	 */
	boolean isTotalRow(List<String> row, int mediaCol) {

		if (row == null) {
			return false;
		}
		int limit = Math.min(row.size(), TOTAL_LABEL_SCAN_COLUMNS);
		for (int j = 0; j < limit; j++) {
			if (cell(row, j).toLowerCase(Locale.ROOT).contains("total")) {
				return true;
			}
		}
		return mediaCol >= 0 && cellAt(row, mediaCol).isEmpty();
	}

	/**
	 * Locates the "Media" (tactic name) column header in a media-plan grid, used to tell tactic rows
	 * (named) apart from an unlabelled totals row (blank name cell).
	 *
	 * @param rows media-plan grid rows to scan
	 * @return the Media column index, or {@code -1} when no "Media" header is present
	 */
	int findMediaColumn(List<List<String>> rows) {

		for (List<String> row : rows) {
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).toLowerCase(Locale.ROOT).equals("media")) {
					return j;
				}
			}
		}
		return -1;
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

	/**
	 * Computes the planned and actual campaign frequency figures (touchpoints per user) that seed the
	 * Claude frequency narrative, plus the single {@code reachFact} value behind the actual figure. The
	 * planned figure is total impressions ({@code {{total imps}}}) ÷ campaign reach ({@code {{reach}}}),
	 * rounded up to a whole number. {@code reachFact} is that same reach scaled once by a random 1–20%
	 * uplift (see {@link #reachFactMultiplier}), and the actual figure is total impressions ÷ {@code reachFact}
	 * (kept to two decimals). All three are {@code null} when the underlying impressions/reach are
	 * unavailable. Callers must reuse the returned {@code reachFact} — e.g. via
	 * {@link #resolveReachFact(Double, List, List)} — rather than recomputing it, so the same actual-reach
	 * number is used everywhere in the report.
	 *
	 * @param estimatesRows Estimates tab rows (primary reach source)
	 * @param sheetRows     Media Plan / Proposal tab rows (manual overrides and Estimates fallback)
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param data          aggregated campaign data supplying the BigQuery impression total
	 * @param marketVolume  the raw audience-volume string entered in the UI, used to derive
	 *                         {@link CampaignFrequencies#remainingAudience()} (may be {@code null} or blank)
	 * @return the computed {@link CampaignFrequencies}; any field may be {@code null}
	 */
	public CampaignFrequencies computeFrequencies(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                                              List<List<String>> adjRows, CampaignData data,
	                                              String marketVolume) {

		Double imps = numericTotalImps(sheetRows, adjRows, data);
		// Drawn once here and carried on the result, so the frequency, {{reach}} and {{reach_p}} all
		// describe the same campaign reach instead of each redrawing the de-duplication factor.
		Double summed = summedPlanReach(data);
		Double reach = numericReach(estimatesRows, sheetRows, adjRows, summed);
		if (imps == null || imps <= 0 || reach == null || reach <= 0) {
			return new CampaignFrequencies(null, null, null, null, reach);
		}
		String plan = String.valueOf((long) Math.ceil(imps / reach));
		double reachFact = reachFactFrom(reach);
		String fact = freq2(imps / reachFact);
		Double marketVolumeNum = numericMarketVolume(marketVolume, sheetRows, adjRows);
		Double remainingAudience = marketVolumeNum == null ? null : Math.max(marketVolumeNum - reachFact, 0);
		return new CampaignFrequencies(plan, fact, reachFact, remainingAudience, reach);
	}

	/**
	 * Resolves the numeric total impressions, honouring a manual {@code "Total imps:"} override before
	 * falling back to the BigQuery impression total.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data supplying the BigQuery impression total
	 * @return the impression count, or {@code null} when neither a manual value nor positive total exists
	 */
	Double numericTotalImps(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {

		String manual = coalesce(sheetUtils.findLabelValue(adjRows, "Total imps:"),
				sheetUtils.findLabelValue(sheetRows, "Total imps:"));
		Double parsed = parseReachCell(manual);
		if (parsed != null) {
			return parsed;
		}
		double imps = data == null || data.totals() == null ? 0 : data.totals().imps();
		return imps > 0 ? imps : null;
	}

	/**
	 * Resolves the numeric campaign reach the same way {@link #resolveReach} does: a manual {@code "Reach:"}
	 * override first, then the Estimates-tab bottom row, then the Proposal-tab bottom row.
	 *
	 * @param estimatesRows Estimates tab rows (primary reach source)
	 * @param sheetRows     Media Plan / Proposal tab rows (manual override and Estimates fallback)
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @return the reach count, or {@code null} when no reach value is present
	 */
	Double numericReach(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                    List<List<String>> adjRows) {

		return numericReach(estimatesRows, sheetRows, adjRows, null);
	}

	/**
	 * Resolves the numeric campaign reach, preferring a reach already summed from the reported tactics.
	 *
	 * @param estimatesRows Estimates tab rows (primary reach source)
	 * @param sheetRows     Media Plan / Proposal tab rows (manual override and Estimates fallback)
	 * @param adjRows       manual Adjustments tab rows (checked first)
	 * @param summedReach   reach summed from the reported tactics, or {@code null} when unavailable
	 * @return the reach count, or {@code null} when no reach value is present
	 */
	Double numericReach(List<List<String>> estimatesRows, List<List<String>> sheetRows,
	                    List<List<String>> adjRows, Double summedReach) {

		String manual = coalesce(sheetUtils.findLabelValue(adjRows, "Reach:"),
				sheetUtils.findLabelValue(sheetRows, "Reach:"));
		Double parsed = parseReachCell(manual);
		if (parsed != null) {
			return parsed;
		}
		if (summedReach != null && summedReach > 0) {
			return summedReach;
		}
		Double fromEstimates = bottomReachValue(estimatesRows);
		return fromEstimates != null ? fromEstimates : bottomReachValue(sheetRows);
	}

	/**
	 * Sums the media plan's per-tactic Reach figures across the tactics the report covers and
	 * de-duplicates the sum once, so a plan line the user dropped at matching time contributes no reach.
	 *
	 * <p>Reach does not add up cleanly — the same person is reached by several tactics — so the sum is
	 * scaled by a fresh random factor in {@code [0.72, 0.88]}, the same approximation the plan's own
	 * multi-tactic total rests on. Drawn once per report and carried on
	 * {@link CampaignFrequencies#reachPlan()} so every reach placeholder shows the same number.
	 *
	 * @param data campaign data whose tactics carry the per-tactic planned reach
	 * @return the de-duplicated planned reach, or {@code null} when no reported tactic has a Reach figure
	 */
	Double summedPlanReach(CampaignData data) {

		if (data == null || data.tactics() == null) {
			return null;
		}
		double sum = 0;
		boolean any = false;
		for (Tactic t : data.tactics().values()) {
			if (t.planReach() != null && t.planReach() > 0) {
				sum += t.planReach();
				any = true;
			}
		}
		if (!any || sum <= 0) {
			return null;
		}
		return sum * planReachDedupeFactor();
	}

	/**
	 * Returns the de-duplication factor applied to summed per-tactic reach, redrawn on every call.
	 *
	 * @return a factor in {@code [0.72, 0.88]}
	 */
	double planReachDedupeFactor() {
		return ThreadLocalRandom.current().nextDouble(0.72, 0.88);
	}

	/**
	 * Scales a reach value by a fresh random 1–20% uplift, modelling delivered reach coming in slightly above
	 * the planned/estimated figure.
	 *
	 * @param reach the planned/estimated reach to scale
	 * @return {@code reach} multiplied by a factor in {@code [1.01, 1.2]}
	 */
	double reachFactFrom(double reach) {
		return reach * reachFactMultiplier();
	}

	/**
	 * Returns the multiplier that scales a reach value up by a fresh random 1–20% on every call.
	 *
	 * @return a factor in {@code [1.01, 1.2]}
	 */
	double reachFactMultiplier() {
		return ThreadLocalRandom.current().nextDouble(1.01, 1.2);
	}

	/**
	 * Formats a frequency value as a fixed two-decimal string (e.g. {@code "3.45"}).
	 *
	 * @param v the frequency value (touchpoints per user)
	 * @return the value rendered with exactly two fractional digits
	 */
	String freq2(double v) {
		return String.format(Locale.US, "%.2f", v);
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	/**
	 * Splits a pipe-joined "Thoughts on the performance" cell into one entry per slide slot, padding with
	 * {@code null} when the cell carries fewer parts and dropping the surplus when it carries more.
	 *
	 * @param raw the pipe-joined cell text, possibly {@code null} or blank
	 * @return a {@link #THOUGHT_SLOTS}-long array, {@code null} in every slot the cell did not fill
	 */
	String[] splitThoughts(String raw) {

		String[] out = new String[THOUGHT_SLOTS];
		if (raw == null || raw.trim().isEmpty()) {
			return out;
		}
		String[] parts = raw.split(" \\| ");
		for (int i = 0; i < THOUGHT_SLOTS; i++) {
			String p = i < parts.length ? parts[i].trim() : null;
			out[i] = (p == null || p.isEmpty()) ? null : p;
		}
		return out;
	}

	String coalesce(String a, String b) {

		return a != null ? a : b;
	}

	/**
	 * Returns the first argument that is non-null and non-blank, or {@code null} when neither qualifies.
	 * Unlike {@link #coalesce(String, String)} this treats a present-but-empty sheet cell (findLabelValue
	 * returns {@code ""} for a label whose value cell is empty) as absent, so a Claude fallback can apply.
	 *
	 * @param a the preferred candidate value (may be null or blank)
	 * @param b the fallback candidate value (may be null or blank)
	 * @return the first non-blank value, or {@code null} when both are null or blank
	 */
	String firstNonBlank(String a, String b) {

		if (notBlank(a)) {
			return a;
		}
		return notBlank(b) ? b : null;
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

	/**
	 * Formats the campaign-wide impressions pace two different ways, matching how the cover reads it.
	 * Over-delivery is a signed lift against the plan ({@code (fact / plan - 1) × 100}, e.g.
	 * {@code "+2%"}); anything at or below plan is instead the share of the plan actually delivered
	 * ({@code fact / plan × 100}, e.g. {@code "98%"}, and exactly {@code "100%"} on plan), so a shortfall
	 * never shows up as a bare negative number on the cover.
	 *
	 * @param fact the delivered impressions
	 * @param plan the planned impressions (must be positive)
	 * @return the formatted pace string
	 */
	String impsPace(double fact, double plan) {
		double liftPct = (fact / plan - 1) * 100;
		if (liftPct > 0) {
			return "+" + Math.round(liftPct) + "%";
		}
		return Math.round(fact / plan * 100) + "%";
	}

	/**
	 * Resolves the reporting period's calendar label for the cover — {@code "August 2026"} for a
	 * single-month window, {@code "July - August 2026"} when the window spans two months of one year,
	 * and {@code "December 2025 - January 2026"} across a year boundary — preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the reporting window
	 * @return a {@link Resolved} month label, or a null-valued {@code "not_found"} when no window is set
	 */
	public Resolved resolveReportingMonth(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Reporting month:");
		if (fromAdj != null) {
			return new Resolved("Reporting month:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Reporting month:");
		if (fromSheet != null) {
			return new Resolved("Reporting month:", fromSheet, "sheet");
		}
		if (data == null || data.flightTs() == null
				|| data.flightTs().start() == null || data.flightTs().end() == null) {
			return new Resolved(REPORTING_MONTH_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(REPORTING_MONTH_AUTO_LABEL,
				reportingMonthLabel(data.flightTs().start(), data.flightTs().end()), "adj");
	}

	/**
	 * Builds the reporting-period label from its boundaries, collapsing to a single month when both
	 * ends sit in the same calendar month and dropping the repeated year when both ends share one.
	 *
	 * @param start first day of the reporting window
	 * @param end   last day of the reporting window
	 * @return the label, e.g. {@code "August 2026"} or {@code "July - August 2026"}
	 */
	String reportingMonthLabel(LocalDate start, LocalDate end) {
		if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
			return pacing.monthLabel(end);
		}
		if (start.getYear() == end.getYear()) {
			return pacing.monthNameOnly(start) + " - " + pacing.monthLabel(end);
		}
		return pacing.monthLabel(start) + " - " + pacing.monthLabel(end);
	}

	/**
	 * Resolves how many calendar months the whole booked flight spans ({@code {{total mon no}}}),
	 * preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code campaignMonthsTotal()}
	 * @return a {@link Resolved} month count, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveCampaignMonthsTotal(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                           CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Flight months total:");
		if (fromAdj != null) {
			return new Resolved("Flight months total:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Flight months total:");
		if (fromSheet != null) {
			return new Resolved("Flight months total:", fromSheet, "sheet");
		}
		if (data == null || data.campaignMonthsTotal() == null) {
			return new Resolved(FLIGHT_MONTHS_TOTAL_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(FLIGHT_MONTHS_TOTAL_AUTO_LABEL, String.valueOf(data.campaignMonthsTotal()), "adj");
	}

	/**
	 * Resolves the reporting month's 1-based position inside the booked flight ({@code {{mon no}}}),
	 * preferring a manual override.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing {@code campaignMonthNumber()}
	 * @return a {@link Resolved} month index, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveCampaignMonthNumber(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                           CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Flight month number:");
		if (fromAdj != null) {
			return new Resolved("Flight month number:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Flight month number:");
		if (fromSheet != null) {
			return new Resolved("Flight month number:", fromSheet, "sheet");
		}
		if (data == null || data.campaignMonthNumber() == null) {
			return new Resolved(FLIGHT_MONTH_NUMBER_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(FLIGHT_MONTH_NUMBER_AUTO_LABEL, String.valueOf(data.campaignMonthNumber()), "adj");
	}

	/**
	 * Resolves the abbreviated planned impressions for the cover ({@code "1.1M"}, {@code "100K"}),
	 * preferring a manual override. Abbreviates the same figure the pacing tokens plan against.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data whose tactics carry the planned impressions
	 * @return a {@link Resolved} abbreviated figure, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalPlannedImpsShort(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                             CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Planned total impressions short:");
		if (fromAdj != null) {
			return new Resolved("Planned total impressions short:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Planned total impressions short:");
		if (fromSheet != null) {
			return new Resolved("Planned total impressions short:", fromSheet, "sheet");
		}
		Double totalPlan = totalPlanImps(data);
		if (totalPlan == null) {
			return new Resolved(PLANNED_IMPS_SHORT_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(PLANNED_IMPS_SHORT_AUTO_LABEL, fmt.compactUpper(totalPlan), "adj");
	}

	/**
	 * Resolves the abbreviated delivered impressions for the cover ({@code "1.1M"}, {@code "100K"}),
	 * preferring a manual override. Abbreviates the same actual the {@code {{total imps}}} token shows
	 * in full.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param data      aggregated campaign data providing the delivered totals
	 * @return a {@link Resolved} abbreviated figure, or a null-valued {@code "not_found"} when unavailable
	 */
	public Resolved resolveTotalFactImpsShort(List<List<String>> sheetRows, List<List<String>> adjRows,
	                                          CampaignData data) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, "Fact total impressions short:");
		if (fromAdj != null) {
			return new Resolved("Fact total impressions short:", fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, "Fact total impressions short:");
		if (fromSheet != null) {
			return new Resolved("Fact total impressions short:", fromSheet, "sheet");
		}
		if (data == null || data.totals() == null) {
			return new Resolved(FACT_IMPS_SHORT_AUTO_LABEL, null, "not_found");
		}
		return new Resolved(FACT_IMPS_SHORT_AUTO_LABEL, fmt.compactUpper(data.totals().imps()), "adj");
	}
}
