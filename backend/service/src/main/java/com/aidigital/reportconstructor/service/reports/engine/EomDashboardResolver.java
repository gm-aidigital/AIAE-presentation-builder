package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fills the EOM dashboard tokens — the ones slides 3–10 of the end-of-month template print — from the
 * figures the placeholder map already carries.
 *
 * <p>Two dashboards, one row per tactic each. The pacing dashboard (slides 3–6) asks for the same five
 * numbers the report's summary table already holds: planned and actual budget, planned and actual
 * impressions, and the pacing between the two spend figures. The performance-vs-plan dashboard (slides
 * 7–10) asks for three: the tactic's KPI goal, its actual KPI and the distance between them.
 *
 * <p>Rather than resolving any of it a second time from the media plan, every column is derived here from
 * the tokens the summary table is built from, which on the sheet flow are the values the user reviewed and
 * corrected in the workbook. A number typed into the sheet therefore reaches the dashboards, which is the
 * whole point of the two-step flow — and no figure on them can disagree with the same figure printed
 * elsewhere in the deck.
 *
 * <p>Every campaign-wide total prefers the workbook's own totals row and falls back to summing the tactic
 * rows, so a template (or a flow) that carries no totals token still prints a total rather than a dash.
 */
@Component
@RequiredArgsConstructor
public class EomDashboardResolver {

	/** Rendered when a figure is missing or a pacing ratio has no positive plan to divide by. */
	static final String DASH = "—";

	/** Source token suffix carrying a tactic's planned spend. */
	static final String SRC_SPEND_PLAN = " spend plan";

	/** Source token suffix carrying a tactic's actual spend. */
	static final String SRC_SPEND = " spend";

	/** Source token suffix carrying a tactic's planned impressions (EOM: planned units). */
	static final String SRC_IMPS_PLAN = " imps plan";

	/** Source token suffix carrying a tactic's actual impressions. */
	static final String SRC_IMPS = " imps";

	/** Source token suffix carrying which KPI the tactic is judged on, as the deck spells it. */
	static final String SRC_KPI_TYPE = " KPI type";

	/** Source token suffix carrying the tactic's actual KPI, already picked by KPI type. */
	static final String SRC_KPI = " KPI";

	/** Source token suffix carrying a tactic's planned click-through rate. */
	static final String SRC_CTR_PLAN = " ctr plan";

	/** Source token suffix carrying a tactic's planned completion rate. */
	static final String SRC_VCR_PLAN = " vcr plan";

	/** KPI type spelling that means the tactic is judged on its click-through rate. */
	static final String KPI_TYPE_CTR = "CTR";

	/** Percentage points below which a tactic is printed as sitting exactly on its goal. */
	static final double ON_GOAL_TOLERANCE_PP = 0.005;

	/** Performance-dashboard token suffix for the KPI-goal column. */
	static final String OUT_KPI_GOAL = " KPI goal";

	/** Performance-dashboard token suffix for the distance between the actual KPI and its goal. */
	static final String OUT_VS_GOAL = " vs goal";

	/** Dashboard token suffix for the planned-budget column. */
	static final String OUT_PLANNED_BUDGET = " planned budget";

	/** Dashboard token suffix for the actual-budget column. */
	static final String OUT_FACT_BUDGET = " fact budget";

	/** Dashboard token suffix for the planned-impressions column. */
	static final String OUT_PLANNED_IMPS = " planned imps";

	/** Dashboard token suffix for the actual-impressions column. */
	static final String OUT_FACT_IMPS = " fact imps";

	/** Dashboard token suffix for the pacing column. */
	static final String OUT_PACING = " pacing";

	/** Totals-row token for the planned budget. */
	static final String TOTAL_PLANNED_BUDGET = "{{total planned budget}}";

	/** Totals-row token for the actual budget. */
	static final String TOTAL_FACT_BUDGET = "{{total fact budget}}";

	/** Totals-row token for the planned impressions. */
	static final String TOTAL_PLANNED_IMPS = "{{total planned imps}}";

	/** Totals-row token for the actual impressions. */
	static final String TOTAL_FACT_IMPS = "{{total fact imps}}";

	/** Totals-row token for the campaign pacing. */
	static final String TOTAL_PACING = "{{total pacing}}";

	/** Workbook tokens holding the campaign's planned spend, most authoritative first. */
	static final List<String> SRC_TOTAL_SPEND_PLAN = List.of("{{total_investment_plan}}");

	/** Workbook tokens holding the campaign's actual spend, most authoritative first. */
	static final List<String> SRC_TOTAL_SPEND = List.of("{{total_investment}}", "{{total spend}}");

	/** Workbook tokens holding the campaign's planned impressions, most authoritative first. */
	static final List<String> SRC_TOTAL_IMPS_PLAN = List.of("{{total imps plan}}");

	/** Workbook tokens holding the campaign's actual impressions, most authoritative first. */
	static final List<String> SRC_TOTAL_IMPS = List.of("{{total imps}}");

	private final ReportNumberParser numbers;
	private final Fmt fmt;

