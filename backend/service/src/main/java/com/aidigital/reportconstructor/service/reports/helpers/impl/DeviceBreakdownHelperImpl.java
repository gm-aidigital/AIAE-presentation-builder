package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.DeviceBreakdownHelper;
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
 * Spring bean implementation of {@link DeviceBreakdownHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceBreakdownHelperImpl implements DeviceBreakdownHelper {

	/**
	 * Slide token prefix of the "Mobile" device row ({@code {{mobile_imps_N}}}, …). The sheet's
	 * pre-filled device label, lower-cased, is matched against these so the row lands on the right
	 * slide tokens regardless of which rows the user actually filled in.
	 */
	private static final String MOBILE_PREFIX = "mobile";

	/** Slide token prefix of the "Connected TV" row ({@code {{ctv_imps_N}}}, …). */
	private static final String CTV_PREFIX = "ctv";

	/** Slide token prefix of the "Desktop" row ({@code {{desktop_imps_N}}}, …). */
	private static final String DESKTOP_PREFIX = "desktop";

	/** Slide token prefix of the "Tablet" row ({@code {{tablet_imps_N}}}, …). */
	private static final String TABLET_PREFIX = "tablet";

	/**
	 * Strings Claude returns per tactic, in slide order: the key takeaway, the "what worked" note, the
	 * watch-out and the recommended action.
	 */
	private static final int AI_FIELD_COUNT = 4;

	/**
	 * Written into a device metric or stat tile the user left blank. Matches the em-dash the sheet's own
	 * unused-slot trim uses, so an unfilled cell reads the same in the workbook and on the slide.
	 */
	private static final String DASH = "—";

	/**
	 * Matches a cell still holding the template's own {@code {{…}}} hint text. Like the geo and creative
	 * blocks, the device block ships with its stat-tile tokens pre-typed as hints ({@code {{dev_n_ctr}}},
	 * {@code {{top_dev_n}}}), so a user who did not overwrite one leaves a literal token behind. Treating
	 * that as unfilled is what keeps a raw token off the slide.
	 */
	private static final Pattern UNFILLED_HINT = Pattern.compile("^\\{\\{.*}}$");

	private final ReportSheetHelper sheetHelper;
	private final BreakdownSelectionResolver breakdownResolver;

	@Override
	public BreakdownSectionInputs<DeviceInsightInput> readDeviceInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken) {
		Set<Integer> tacticNums = deviceTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return new BreakdownSectionInputs<>(Set.of(), Map.of(), Map.of(), List.of());
		}
		Map<Integer, DeviceTable> tables =
				sheetHelper.readDeviceTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		Map<Integer, DeviceInsightInput> inputs = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			putTableValues(
					values, tacticNum, tables.getOrDefault(tacticNum, DeviceTable.EMPTY), flatReplacements);
			DeviceTable table = sanitized(tables.getOrDefault(tacticNum, DeviceTable.EMPTY));
			if (table.isEmpty()) {
				log.info("[device] tactic {} enabled Device breakdown but its block is empty — "
						+ "slide ships without copy", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			inputs.put(tacticNum, new DeviceInsightInput(tacticNum, name, table));
		}
		return new BreakdownSectionInputs<>(tacticNums, inputs, values, List.of());
	}

	/**
	 * Selects the tactics that enabled the Device breakdown, in ascending order so the Claude chunks and
	 * the log lines follow the deck's own tactic order.
	 *
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return the tactic numbers with the Device breakdown enabled (empty when none did)
	 */
	Set<Integer> deviceTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> tacticNums = new TreeSet<>();
		if (enabledByTactic == null) {
			return tacticNums;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && enabled != null && enabled.contains(BreakdownType.DEVICE)) {
				tacticNums.add(tacticNum);
			}
		});
		return tacticNums;
	}

	/**
	 * Writes one tactic's slide tokens: the heading carried over from the deck's own placeholder map
	 * (which the copies' fill pass can no longer reach), the five stat tiles, and the four fixed device
	 * rows, em-dashing the slots the user left blank.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNum        the tactic whose tokens are being written
	 * @param table            the tactic's device block as read from the sheet
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putTableValues(
			Map<String, String> values, int tacticNum, DeviceTable table, Map<String, String> flatReplacements) {
		// The heading already resolved for the tactic's main slide; the breakdown copy did not exist when
		// that pass ran, so the same value is re-issued here rather than re-derived.
		putIfPresent(values, "{{tactic " + tacticNum + "}}", flatReplacements);

		values.put("{{dev_" + tacticNum + "_ctr}}", orDash(table.highestCtr()));
		values.put("{{dev_" + tacticNum + "_vcr}}", orDash(table.bestCompletion()));
		values.put("{{dev_" + tacticNum + "_amount}}", orDash(table.devicesTracked()));
		values.put("{{top_dev_" + tacticNum + "}}", orDash(table.topDevice()));
		values.put("{{dev_proc_imps_" + tacticNum + "}}", orDash(table.topDeviceImpressionsPct()));

		Map<String, DeviceRow> rowsByDevice = rowsByDevice(table.rows());
		// The slide's Connected TV row shows a literal "—" for CTR (non-clickable inventory), so it carries
		// no {{ctv_ctr_N}} token; the other three devices carry the full metric set.
		putDeviceRow(values, tacticNum, MOBILE_PREFIX, rowsByDevice.get(MOBILE_PREFIX), true);
		putDeviceRow(values, tacticNum, CTV_PREFIX, rowsByDevice.get(CTV_PREFIX), false);
		putDeviceRow(values, tacticNum, DESKTOP_PREFIX, rowsByDevice.get(DESKTOP_PREFIX), true);
		putDeviceRow(values, tacticNum, TABLET_PREFIX, rowsByDevice.get(TABLET_PREFIX), true);
	}

	/**
	 * Writes one fixed device row's slide tokens — impressions, VCR and spend always, CTR only when the
	 * device carries one — em-dashing every metric of a device the user left out of the table so no slot
	 * can ship as a raw {@code {{<device>_imps_N}}} token.
	 *
	 * @param values     the accumulating token → value map
	 * @param tacticNum  the tactic whose tokens are being written
	 * @param prefix     the device's slide token prefix (e.g. {@code "mobile"})
	 * @param row        the device's sheet row, or {@code null} when the user did not fill it in
	 * @param includeCtr whether the device carries a {@code {{<prefix>_ctr_N}}} token
	 */
	void putDeviceRow(
			Map<String, String> values, int tacticNum, String prefix, DeviceRow row, boolean includeCtr) {
		values.put("{{" + prefix + "_imps_" + tacticNum + "}}", row == null ? DASH : orDash(row.impressions()));
		if (includeCtr) {
			values.put("{{" + prefix + "_ctr_" + tacticNum + "}}", row == null ? DASH : orDash(row.ctr()));
		}
		values.put("{{" + prefix + "_vcr_" + tacticNum + "}}", row == null ? DASH : orDash(row.vcr()));
		values.put("{{" + prefix + "_spend_" + tacticNum + "}}", row == null ? DASH : orDash(row.spend()));
	}

	/**
	 * Indexes a block's device rows by their lower-cased device label, so each fixed slide row can look
	 * up its own sheet row regardless of order and of which rows the user filled in. A duplicate label
	 * keeps the last row, which is the sheet's own precedence when a device appears twice.
	 *
	 * @param rows the block's filled device rows, in sheet order
	 * @return device label (lower-cased) → its row
	 */
	Map<String, DeviceRow> rowsByDevice(List<DeviceRow> rows) {
		Map<String, DeviceRow> byDevice = new LinkedHashMap<>();
		for (DeviceRow row : rows) {
			if (row.device() != null && !row.device().isBlank()) {
				byDevice.put(row.device().trim().toLowerCase(), row);
			}
		}
		return byDevice;
	}

	/**
	 * Asks Claude for the four device strings of every tactic whose block carries data, and writes them.
	 * Tactics with a blank block are never sent — there is nothing to observe and the copy would be
	 * invented — and their fields are blanked instead, as are those of a tactic Claude returned nothing
	 * for.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNums       the tactics that enabled the Device breakdown
	 * @param tables           each tactic's device block
	 * @param flatReplacements the deck's resolved placeholder map, source of the tactic names
	 * @param brief            free-text campaign brief passed to Claude for audience/goal context
	 * @return one warning per tactic that had device data but came back without copy; empty when every
	 * tactic Claude was asked about answered
	 */
	@Override
	public List<String> writeDeviceInsights(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> insights, Map<String, String> flatReplacements) {
		List<String> warnings = new ArrayList<>();
		for (Integer tacticNum : tactics) {
			List<String> fields = insights.getOrDefault(tacticNum, List.of());
			// A tactic we did send that came back with nothing ships blank copy, which on the slide is
			// indistinguishable from "the user filled nothing in" — so say so, in the log and on the card.
			if (fields.isEmpty() && sentTactics.contains(tacticNum)) {
				String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
				log.warn("[device] tactic {} had device data but Claude returned no copy — "
						+ "slide ships with blank fields (see the [claude:BatchConclusions] log line above "
						+ "for the cause)", tacticNum);
				warnings.add("Device breakdown – " + name
						+ ": the takeaway/what-worked/watch-out/recommendation are empty, Claude did not return "
						+ "them. The table itself is filled.");
			}
			values.put("{{dev_" + tacticNum + "_takeaway}}", fieldOrBlank(fields, 0));
			values.put("{{dev_" + tacticNum + "_worked}}", fieldOrBlank(fields, 1));
			values.put("{{dev_" + tacticNum + "_flag}}", fieldOrBlank(fields, 2));
			values.put("{{dev_" + tacticNum + "_reco}}", fieldOrBlank(fields, 3));
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
	 * would try to interpret as a value. Rows are left alone: the metric cells ship empty in the
	 * template, so a row only exists when the user typed an impressions value into it.
	 *
	 * @param table the block as read from the sheet
	 * @return the block with hint-only stat tiles blanked
	 */
	DeviceTable sanitized(DeviceTable table) {
		return new DeviceTable(
				filled(table.highestCtr()), filled(table.bestCompletion()), filled(table.devicesTracked()),
				filled(table.topDevice()), filled(table.topDeviceImpressionsPct()), table.rows());
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
