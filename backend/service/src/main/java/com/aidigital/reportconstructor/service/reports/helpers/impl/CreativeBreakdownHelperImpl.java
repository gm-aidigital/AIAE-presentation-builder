package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.CreativeBreakdownHelper;
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
 * Spring bean implementation of {@link CreativeBreakdownHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeBreakdownHelperImpl implements CreativeBreakdownHelper {

	/**
	 * Creative rows the master slide's table carries. Every slot is always written — with the sheet's
	 * value or an em-dash — so no slot can ship as a raw {@code {{tactic n.x top creative …}}} token.
	 */
	private static final int CREATIVE_ROWS = 5;

	/** KEY TAKEAWAYS bullets the master slide carries. */
	private static final int TAKEAWAY_COUNT = 4;

	/**
	 * Written into a creative row or stat tile the user left blank. Matches the em-dash the sheet's own
	 * unused-slot trim uses, so an unfilled cell reads the same in the workbook and on the slide.
	 */
	private static final String DASH = "—";

	/**
	 * Matches a cell still holding the template's own {@code {{…}}} hint text. Unlike the publisher table
	 * — whose cells the template leaves empty — the creative block ships with its tokens pre-typed as
	 * hints ({@code {{cr_live_n}}}), so a user who did not overwrite one leaves a literal token behind.
	 * Treating that as unfilled is what keeps a raw token off the slide.
	 */
	private static final Pattern UNFILLED_HINT = Pattern.compile("^\\{\\{.*}}$");

	private final ReportSheetHelper sheetHelper;
	private final BreakdownSelectionResolver breakdownResolver;
	private final ClaudeClient claude;

	@Override
	public Map<String, String> buildCreativeValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken) {
		Set<Integer> tacticNums = creativeTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return Map.of();
		}
		Map<Integer, CreativeTable> tables =
				sheetHelper.readCreativeTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			putTableValues(values, tacticNum, tables.getOrDefault(tacticNum, CreativeTable.empty()), flatReplacements);
		}
		putTakeaways(values, tacticNums, tables, flatReplacements, brief);
		return values;
	}

	/**
	 * Selects the tactics that enabled the Creative analysis breakdown, in ascending order so the Claude
	 * chunks and the log lines follow the deck's own tactic order.
	 *
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return the tactic numbers with Creative analysis enabled (empty when none did)
	 */
	Set<Integer> creativeTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> tacticNums = new TreeSet<>();
		if (enabledByTactic == null) {
			return tacticNums;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && enabled != null && enabled.contains(BreakdownType.CREATIVE)) {
				tacticNums.add(tacticNum);
			}
		});
		return tacticNums;
	}

	/**
	 * Writes one tactic's slide tokens: the heading and KPI type carried over from the deck's own
	 * placeholder map (which the copies' fill pass can no longer reach), the four stat tiles, and all
	 * {@link #CREATIVE_ROWS} table rows, em-dashing the slots the user left blank.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNum        the tactic whose tokens are being written
	 * @param table            the tactic's creative block as read from the sheet
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putTableValues(
			Map<String, String> values, int tacticNum, CreativeTable table, Map<String, String> flatReplacements) {
		// The heading and KPI type already resolved for the tactic's main slide; the breakdown copy did not
		// exist when that pass ran, so the same values are re-issued here rather than re-derived.
		putIfPresent(values, "{{tactic " + tacticNum + "}}", flatReplacements);
		putIfPresent(values, "{{tactic " + tacticNum + " KPI type}}", flatReplacements);

		values.put("{{cr_live_" + tacticNum + "}}", orDash(table.creativesLive()));
		values.put("{{cr_bKPI_" + tacticNum + "}}", orDash(table.bestKpi()));
		values.put("{{cr_aKPI_" + tacticNum + "}}", orDash(table.avgKpi()));
		values.put("{{tactic " + tacticNum + " top creative name}}", orDash(table.topCreative()));

		for (int i = 1; i <= CREATIVE_ROWS; i++) {
			CreativeRow row = i <= table.rows().size() ? table.rows().get(i - 1) : null;
			String prefix = "{{tactic " + tacticNum + "." + i + " top creative ";
			values.put("{{tactic " + tacticNum + " top creative name " + tacticNum + "." + i + "}}",
					row == null ? DASH : orDash(row.name()));
			values.put(prefix + "imps}}", row == null ? DASH : orDash(row.impressions()));
			values.put(prefix + "ctr}}", row == null ? DASH : orDash(row.ctr()));
			values.put(prefix + "vcr}}", row == null ? DASH : orDash(row.vcr()));
			values.put(prefix + "spend}}", row == null ? DASH : orDash(row.spend()));
		}
	}

	/**
	 * Asks Claude for the takeaway bullets of every tactic whose block carries data, and writes them.
	 * Tactics with a blank block are never sent — there is nothing to observe and the copy would be
	 * invented — and their bullets are blanked instead, as are those of a tactic Claude returned nothing
	 * for.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNums       the tactics that enabled the Creative analysis breakdown
	 * @param tables           each tactic's creative block
	 * @param flatReplacements the deck's resolved placeholder map, source of the tactic names and KPI types
	 * @param brief            free-text campaign brief passed to Claude for industry context
	 */
	void putTakeaways(
			Map<String, String> values, Set<Integer> tacticNums, Map<Integer, CreativeTable> tables,
			Map<String, String> flatReplacements, String brief) {
		List<CreativeTakeawayInput> inputs = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			CreativeTable table = sanitized(tables.getOrDefault(tacticNum, CreativeTable.empty()));
			if (table.isEmpty()) {
				log.info("[creatives] tactic {} enabled Creative analysis but its block is empty — "
						+ "slide ships without takeaways", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			String kpiType = flatReplacements.getOrDefault("{{tactic " + tacticNum + " KPI type}}", "");
			inputs.add(new CreativeTakeawayInput(tacticNum, name, kpiType, table));
		}
		Map<Integer, List<String>> takeaways = inputs.isEmpty()
				? Map.of() : claude.batchCreativeTakeaways(inputs, brief);

		for (Integer tacticNum : tacticNums) {
			List<String> bullets = takeaways.getOrDefault(tacticNum, List.of());
			// A tactic we did send that came back with nothing ships blank bullets, which on the slide is
			// indistinguishable from "the user filled nothing in". Say so, or the next blank KEY TAKEAWAYS
			// is a guessing game between an empty block, a failed call and an unparseable reply.
			if (bullets.isEmpty() && inputs.stream().anyMatch(input -> input.tacticNum() == tacticNum)) {
				log.warn("[creatives] tactic {} had creative data but Claude returned no takeaways — "
						+ "slide ships with blank bullets (see the [claude:BatchCreatives] log line above "
						+ "for the cause)", tacticNum);
			}
			for (int i = 1; i <= TAKEAWAY_COUNT; i++) {
				String bullet = i <= bullets.size() ? bullets.get(i - 1) : null;
				values.put("{{cr_takeaway_tactic " + tacticNum + "_" + i + "}}", bullet == null ? "" : bullet);
			}
		}
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
	CreativeTable sanitized(CreativeTable table) {
		return new CreativeTable(
				filled(table.creativesLive()), filled(table.bestKpi()),
				filled(table.avgKpi()), filled(table.topCreative()), table.rows());
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