	/**
	 * Fills every dashboard token for the campaign's real tactics and the pacing dashboard's totals row,
	 * overwriting whatever the map held for them.
	 *
	 * <p>Bounded to {@code tacticCount} on purpose: the slots above it belong to dashboard rows that are
	 * deleted during the deck trim, and filling them would only put figures on rows that are about to go.
	 *
	 * @param flat        the placeholder map to fill, mutated in place; a {@code null} map is ignored
	 * @param tacticCount number of real tactics in the campaign
	 */
	public void fill(Map<String, String> flat, int tacticCount) {
		if (flat == null) {
			return;
		}
		for (int n = 1; n <= tacticCount; n++) {
			fillTactic(flat, n);
			fillPerformance(flat, n);
		}
		fillTotals(flat, tacticCount);
	}

	/**
	 * Fills one tactic's row of the pacing dashboard: the four figures copied from the summary-table tokens
	 * and the pacing computed between the two spend figures.
	 *
	 * @param flat the placeholder map to fill, mutated in place
	 * @param n    the 1-based tactic number
	 */
	void fillTactic(Map<String, String> flat, int n) {
		String plannedBudget = value(flat, tacticToken(n, SRC_SPEND_PLAN));
		String factBudget = value(flat, tacticToken(n, SRC_SPEND));
		flat.put(tacticToken(n, OUT_PLANNED_BUDGET), display(plannedBudget));
		flat.put(tacticToken(n, OUT_FACT_BUDGET), display(factBudget));
		flat.put(tacticToken(n, OUT_PLANNED_IMPS), display(value(flat, tacticToken(n, SRC_IMPS_PLAN))));
		flat.put(tacticToken(n, OUT_FACT_IMPS), display(value(flat, tacticToken(n, SRC_IMPS))));
		flat.put(tacticToken(n, OUT_PACING), pacing(factBudget, plannedBudget));
	}

	/**
	 * Fills one tactic's row of the performance-vs-plan dashboard: the goal for the KPI the tactic is
	 * judged on, and the distance the actual KPI stands from it.
	 *
	 * <p>The actual KPI itself is not written here — {@code {{tactic n KPI}}} already carries it, picked by
	 * the same KPI type this row's goal is picked by, so the two columns can never end up describing
	 * different metrics.
	 *
	 * @param flat the placeholder map to fill, mutated in place
	 * @param n    the 1-based tactic number
	 */
	void fillPerformance(Map<String, String> flat, int n) {
		String goal = kpiGoal(flat, n);
		flat.put(tacticToken(n, OUT_KPI_GOAL), display(goal));
		flat.put(tacticToken(n, OUT_VS_GOAL), pointsAgainst(value(flat, tacticToken(n, SRC_KPI)), goal));
	}

	/**
	 * Reads the planned rate for whichever KPI the tactic is judged on: the planned CTR for a click-led
	 * tactic, the planned completion rate for every other spelling of the KPI type.
	 *
	 * <p>The completion rate is the one metric the deck spells two ways — VCR for video, ACR for audio —
	 * so the type is matched on the click-led spelling and everything else falls to the completion rate,
	 * rather than listing the spellings and dashing the row when a new one appears. A tactic whose KPI type
	 * is missing has no goal column at all: there is nothing to say which of the two planned rates the row
	 * is about.
	 *
	 * @param flat the placeholder map being read
	 * @param n    the 1-based tactic number
	 * @return the planned rate as a display string, or {@code null} when the type or the rate is missing
	 */
	String kpiGoal(Map<String, String> flat, int n) {
		String type = value(flat, tacticToken(n, SRC_KPI_TYPE));
		if (type == null) {
			return null;
		}
		String suffix = type.equalsIgnoreCase(KPI_TYPE_CTR) ? SRC_CTR_PLAN : SRC_VCR_PLAN;
		return value(flat, tacticToken(n, suffix));
	}

	/**
	 * Computes the performance dashboard's "vs goal" column: how far the actual rate stands from its goal,
	 * in percentage points.
	 *
	 * <p>Both figures are rates the deck prints as percentages, so their distance is a percentage-point
	 * delta rather than a ratio — 0.35% against a 0.25% goal is {@code "+0.10pp"}, not "+40%". It is
	 * rendered signed and to two decimals with trailing zeros dropped, so a CTR delta keeps the precision
	 * the metric is read at while a completion-rate delta of twenty points prints as {@code "+20pp"}. A
	 * delta that rounds away to nothing prints as {@code "0pp"}, so the column never shows a signed zero.
	 *
	 * @param fact the actual rate as a display string (may be {@code null})
	 * @param goal the goal rate as a display string (may be {@code null})
	 * @return the signed percentage-point delta, or a dash when either figure is missing
	 */
	String pointsAgainst(String fact, String goal) {
		if (fact == null || goal == null) {
			return DASH;
		}
		double delta = numbers.parseReportNumber(fact) - numbers.parseReportNumber(goal);
		if (Math.abs(delta) < ON_GOAL_TOLERANCE_PP) {
			return "0pp";
		}
		String rounded = String.format(Locale.US, "%.2f", delta);
		if (rounded.endsWith("0")) {
			rounded = rounded.substring(0, rounded.length() - 1);
		}
		if (rounded.endsWith(".0")) {
			rounded = rounded.substring(0, rounded.length() - 2);
		}
		return (delta > 0 ? "+" : "") + rounded + "pp";
	}

