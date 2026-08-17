package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
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

	@Override
	public BreakdownSectionInputs<AudienceInsightInput> readAudienceInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken) {
		Set<Integer> tacticNums = audienceTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of());
		}
		Map<Integer, AudienceTable> tables =
				sheetHelper.readAudienceTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, AudienceInsightInput> inputs = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			putTableValues(
					values, tacticNum, tables.getOrDefault(tacticNum, AudienceTable.EMPTY), flatReplacements);
			AudienceTable table = sanitized(tables.getOrDefault(tacticNum, AudienceTable.EMPTY));
			if (table.isEmpty()) {
				log.info("[audience] tactic {} enabled Audience analysis but its block is empty — "
						+ "slide ships without copy", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			inputs.put(tacticNum, new AudienceInsightInput(tacticNum, name, table));
		}
		return new BreakdownSectionInputs<>(tacticNums, inputs, values, List.of());
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

		// The top segment is not emitted on its own: the slide has no {{aud_N_top_segment}} tile, and the
		// same row is already written below as segment row 1.
		for (int i = 1; i <= SEGMENT_ROWS; i++) {
			AudienceSegmentRow row = i <= table.segmentRows().size() ? table.segmentRows().get(i - 1) : null;
			values.put("{{aud_" + tacticNum + "_" + i + "}}", row == null ? DASH : orDash(row.segment()));
			values.put("{{aud_in_" + tacticNum + "_" + i + "}}", row == null ? DASH : orDash(row.affinityIndex()));
		}

		// Reach, frequency and the primary engagement rate are deliberately not re-issued under
		// {{aud_N_reach}}/{{aud_N_freq}}/{{aud_N_engaged}}: the Audience Analysis slide carries no such
		// tiles, and the figures already ship on the tactic's own slide as
		// {{tactic N reach}}/{{tactic N f}}/{{tactic N KPI}}.
		putAgeBucketShares(values, tacticNum, table.ageRows(),
				tacticImpressions(flatReplacements, tacticNum));
	}

	/**
	 * Reads the tactic's already-resolved total impressions out of the deck's placeholder map, the
	 * denominator every {@code {{age_N_<bucket>}}} share is computed against. The value arrives
	 * group-formatted ({@code "1,234,567"}), so it is parsed the same way a sheet cell is.
	 *
	 * @param flatReplacements the deck's resolved placeholder map
	 * @param tacticNum        the tactic whose total is wanted
	 * @return the tactic's total impressions, or {@code 0} when the token never resolved to a number
	 */
	double tacticImpressions(Map<String, String> flatReplacements, int tacticNum) {
		if (flatReplacements == null) {
			return 0;
		}
		return parseImpressions(flatReplacements.get("{{tactic " + tacticNum + " imps}}"));
	}

	/** Canonical age-bucket labels, in slide order, matched against the sheet's pre-filled age column. */
	private static final List<String> AGE_BUCKETS = List.of("18-24", "25-34", "35-44", "45-54", "55-64", "65+");

	/**
	 * Slide token suffix for each entry in {@link #AGE_BUCKETS}, in the same order. Each suffix is the
	 * bucket's opening age alone ({@code 18}, {@code 25}, …), which is what the deck template's
	 * {@code {{age_N_18}}} … {@code {{age_N_65}}} slots are named — the full range never appears in a
	 * token name because the slide already prints the range as its own row label.
	 */
	private static final List<String> AGE_BUCKET_TOKEN_SUFFIXES =
			List.of("18", "25", "35", "45", "55", "65");

	/**
	 * Computes each canonical age bucket's share of the tactic's impressions from the sheet's
	 * hand-entered age-distribution rows, and writes the six {@code {{age_N_<bucket>}}} tokens. The age
	 * rows never appear on the slide as their own table (the master renders them as an embedded chart
	 * instead), but the same impressions numbers are the only source for these per-bucket percentages.
	 *
	 * <p>The denominator is the tactic's own total impressions ({@code {{tactic N imps}}}), not the sum
	 * of the age rows: the buckets are what the platform could resolve an age for, so summing them makes
	 * every bucket a share of tracked-only traffic and the six percentages always add to 100%, which
	 * overstates each bucket by however much of the tactic went unattributed. Against the tactic total
	 * the six shares add to the tactic's age-coverage instead, which is the honest figure. When the
	 * tactic total is missing or unparseable the row sum is used as a fallback — a slightly overstated
	 * percentage still beats dashing the whole distribution.
	 *
	 * @param values    the accumulating token → value map
	 * @param tacticNum the tactic whose tokens are being written
	 * @param ageRows   the tactic's filled age-distribution rows
	 * @param tacticImps the tactic's total impressions, or {@code 0} when it did not resolve
	 */
	void putAgeBucketShares(
			Map<String, String> values, int tacticNum, List<AudienceAgeRow> ageRows, double tacticImps) {
		Map<String, Double> impsByBucket = new LinkedHashMap<>();
		double rowSum = 0;
		for (AudienceAgeRow row : ageRows) {
			double imps = parseImpressions(row.impressions());
			rowSum += imps;
			String bucket = normalizeAgeBucket(row.ageGroup());
			if (bucket != null) {
				impsByBucket.merge(bucket, imps, Double::sum);
			}
		}
		double total = tacticImps > 0 ? tacticImps : rowSum;
		if (tacticImps <= 0 && rowSum > 0) {
			log.info("[audience] tactic {} has no usable {{{{tactic {} imps}}}} — age shares fall back to the "
					+ "age rows' own total", tacticNum, tacticNum);
		}
		for (int i = 0; i < AGE_BUCKETS.size(); i++) {
			Double imps = impsByBucket.get(AGE_BUCKETS.get(i));
			String value = imps == null || total <= 0 ? DASH : Math.round(imps / total * 100) + "%";
			values.put("{{age_" + tacticNum + "_" + AGE_BUCKET_TOKEN_SUFFIXES.get(i) + "}}", value);
		}
	}

	/**
	 * Matches a sheet age-group label against the six canonical buckets, tolerating the en/em-dash
	 * Sheets sometimes substitutes for a hyphen and surrounding whitespace.
	 *
	 * @param raw the sheet's age-group cell text
	 * @return the matching canonical bucket label, or {@code null} when it matches none
	 */
	String normalizeAgeBucket(String raw) {
		if (raw == null) {
			return null;
		}
		String cleaned = raw.trim().replace('–', '-').replace('—', '-').replaceAll("\\s+", "");
		for (String bucket : AGE_BUCKETS) {
			if (bucket.equalsIgnoreCase(cleaned)) {
				return bucket;
			}
		}
		return cleaned.equalsIgnoreCase("65plus") ? "65+" : null;
	}

	/**
	 * Parses an age row's impressions cell into a number, stripping grouping separators and any other
	 * non-numeric decoration.
	 *
	 * @param raw the raw impressions cell text
	 * @return the parsed impressions count, or {@code 0} when the cell holds no usable number
	 */
	double parseImpressions(String raw) {
		if (raw == null) {
			return 0;
		}
		String cleaned = raw.replaceAll("[^0-9.]", "");
		if (cleaned.isEmpty()) {
			return 0;
		}
		try {
			return Double.parseDouble(cleaned);
		} catch (NumberFormatException ex) {
			return 0;
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
	@Override
	public List<String> writeAudienceInsights(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> insights, Map<String, String> flatReplacements) {
		List<String> warnings = new ArrayList<>();
		for (Integer tacticNum : tactics) {
			List<String> fields = insights.getOrDefault(tacticNum, List.of());
			// A tactic we did send that came back with nothing ships blank copy, which on the slide is
			// indistinguishable from "the user filled nothing in" — so say so, in the log and on the card.
			if (fields.isEmpty() && sentTactics.contains(tacticNum)) {
				String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
				log.warn("[audience] tactic {} had audience data but Claude returned no copy — "
						+ "slide ships with blank fields (see the [claude:BatchConclusions] log line above "
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
