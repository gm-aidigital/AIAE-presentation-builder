package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.TacticPacing;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingInput;
import com.aidigital.reportconstructor.service.reports.dto.TacticPacingMetric;
import com.aidigital.reportconstructor.service.reports.helpers.PacingNarrativeAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link PacingNarrativeAssembler}: reads the channel slide's METRIC table out of the placeholder
 * map, and writes the four narrative tokens back onto it.
 */
@Component
@RequiredArgsConstructor
public class PacingNarrativeAssemblerImpl implements PacingNarrativeAssembler {

	/** Value an unresolved or unwritten narrative token flattens to. */
	static final String DASH = "—";

	/**
	 * The METRIC table's rows in the order the slide draws them, keyed as
	 * {@link SheetPlaceholderReaderImpl#METRIC_ROW_SUFFIXES} keys them. The order lives here rather than
	 * with the suffixes because it is a property of the slide, not of the workbook, while the suffixes are
	 * shared with the reader so a renamed column cannot mean two different things on the two sides.
	 */
	static final List<String> METRIC_ROW_ORDER =
			List.of("impressions", "ctr", "clicks", "reach", "cpm", "spend");

	/** The row labels the prompt sees, in {@link #METRIC_ROW_ORDER} order. */
	static final Map<String, String> METRIC_ROW_LABELS = Map.of(
			"impressions", "Impressions",
			"ctr", "CTR",
			"clicks", "Clicks",
			"reach", "Reach",
			"cpm", "CPM",
			"spend", "Spend");

	/** Token suffix of the channel's KPI type, which tells the narrative what the channel is judged on. */
	static final String KPI_TYPE_SUFFIX = " KPI type";

	/** Token suffix of the "what worked" takeaway. */
	static final String WHAT_WORKED_TOKEN = "{{what worked pacing %d}}";

	/** Token suffix of the "watch outs" takeaway. */
	static final String WATCH_OUTS_TOKEN = "{{watch outs pacing %d}}";

	/** Token suffix of the "recommended action" takeaway. */
	static final String ACTIONS_TOKEN = "{{actions pacing %d}}";

	/** Token of the month-ahead directive. */
	static final String NEXT_MONTH_TOKEN = "{{pacing %d next month}}";

	@Override
	public List<TacticPacingInput> toInputs(Map<String, String> flat, int tacticCount) {
		List<TacticPacingInput> inputs = new ArrayList<>();
		if (flat == null) {
			return inputs;
		}
		for (int n = 1; n <= tacticCount; n++) {
			List<TacticPacingMetric> metrics = metricsOf(flat, n);
			if (metrics.isEmpty()) {
				continue;
			}
			inputs.add(new TacticPacingInput(
					n, value(flat, "{{tactic " + n + "}}"), value(flat, tacticToken(n, KPI_TYPE_SUFFIX)), metrics));
		}
		return inputs;
	}

	@Override
	public void write(Map<String, String> flat, int tacticCount, List<TacticPacing> narratives) {
		if (flat == null) {
			return;
		}
		Map<Integer, TacticPacing> byTactic = new LinkedHashMap<>();
		if (narratives != null) {
			for (TacticPacing narrative : narratives) {
				if (narrative != null) {
					byTactic.put(narrative.tacticNum(), narrative);
				}
			}
		}
		for (int n = 1; n <= tacticCount; n++) {
			TacticPacing narrative = byTactic.get(n);
			put(flat, WHAT_WORKED_TOKEN, n, narrative == null ? null : narrative.whatWorked());
			put(flat, WATCH_OUTS_TOKEN, n, narrative == null ? null : narrative.watchOuts());
			put(flat, ACTIONS_TOKEN, n, narrative == null ? null : narrative.actions());
			put(flat, NEXT_MONTH_TOKEN, n, narrative == null ? null : narrative.nextMonth());
		}
	}

	/**
	 * Writes one narrative token, leaving a value the map already carries in place.
	 *
	 * <p>Whatever is already there came from the workbook the user reviewed, and their own wording outranks
	 * a generated line. A missing reply dashes the token rather than leaving it raw, so the slide reads as
	 * "nothing to say here" instead of printing the token's own name.
	 *
	 * @param flat     the placeholder map to fill, mutated in place
	 * @param template the token's format string, taking the tactic number
	 * @param n        the 1-based tactic number
	 * @param value    the generated copy, or {@code null} when the call produced none
	 */
	void put(Map<String, String> flat, String template, int n, String value) {
		String token = String.format(template, n);
		String existing = flat.get(token);
		if (existing != null && !existing.isBlank() && !DASH.equals(existing.trim())) {
			return;
		}
		flat.put(token, value == null || value.isBlank() ? DASH : value);
	}

	/**
	 * Reads one tactic's METRIC rows out of the placeholder map, dropping a row whose every column is
	 * missing so an empty table yields no input at all.
	 *
	 * @param flat the resolved placeholder map
	 * @param n    the 1-based tactic number
	 * @return the tactic's populated METRIC rows in slide order
	 */
	List<TacticPacingMetric> metricsOf(Map<String, String> flat, int n) {
		List<TacticPacingMetric> metrics = new ArrayList<>();
		for (String row : METRIC_ROW_ORDER) {
			List<String> suffixes = SheetPlaceholderReaderImpl.METRIC_ROW_SUFFIXES.get(row);
			if (suffixes == null || suffixes.size() < 5) {
				continue;
			}
			List<String> columns = new ArrayList<>(5);
			for (String suffix : suffixes) {
				columns.add(value(flat, tacticToken(n, suffix)));
			}
			if (columns.stream().allMatch(Objects::isNull)) {
				continue;
			}
			metrics.add(new TacticPacingMetric(METRIC_ROW_LABELS.get(row),
					columns.get(0), columns.get(1), columns.get(2), columns.get(3), columns.get(4)));
		}
		return metrics;
	}

	/**
	 * Reads a token's value, treating a blank cell and a dash alike as "no figure" so a dashed source never
	 * reaches the prompt as if it were data.
	 *
	 * @param flat  the placeholder map being read
	 * @param token the token to read
	 * @return the trimmed value, or {@code null} when the token is absent, blank or dashed
	 */
	String value(Map<String, String> flat, String token) {
		String raw = flat.get(token);
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		return trimmed.isEmpty() || DASH.equals(trimmed) ? null : trimmed;
	}

	/**
	 * Builds a per-tactic token key.
	 *
	 * @param n      the 1-based tactic number
	 * @param suffix the token suffix, starting with a space
	 * @return the full {@code {{tactic n <suffix>}}} token
	 */
	String tacticToken(int n, String suffix) {
		return "{{tactic " + n + suffix + "}}";
	}
}
