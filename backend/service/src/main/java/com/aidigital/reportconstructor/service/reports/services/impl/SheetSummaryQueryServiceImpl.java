package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.SheetSummaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Default {@link SheetSummaryQueryService}: reads the generated workbook's first tab back into a
 * placeholder map (the same read/parse the slides-from-sheet pipeline uses) and projects each
 * tactic's plan/fact figures into a {@link SheetSummaryRow}.
 */
@Service
@RequiredArgsConstructor
public class SheetSummaryQueryServiceImpl implements SheetSummaryQueryService {

	/** Max tactics the report template carries (summary rows and "Main slide" blocks). */
	private static final int MAX_TACTICS = 28;

	/** Per-tactic placeholder tokens, assembled as {@code "{{tactic " + n + suffix + "}}"}. */
	private static final String TOKEN_PREFIX = "{{tactic ";
	private static final String TOKEN_SUFFIX = "}}";
	private static final String SUFFIX_NAME = "";
	private static final String SUFFIX_IMPS_PLAN = " imps plan";
	private static final String SUFFIX_IMPS_FACT = " imps";
	private static final String SUFFIX_CLICKS_PLAN = " clicks plan";
	private static final String SUFFIX_CLICKS_FACT = " clicks";
	private static final String SUFFIX_COMPLETIONS_PLAN = " completions plan";
	private static final String SUFFIX_COMPLETIONS_FACT = " complitions";
	private static final String SUFFIX_SPEND_PLAN = " spend plan";
	private static final String SUFFIX_SPEND_FACT = " spend";

	/** The summary table's "Rate type" cell token, {@code {{rate type N}}} — not a per-tactic suffix. */
	private static final String RATE_TYPE_TOKEN_PREFIX = "{{rate type ";

	/** The em-dash a resolver writes for an unresolved figure (see {@code Resolved}); not a real value. */
	private static final String UNRESOLVED_DASH = "—";

	private final ReportSheetHelper sheetHelper;
	private final SheetPlaceholderReader placeholderReader;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;

