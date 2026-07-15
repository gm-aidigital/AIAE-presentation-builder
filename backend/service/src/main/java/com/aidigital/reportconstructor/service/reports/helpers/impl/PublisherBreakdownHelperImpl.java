package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.PublisherBreakdownHelper;
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

/**
 * Spring bean implementation of {@link PublisherBreakdownHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublisherBreakdownHelperImpl implements PublisherBreakdownHelper {

	/**
	 * Publisher rows the master slide's table carries. Every slot is always written — with the sheet's
	 * value or an em-dash — so no slot can ship as a raw {@code {{publisher_N.x}}} token.
	 */
	private static final int PUBLISHER_ROWS = 15;

	/** KEY OBSERVATIONS bullets the master slide carries. */
	private static final int OBSERVATION_COUNT = 4;

	/**
	 * Written into a publisher row the user left blank. Matches the em-dash the sheet's own unused-slot
	 * trim uses, so an unfilled row reads the same in the workbook and on the slide.
	 */
	private static final String DASH = "—";

	private final ReportSheetHelper sheetHelper;
	private final BreakdownSelectionResolver breakdownResolver;
	private final ClaudeClient claude;

	@Override
	public Map<String, String> buildPublisherValues(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String brief, String userGoogleToken) {
		Set<Integer> tacticNums = publisherTactics(breakdownResolver.resolve(selections));
		if (tacticNums.isEmpty()) {
			return Map.of();
		}
		Map<Integer, List<PublisherRow>> tables =
				sheetHelper.readPublisherTables(sheetUrl, tacticNums, userGoogleToken);

		Map<String, String> values = new LinkedHashMap<>();
		for (Integer tacticNum : tacticNums) {
			List<PublisherRow> rows = tables.getOrDefault(tacticNum, List.of());
			putTableValues(values, tacticNum, rows, flatReplacements);
		}
		putObservations(values, tacticNums, tables, flatReplacements, brief);
		return values;
	}

	/**
	 * Selects the tactics that enabled the Top Publishers breakdown, in ascending order so the Claude
	 * chunks and the log lines follow the deck's own tactic order.
	 *
	 * @param enabledByTactic 1-based tactic number → the breakdown sections that tactic enabled
	 * @return the tactic numbers with Top Publishers enabled (empty when none did)
	 */
	Set<Integer> publisherTactics(Map<Integer, Set<BreakdownType>> enabledByTactic) {
		Set<Integer> tacticNums = new TreeSet<>();
		if (enabledByTactic == null) {
			return tacticNums;
		}
		enabledByTactic.forEach((tacticNum, enabled) -> {
			if (tacticNum != null && enabled != null && enabled.contains(BreakdownType.TOP_PUBLISHERS)) {
				tacticNums.add(tacticNum);
			}
		});
		return tacticNums;
	}

	/**
	 * Writes one tactic's table tokens: the block heading and total (carried over from the deck's own
	 * placeholder map, which the copies' fill pass can no longer reach) and all
	 * {@link #PUBLISHER_ROWS} rows, em-dashing the slots the user left blank.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNum        the tactic whose tokens are being written
	 * @param rows             the tactic's filled publisher rows, in sheet order
	 * @param flatReplacements the deck's resolved placeholder map
	 */
	void putTableValues(
			Map<String, String> values, int tacticNum, List<PublisherRow> rows,
			Map<String, String> flatReplacements) {
		// The heading and total already resolved for the tactic's main slide; the breakdown copy did not
		// exist when that pass ran, so the same values are re-issued here rather than re-derived.
		putIfPresent(values, "{{tactic " + tacticNum + "}}", flatReplacements);
		putIfPresent(values, "{{tactic " + tacticNum + " imps}}", flatReplacements);
		for (int i = 1; i <= PUBLISHER_ROWS; i++) {
			PublisherRow row = i <= rows.size() ? rows.get(i - 1) : null;
			values.put("{{publisher_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.name()));
			values.put("{{pub_imp_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.impressions()));
			values.put("{{pub_sov_" + tacticNum + "." + i + "}}", row == null ? DASH : orDash(row.shareOfVoice()));
		}
	}

	/**
	 * Asks Claude for the observation bullets of every tactic whose table has rows, and writes them.
	 * Tactics with an empty table are never sent — there is nothing to observe and any copy would be
	 * invented — and their bullets are blanked instead, as are those of a tactic Claude returned nothing
	 * for.
	 *
	 * @param values           the accumulating token → value map
	 * @param tacticNums       the tactics that enabled the Top Publishers breakdown
	 * @param tables           each tactic's filled publisher rows
	 * @param flatReplacements the deck's resolved placeholder map, source of the tactic names
	 * @param brief            free-text campaign brief passed to Claude for audience context
	 */
	void putObservations(
			Map<String, String> values, Set<Integer> tacticNums, Map<Integer, List<PublisherRow>> tables,
			Map<String, String> flatReplacements, String brief) {
		List<PublisherObservationInput> inputs = new ArrayList<>();
		for (Integer tacticNum : tacticNums) {
			List<PublisherRow> rows = tables.getOrDefault(tacticNum, List.of());
			if (rows.isEmpty()) {
				log.info("[publishers] tactic {} enabled Top Publishers but its table is empty — "
						+ "slide ships without observations", tacticNum);
				continue;
			}
			String name = flatReplacements.getOrDefault("{{tactic " + tacticNum + "}}", "Tactic " + tacticNum);
			inputs.add(new PublisherObservationInput(tacticNum, name, rows));
		}
		Map<Integer, List<String>> observations = inputs.isEmpty()
				? Map.of() : claude.batchPublisherObservations(inputs, brief);

		for (Integer tacticNum : tacticNums) {
			List<String> bullets = observations.getOrDefault(tacticNum, List.of());
			for (int i = 1; i <= OBSERVATION_COUNT; i++) {
				String bullet = i <= bullets.size() ? bullets.get(i - 1) : null;
				values.put("{{publishers_observation_" + tacticNum + "_" + i + "}}", bullet == null ? "" : bullet);
			}
		}
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
	 * Returns the value, or an em-dash when the user left the cell blank, so a half-filled row never
	 * shows one populated cell beside a raw token.
	 *
	 * @param value the cell value read back from the sheet
	 * @return the trimmed value, or {@link #DASH} when it is null or blank
	 */
	String orDash(String value) {
		return value == null || value.isBlank() ? DASH : value.trim();
	}
}
