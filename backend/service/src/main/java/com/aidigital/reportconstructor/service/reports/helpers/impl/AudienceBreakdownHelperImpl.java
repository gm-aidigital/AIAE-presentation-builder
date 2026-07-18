package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
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
 * Spring bean implementation of {@link AudienceBreakdownHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceBreakdownHelperImpl implements AudienceBreakdownHelper {

	/**
	 * Top-audience-segment rows the master slide's table carries. Every slot is always written — with
	 * the sheet's value or an em-dash — so no slot can ship as a raw {@code {{aud_N_x}}} token.
	 */
	private static final int SEGMENT_ROWS = 5;

	/**
	 * Strings Claude returns per tactic, in slide order: the key takeaway, the "what worked" note, the
	 * watch-out and the recommended action.
	 */
	private static final int AI_FIELD_COUNT = 4;

	/**
	 * Written into a segment row or stat tile the user left blank. Matches the em-dash the sheet's own
	 * unused-slot trim uses, so an unfilled cell reads the same in the workbook and on the slide.
	 */
	private static final String DASH = "—";

	/**
	 * Matches a cell still holding the template's own {@code {{…}}} hint text. Like the geo and creative
	 * blocks, the audience block ships with its stat-tile tokens pre-typed as hints
	 * ({@code {{age_n_gr}}}, {@code {{gender_n}}}), so a user who did not overwrite one leaves a literal
	 * token behind. Treating that as unfilled is what keeps a raw token off the slide.
	 */
	private static final Pattern UNFILLED_HINT = Pattern.compile("^\\{\\{.*}}$");

	private final ReportSheetHelper sheetHelper;
	private final BreakdownSelectionResolver breakdownResolver;
	private final ClaudeClient claude;

	@Override
	public BreakdownValues buildAudienceValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken) {
		Set<Integer> tacticNums = audienceTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return BreakdownValues.EMPTY;
		}
		Map<Integer, AudienceTable> tables =
				sheetHelper.readAudienceTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			putTableValues(
					values, tacticNum, tables.getOrDefault(tacticNum, AudienceTable.EMPTY), flatReplacements);
		}
		List<String> warnings = putInsights(values, tacticNums, tables, flatReplacements, brief);
		return new BreakdownValues(values, warnings);
	}

	/**
	 * Selects the tactics that enabled the Audience analysis breakdown, in ascending order so the Claude
	 * chunks and the log lines follow the deck's own tactic order.
	 *
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return the tactic numbers with Audience analysis enabled (empty when none did)
	 */
	Set<Integer> audienceTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> tacticNums = new TreeSet<>();
		if (enabledByTactic == null) {
			return tacticNums;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && enabled != null && enabled.contains(BreakdownType.AUDIENCE)) {
				tacticNums.add(tacticNum);
			}
		});
		return tacticNums;
	}

	/**
	 * Writes one tactic's slide tokens: the heading and gender-split values carried over from the deck's
	 * own placeholder map (which the copies' fill pass can no longer reach), the two stat tiles, and all
	 * {@link #SEGMENT_ROWS} segment rows with their affinity indexes, em-dashing the slots the user left
	 * blank. The age-distribution rows are deliberately not emitted — the slide renders them as an
	 * embedded chart, not text.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNum        the tactic whose tokens are being written
	 * @param table            the tactic's audience block as read from the sheet
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putTableValues(
			Map<String, String> values, int tacticNum, AudienceTable table,
			Map<String, String> flatReplacements) {
		// The heading and the gender-split bars already resolved for the tactic's main slide; the
		// breakdown copy did not exist when that pass ran, so the same values are re-issued here rather
		// than re-derived. The gender split is generated onto the workbook's first tab, so it lives in the
		// placeholder map exactly like the tactic name.
		putIfPresent(values, "{{tactic " + tacticNum + "}}", flatReplacements);
		putIfPresent(values, "{{tactic " + tacticNum + " male}}", flatReplacements);
		putIfPresent(values, "{{tactic " + tacticNum + " female}}", flatReplacements);

		values.put("{{age_" + tacticNum + "_gr}}", orDash(table.ageDistribution()));
		values.put("{{gender_" + tacticNum + "}}", orDash(table.genderDemographics()));

		for (int i = 1; i <= SEGMENT_ROWS; i++) {
			AudienceSegmentRow row = i <= table.segmentRows().size() ? table.segmentRows().get(i - 1) : null;
			values.put("{{aud_" + tacticNum + "_" + i + "}}", row == null ? DASH : orDash(row.segment()));
			values.put("{{aud_in_" + tacticNum + "_" + i + "}}", row == null ? DASH : orDash(row.affinityIndex()));
		}
	}

	/**
	 * Asks Claude for the four audience strings of every tactic whose block carries data, and writes
	 * them. Tactics with a blank block are never sent — there is nothing to observe and the copy would be
	 * invented — and their fields are blanked instead, as are those of a tactic Claude returned nothing
	 * for.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNums       the tactics that enabled the Audience analysis breakdown
	 * @param tables           each tactic's audience block
	 * @param flatReplacements the deck's resolved placeholder map, source of the tactic names
	 * @param brief            free-text campaign brief passed to Claude for audience/goal context
	 * @return one warning per tactic that had audience data but came back without copy; empty when every
	 * tactic Claude was asked about answered
	 */
	List<String> putInsights(
			Map<String, String> values, Set<Integer> tacticNums, Map<Integer, AudienceTable> tables,
			Map<String, String> flatReplacements, String brief) {
		List<AudienceInsightInput> inputs = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			AudienceTable table = sanitized(tables.getOrDefault(tacticNum, AudienceTable.EMPTY));
			if (table.isEmpty()) {
				log.info("[audience] tactic {} enabled Audience analysis but its block is empty — "
						+ "slide ships without copy", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			inputs.add(new AudienceInsightInput(tacticNum, name, table));
		}
		Map<Integer, List<String>> insights = inputs.isEmpty()
				? Map.of() : claude.batchAudienceInsights(inputs, brief);

		List<String> warnings = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			List<String> fields = insights.getOrDefault(tacticNum, List.of());
			// A tactic we did send that came back with nothing ships blank copy, which on the slide is
			// indistinguishable from "the user filled nothing in" — so say so, in the log and on the card.
			if (fields.isEmpty() && inputs.stream().anyMatch(input -> input.tacticNum() == tacticNum)) {
				String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
				log.warn("[audience] tactic {} had audience data but Claude returned no copy — "
						+ "slide ships with blank fields (see the [claude:BatchAudience] log line above "
						+ "for the cause)", tacticNum);
				warnings.add("Audience analysis – " + name
						+ ": the takeaway/what-worked/watch-out/recommendation are empty, Claude did not return "
						+ "them. The tables themselves are filled.");
			}
			values.put("{{aud_" + tacticNum + "_takeaway}}", fieldOrBlank(fields, 0));
			values.put("{{aud_" + tacticNum + "_worked}}", fieldOrBlank(fields, 1));
			values.put("{{aud_" + tacticNum + "_flag}}", fieldOrBlank(fields, 2));
			values.put("{{aud_" + tacticNum + "_reco}}", fieldOrBlank(fields, 3));
		}
		return warnings;
	}

	/**
	 * Returns the Claude string at the given slide position, or an empty string when the reply was
	 * shorter — so a missing field renders blank rather than as a raw token.
	 *
	 * @param fields the tactic's returned strings, in slide order
	 * @param index  the zero-based slide position
	 * @return the field's value, or an empty string when absent
	 */
	String fieldOrBlank(List<String> fields, int index) {
		return index < fields.size() && fields.get(index) != null ? fields.get(index) : "";
	}

	/**
	 * Strips the template's un-overwritten {@code {{…}}} hint text out of a block before it reaches
	 * Claude, so a stat tile the user never filled reads as absent rather than as a literal token Claude
	 * would try to interpret as a value. Rows are left alone: the segment and impressions cells ship
	 * empty in the template, so a row only exists when the user typed into it.
	 *
	 * @param table the block as read from the sheet
	 * @return the block with hint-only stat tiles blanked
	 */
	AudienceTable sanitized(AudienceTable table) {
		return new AudienceTable(
				filled(table.ageDistribution()), filled(table.genderDemographics()),
				table.ageRows(), table.segmentRows());
	}

	/**
	 * Copies a token's already-resolved value across from the deck's placeholder map, skipping tokens the
	 * map never resolved so they fall through to the caller's renumber-only path rather than being
	 * blanked.
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
	 * Returns the value, or an em-dash when the user left the cell blank (or left the template's hint
	 * token in it), so a half-filled block never shows one populated cell beside a raw token.
	 *
	 * @param value the cell value read back from the sheet
	 * @return the trimmed value, or {@link #DASH} when it is unfilled
	 */
	String orDash(String value) {
		String trimmed = filled(value);
		return trimmed.isEmpty() ? DASH : trimmed;
	}
}