	@Override
	public List<SheetSummaryRow> readSummary(String sheetUrl, String callerUserId) {
		UserGoogleTokenProvider tokens = userGoogleTokens.getIfAvailable();
		String userGoogleToken = tokens == null ? null : tokens.googleAccessToken(callerUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(sheetUrl, userGoogleToken);
		Map<String, String> values = placeholderReader.readPlaceholders(grid);

		int count = tacticCount(values);
		List<SheetSummaryRow> rows = new ArrayList<>(count);
		for (int n = 1; n <= count; n++) {
			rows.add(new SheetSummaryRow(
					values.get(token(n, SUFFIX_NAME)),
					unitPlan(values, n),
					unitFact(values, n),
					values.get(token(n, SUFFIX_SPEND_PLAN)),
					values.get(token(n, SUFFIX_SPEND_FACT))));
		}
		return rows;
	}

	/**
	 * Resolves tactic {@code n}'s planned main-unit figure: whichever unit the tactic's "Rate type"
	 * cell says it was bought in — clicks plan for CPC, completions plan for CPV, impressions plan for
	 * CPM. Every plan column is populated for every rate type now (the non-bought ones are derived
	 * through the CTR/VCR benchmarks), so the rate type is what picks the right one; a workbook with no
	 * rate type at all (EOC) falls back to whichever plan cell carries a value.
	 *
	 * @param values the placeholder map read from the workbook
	 * @param n      1-based tactic number
	 * @return the planned cell for the bought unit, or {@code null} when it carries no resolved value
	 */
	String unitPlan(Map<String, String> values, int n) {
		String suffix = boughtUnitPlanSuffix(values, n);
		if (suffix != null) {
			return values.get(token(n, suffix));
		}
		String clicksPlan = values.get(token(n, SUFFIX_CLICKS_PLAN));
		if (hasValue(clicksPlan)) {
			return clicksPlan;
		}
		String completionsPlan = values.get(token(n, SUFFIX_COMPLETIONS_PLAN));
		if (hasValue(completionsPlan)) {
			return completionsPlan;
		}
		return values.get(token(n, SUFFIX_IMPS_PLAN));
	}

	/**
	 * Resolves tactic {@code n}'s delivered main-unit figure, matching whichever unit
	 * {@link #unitPlan} chose — clicks fact for a CPC tactic, completions fact for CPV, impressions
	 * fact otherwise.
	 *
	 * @param values the placeholder map read from the workbook
	 * @param n      1-based tactic number
	 * @return the delivered clicks/completions/impressions cell matching the planned unit, or
	 * {@code null} when that cell is absent
	 */
	String unitFact(Map<String, String> values, int n) {
		String planSuffix = boughtUnitPlanSuffix(values, n);
		if (SUFFIX_CLICKS_PLAN.equals(planSuffix)
				|| (planSuffix == null && hasValue(values.get(token(n, SUFFIX_CLICKS_PLAN))))) {
			return values.get(token(n, SUFFIX_CLICKS_FACT));
		}
		if (SUFFIX_COMPLETIONS_PLAN.equals(planSuffix)
				|| (planSuffix == null && hasValue(values.get(token(n, SUFFIX_COMPLETIONS_PLAN))))) {
			return values.get(token(n, SUFFIX_COMPLETIONS_FACT));
		}
		return values.get(token(n, SUFFIX_IMPS_FACT));
	}

	/**
	 * Maps tactic {@code n}'s "Rate type" cell to the plan-column suffix that rate type is bought in.
	 *
	 * @param values the placeholder map read from the workbook
	 * @param n      1-based tactic number
	 * @return the clicks/completions/impressions plan suffix, or {@code null} when the workbook carries
	 * no usable rate type for the tactic (an EOC report, or an unfilled cell)
	 */
	String boughtUnitPlanSuffix(Map<String, String> values, int n) {
		String rateType = values.get(RATE_TYPE_TOKEN_PREFIX + n + TOKEN_SUFFIX);
		if (rateType == null) {
			return null;
		}
		return switch (rateType.trim().toUpperCase(Locale.ROOT)) {
			case "CPC" -> SUFFIX_CLICKS_PLAN;
			case "CPV" -> SUFFIX_COMPLETIONS_PLAN;
			case "CPM" -> SUFFIX_IMPS_PLAN;
			default -> null;
		};
	}

	/**
	 * Tells whether a cell read back from the workbook carries a real resolved figure, as opposed to
	 * being absent ({@code null}) or the em-dash a resolver writes for an unresolved value (e.g. a
	 * CPM tactic's "Clicks Plan" cell, which does not apply to it).
	 *
	 * @param cell the cell value read from the placeholder map, or {@code null} when absent
	 * @return {@code true} when the cell is present and is not the unresolved-value dash
	 */
	boolean hasValue(String cell) {
		return cell != null && !UNRESOLVED_DASH.equals(cell);
	}

	/**
	 * Builds a per-tactic placeholder token for the given tactic number and column suffix.
	 *
	 * @param n      1-based tactic number
	 * @param suffix the column suffix (e.g. {@code " spend plan"}; empty for the tactic name)
	 * @return the full token, e.g. {@code "{{tactic 2 spend plan}}"}
	 */
	String token(int n, String suffix) {
		return TOKEN_PREFIX + n + suffix + TOKEN_SUFFIX;
	}

	/**
	 * Counts the tactics present in the summary map: the number of contiguous {@code {{tactic n}}}
	 * name tokens starting at 1, clamped to {@link #MAX_TACTICS}.
	 *
	 * @param values the placeholder map read from the workbook
	 * @return the tactic count in {@code [0, MAX_TACTICS]}
	 */
	int tacticCount(Map<String, String> values) {
		int count = 0;
		for (int n = 1; n <= MAX_TACTICS; n++) {
			String name = values.get(token(n, SUFFIX_NAME));
			if (name == null || name.isBlank()) {
				break;
			}
			count = n;
		}
		return count;
	}
}
