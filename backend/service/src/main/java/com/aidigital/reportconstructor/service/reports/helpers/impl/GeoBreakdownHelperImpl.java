package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.GeoBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link GeoBreakdownHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoBreakdownHelperImpl implements GeoBreakdownHelper {

	/**
	 * Geo rows the master slide's table carries. Every slot is always written — with the sheet's value or
	 * an em-dash — so no slot can ship as a raw {@code {{geo_n.x}}} token.
	 */
	private static final int GEO_ROWS = 8;

	/** "WHAT THE MAP TELLS US" insight bullets the master slide carries. */
	private static final int INSIGHT_COUNT = 4;

	/**
	 * Strings Claude returns per tactic: the {@link #INSIGHT_COUNT} insight bullets plus one recommendation.
	 * The recommendation is the last element, written to {@code {{geo_N_reco}}}.
	 */
	private static final int GEO_BULLET_COUNT = INSIGHT_COUNT + 1;

	/**
	 * Written into a geo row or stat tile the user left blank. Matches the em-dash the sheet's own
	 * unused-slot trim uses, so an unfilled cell reads the same in the workbook and on the slide.
	 */
	private static final String DASH = "—";

	/**
	 * Matches a cell still holding the template's own {@code {{…}}} hint text. Like the creative block —
	 * and unlike the publisher table — the geo block ships with its stat-tile tokens pre-typed as hints
	 * ({@code {{geo_n_amount}}}), so a user who did not overwrite one leaves a literal token behind.
	 * Treating that as unfilled is what keeps a raw token off the slide.
	 */
	private static final Pattern UNFILLED_HINT = Pattern.compile("^\\{\\{.*}}$");

	private final ReportSheetHelper sheetHelper;
	private final BreakdownSelectionResolver breakdownResolver;
	private final ClaudeClient claude;

	@Override
	public BreakdownValues buildGeoValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken) {
		Set<Integer> tacticNums = geoTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return BreakdownValues.empty();
		}
		Map<Integer, GeoTable> tables =
				sheetHelper.readGeoTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			putTableValues(values, tacticNum, tables.getOrDefault(tacticNum, GeoTable.empty()), flatReplacements);
		}
		List<String> warnings = putInsights(values, tacticNums, tables, flatReplacements, brief);
		return new BreakdownValues(values, warnings);
	}

	/**
	 * Selects the tactics that enabled the Geo analysis breakdown, in ascending order so the Claude
	 * chunks and the log lines follow the deck's own tactic order.
	 *
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return the tactic numbers with Geo analysis enabled (empty when none did)
	 */
	Set<Integer> geoTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> tacticNums = new TreeSet<>();
		if (enabledByTactic == null) {
			return tacticNums;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && enabled != null && enabled.contains(BreakdownType.GEO)) {
				tacticNums.add(tacticNum);
			}
		});
		return tacticNums;
	}

	/**
	 * Writes one tactic's slide tokens: the heading and KPI type carried over from the deck's own
	 * placeholder map (which the copies' fill pass can no longer reach), the three stat tiles, and all
	 * {@link #GEO_ROWS} table rows, em-dashing the slots the user left blank.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNum        the tactic whose tokens are being written
	 * @param table            the tactic's geo block as read from the sheet
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putTableValues(
			Map<String, String> values, int tacticNum, GeoTable table, Map<String, String> flatReplacements) {
		// The heading and KPI type already resolved for the tactic's main slide; the breakdown copy did not
		// exist when that pass ran, so the same values are re-issued here rather than re-derived.
		putIfPresent(values, "{{tactic " + tacticNum + "}}", flatReplacements);
		putIfPresent(values, "{{tactic " + tacticNum + " KPI type}}", flatReplacements);

		values.put("{{geo_" + tacticNum + "_amount}}", orDash(table.marketsActivated()));
		values.put("{{geo_" + tacticNum + "_topgeo}}", orDash(table.topGeo()));
		values.put("{{geo_" + tacticNum + "_topkpi}}", orDash(table.topKpi()));

		for (int i = 1; i <= GEO_ROWS; i++) {
			GeoRow row = i <= table.rows().size() ? table.rows().get(i - 1) : null;
			values.put("{{geo_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.name()));
			values.put("{{geo_imp_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.impressions()));
			values.put("{{geo_kpi_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.kpi()));
		}
	}

	/**
	 * Asks Claude for the insight bullets and recommendation of every tactic whose block carries data, and
	 * writes them. Tactics with a blank block are never sent — there is nothing to observe and the copy
	 * would be invented — and their insights are blanked instead, as are those of a tactic Claude returned
	 * nothing for.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNums       the tactics that enabled the Geo analysis breakdown
	 * @param tables           each tactic's geo block
	 * @param flatReplacements the deck's resolved placeholder map, source of the tactic names and KPI types
	 * @param brief            free-text campaign brief passed to Claude for audience/goal context
	 * @return one warning per tactic that had geo data but came back without insights; empty when every
	 * tactic Claude was asked about answered
	 */
	List<String> putInsights(
			Map<String, String> values, Set<Integer> tacticNums, Map<Integer, GeoTable> tables,
			Map<String, String> flatReplacements, String brief) {
		List<GeoInsightInput> inputs = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			GeoTable table = sanitized(tables.getOrDefault(tacticNum, GeoTable.empty()));
			if (table.isEmpty()) {
				log.info("[geo] tactic {} enabled Geo analysis but its block is empty — "
						+ "slide ships without insights", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			String kpiType = flatReplacements.getOrDefault("{{tactic " + tacticNum + " KPI type}}", "");
			inputs.add(new GeoInsightInput(tacticNum, name, kpiType, table));
		}
		Map<Integer, List<String>> insights = inputs.isEmpty()
				? Map.of() : claude.batchGeoInsights(inputs, brief);

		List<String> warnings = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			List<String> bullets = insights.getOrDefault(tacticNum, List.of());
			// A tactic we did send that came back with nothing ships blank insights, which on the slide is
			// indistinguishable from "the user filled nothing in" — so say so, in the log and on the card.
			if (bullets.isEmpty() && inputs.stream().anyMatch(input -> input.tacticNum() == tacticNum)) {
				String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
				log.warn("[geo] tactic {} had geo data but Claude returned no insights — "
						+ "slide ships with blank bullets (see the [claude:BatchGeo] log line above "
						+ "for the cause)", tacticNum);
				warnings.add("Geo analysis – " + name
						+ ": WHAT THE MAP TELLS US is empty, Claude did not return it. The table itself is filled.");
			}
			for (int i = 1; i <= INSIGHT_COUNT; i++) {
				String bullet = i <= bullets.size() ? bullets.get(i - 1) : null;
				values.put("{{geo_insight_" + tacticNum + "." + i + "}}", bullet == null ? "" : bullet);
			}
			String reco = bullets.size() >= GEO_BULLET_COUNT ? bullets.get(GEO_BULLET_COUNT - 1) : null;
			values.put("{{geo_" + tacticNum + "_reco}}", reco == null ? "" : reco);
		}
		return warnings;
	}

	/**
	 * Strips the template's un-overwritten {@code {{…}}} hint text out of a block before it reaches Claude,
	 * so a stat tile the user never filled reads as absent rather than as a literal token Claude would try
	 * to interpret as a value. Rows are left alone: the table's cells ship empty in the template, so a row
	 * only exists when the user typed its name.
	 *
	 * @param table the block as read from the sheet
	 * @return the block with hint-only stat tiles blanked
	 */
	GeoTable sanitized(GeoTable table) {
		return new GeoTable(
				filled(table.marketsActivated()), filled(table.topGeo()), filled(table.topKpi()), table.rows());
	}

	/**
	 * Copies a token's already-resolved value across from the deck's placeholder map, skipping tokens the
	 * map never resolved so they fall through to the caller's renumber-only path rather than being blanked.
	 *
	 * @param values           the accumulating token → value map
	 * @param token            the token to copy
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putIfPresent(Map<String, String> values, String token, Map<String, String> flatReplacements) {
		String value = flatReplacements.get(token);
		if (value != null) {
			values.put(token, value);
		}
	}

	/**
	 * Returns the value the user actually typed, or an empty string when the cell is blank or still holds
	 * the template's own {@code {{…}}} hint text.
	 *
	 * @param value the cell value read back from the sheet
	 * @return the trimmed user-typed value, or an empty string when the cell is unfilled
	 */
	String filled(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String trimmed = value.trim();
		return UNFILLED_HINT.matcher(trimmed).matches() ? "" : trimmed;
	}

	/**
	 * Returns the value, or an em-dash when the user left the cell blank (or left the template's hint token
	 * in it), so a half-filled block never shows one populated cell beside a raw token.
	 *
	 * @param value the cell value read back from the sheet
	 * @return the trimmed value, or {@link #DASH} when it is unfilled
	 */
	String orDash(String value) {
		String trimmed = filled(value);
		return trimmed.isEmpty() ? DASH : trimmed;
	}
}