	/**
	 * Fills the dashboard's totals row. Each total prefers the workbook's own totals-row token and falls
	 * back to the sum of the tactic rows above it, so the row is never a dash while the rows it totals
	 * carry figures.
	 *
	 * @param flat        the placeholder map to fill, mutated in place
	 * @param tacticCount number of real tactics whose rows are summed for the fallback
	 */
	void fillTotals(Map<String, String> flat, int tacticCount) {
		String plannedBudget = totalMoney(flat, SRC_TOTAL_SPEND_PLAN, SRC_SPEND_PLAN, tacticCount);
		String factBudget = totalMoney(flat, SRC_TOTAL_SPEND, SRC_SPEND, tacticCount);
		flat.put(TOTAL_PLANNED_BUDGET, display(plannedBudget));
		flat.put(TOTAL_FACT_BUDGET, display(factBudget));
		flat.put(TOTAL_PLANNED_IMPS, display(totalCount(flat, SRC_TOTAL_IMPS_PLAN, SRC_IMPS_PLAN, tacticCount)));
		flat.put(TOTAL_FACT_IMPS, display(totalCount(flat, SRC_TOTAL_IMPS, SRC_IMPS, tacticCount)));
		flat.put(TOTAL_PACING, pacing(factBudget, plannedBudget));
	}

	/**
	 * Resolves a campaign total that is money: the first non-blank source token, or the tactic rows summed
	 * and formatted the way every other monetary figure in the deck is.
	 *
	 * @param flat        the placeholder map being read
	 * @param sources     the totals-row tokens to prefer, most authoritative first
	 * @param rowSuffix   the per-tactic token suffix summed when no total is carried
	 * @param tacticCount number of real tactics to sum over
	 * @return the total as a display string, or {@code null} when nothing was carried and the sum is zero
	 */
	String totalMoney(Map<String, String> flat, List<String> sources, String rowSuffix, int tacticCount) {
		String carried = firstValue(flat, sources);
		if (carried != null) {
			return carried;
		}
		double sum = sumRows(flat, rowSuffix, tacticCount);
		return sum > 0 ? fmt.moneyExact(sum) : null;
	}

	/**
	 * Resolves a campaign total that is a count, in the same order of preference as {@link #totalMoney}.
	 *
	 * @param flat        the placeholder map being read
	 * @param sources     the totals-row tokens to prefer, most authoritative first
	 * @param rowSuffix   the per-tactic token suffix summed when no total is carried
	 * @param tacticCount number of real tactics to sum over
	 * @return the total as a display string, or {@code null} when nothing was carried and the sum is zero
	 */
	String totalCount(Map<String, String> flat, List<String> sources, String rowSuffix, int tacticCount) {
		String carried = firstValue(flat, sources);
		if (carried != null) {
			return carried;
		}
		double sum = sumRows(flat, rowSuffix, tacticCount);
		return sum > 0 ? fmt.intGroup(sum) : null;
	}

	/**
	 * Sums one column of the dashboard over the campaign's real tactics.
	 *
	 * @param flat        the placeholder map being read
	 * @param suffix      the per-tactic token suffix to sum
	 * @param tacticCount number of real tactics to sum over
	 * @return the sum, {@code 0} when no row carries a parseable figure
	 */
	double sumRows(Map<String, String> flat, String suffix, int tacticCount) {
		double sum = 0;
		for (int n = 1; n <= tacticCount; n++) {
			sum += numbers.parseReportNumber(value(flat, tacticToken(n, suffix)));
		}
		return sum;
	}

	/**
	 * Computes a pacing figure as the actual against the plan, rendered as a whole percentage the way the
	 * dashboard column prints it (e.g. {@code "103%"}).
	 *
	 * @param fact the actual figure as a display string (may be {@code null})
	 * @param plan the planned figure as a display string (may be {@code null})
	 * @return the pacing percentage, or a dash when the plan is missing or not positive
	 */
	String pacing(String fact, String plan) {
		double planned = numbers.parseReportNumber(plan);
		if (planned <= 0) {
			return DASH;
		}
		return String.format(Locale.US, "%d%%", Math.round(numbers.parseReportNumber(fact) / planned * 100));
	}

	/**
	 * Reads a token's value, treating a blank cell and a dash as "no figure" so a dashed source never
	 * propagates as if it were data.
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
	 * Reads the first token of a preference list that carries a figure.
	 *
	 * @param flat   the placeholder map being read
	 * @param tokens the tokens to try, most authoritative first
	 * @return the first non-blank value, or {@code null} when none of them carries one
	 */
	String firstValue(Map<String, String> flat, List<String> tokens) {
		for (String token : tokens) {
			String value = value(flat, token);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Renders a resolved figure for the slide, falling back to the dash the deck prints for an empty slot.
	 *
	 * @param value the resolved figure (may be {@code null})
	 * @return the value, or a dash when there is none
	 */
	String display(String value) {
		return value == null ? DASH : value;
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
