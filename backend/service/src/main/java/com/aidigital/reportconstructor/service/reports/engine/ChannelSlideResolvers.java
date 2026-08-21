package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Resolvers for the EOM channel slide's metric table — the six-row block (Impressions, CTR, Clicks,
 * Reach, CPM, Spend) against the reporting month's goal, the month's actual, the variance, the
 * end-of-campaign goal and the end-of-campaign projection.
 *
 * <p>Kept apart from {@link TacticResolvers} because this table follows its own rules rather than the
 * proration the other EOM pacing tokens use: the month's goal is the plan the user entered while
 * matching, the end-of-campaign columns come from the media plan's own flight figures, and every
 * projection is floored at plan — an under-pacing tactic projects to its goal rather than below it,
 * which is a deliberate reporting decision, not a rounding artefact.
 *
 * <p>Each resolver keeps the house source priority: manual Adjustments → Media Plan → computed.
 */
@Component
@RequiredArgsConstructor
public class ChannelSlideResolvers {

	/** Printed when an input is missing, zero or otherwise leaves the cell uncomputable. */
	static final String DASH = "—";

	/** Source tag for a value this class computed itself. */
	static final String SOURCE_AUTO = "adj";

	/** Source tag for a value no input could produce. */
	static final String SOURCE_NOT_FOUND = "not_found";

	/** Days in a week, used to turn the reporting window into the week count reach is planned in. */
	static final double DAYS_PER_WEEK = 7.0;

	/** Impressions-to-CPM scale factor. */
	static final double CPM_UNITS = 1000.0;

	/** Percentage scale, and the pacing level at or above which a projection may exceed its plan. */
	static final double FULL_PACE_PCT = 100.0;

	private final SheetRowHelper sheetUtils;
	private final Fmt fmt;
	private final TacticResolvers tacticResolvers;
	private final ReportNumberParser numbers;

	/**
	 * Resolves the reporting month's impressions pacing: delivered against the month's goal.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered and planned impressions
	 * @return the pacing as a whole percentage ({@code "101%"}), or a dash when the goal is missing
	 */
	public Resolved resolveImpsPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                  CampaignData data) {
		String label = "Tactic " + n + " imps pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, pacingPct(t == null ? null : t.imps(), t == null ? null : t.planImps()));
	}

	/**
	 * Resolves the full-flight impressions goal from the media plan's own Impressions column, which an
	 * EOM report otherwise loses: its plan fields carry the reporting month's targets.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's flight impressions
	 * @return the flight goal as a grouped integer, or a dash when the plan carries none
	 */
	public Resolved resolveEocPlannedImps(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                      CampaignData data) {
		String label = "Tactic " + n + " eoc planned imps:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, count(t == null ? null : t.planFlightImps()));
	}

	/**
	 * Resolves the projected end-of-campaign impressions: the flight goal carried forward at this month's
	 * pace, floored at the goal itself when the tactic is pacing below it.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the flight goal and this month's pace
	 * @return the projection as a grouped integer, or a dash when the flight goal is missing
	 */
	public Resolved resolveProjImps(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                CampaignData data) {
		String label = "Tactic " + n + " proj imps:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		if (t == null) {
			return resolved(label, null);
		}
		return resolved(label, projectAtPace(t.planFlightImps(), ratio(t.imps(), t.planImps())));
	}

	/**
	 * Resolves the CTR variance against the month's goal, in percentage points.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the actual and planned CTR
	 * @return the signed variance ({@code "+0.05pp"}), or a dash when either rate is missing
	 */
	public Resolved resolveCtrPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " ctr pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		if (t == null || t.ctr() == null || t.planCtr() == null) {
			return resolved(label, null);
		}
		return resolved(label, points(t.ctr() - t.planCtr()));
	}

	/**
	 * Resolves the projected end-of-campaign CTR: half the gap between goal and actual is carried
	 * forward when the tactic beats its goal, and the goal itself stands when it does not.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the actual and planned CTR
	 * @return the projected rate, or a dash when the goal is missing
	 */
	public Resolved resolveCtrProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                               CampaignData data) {
		String label = "Tactic " + n + " ctr proj:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		if (t == null || t.planCtr() == null) {
			return resolved(label, null);
		}
		Double actual = t.ctr();
		double projected = actual != null && actual > t.planCtr() ? (actual + t.planCtr()) / 2 : t.planCtr();
		return resolved(label, fmt.dec2(projected) + "%");
	}

	/**
	 * Resolves the reporting month's clicks pacing: delivered against the month's goal.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered and planned clicks
	 * @return the pacing as a whole percentage, or a dash when the goal is missing
	 */
	public Resolved resolveClicksPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                    CampaignData data) {
		String label = "Tactic " + n + " clicks pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, pacingPct(t == null ? null : t.clicks(), monthPlanClicks(t)));
	}

	/**
	 * Resolves the full-flight clicks goal from the media plan's own Clicks column.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's flight clicks
	 * @return the flight goal as a grouped integer, or a dash when the plan carries no Clicks column
	 */
	public Resolved resolveClicksMp(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                CampaignData data) {
		String label = "Tactic " + n + " clicks mp:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, count(t == null ? null : t.planFlightClicks()));
	}

	/**
	 * Resolves the projected end-of-campaign clicks: the flight goal carried forward at this month's
	 * clicks pace, floored at the goal when the tactic paces below it.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the flight goal and this month's clicks pace
	 * @return the projection as a grouped integer, or a dash when the flight goal is missing
	 */
	public Resolved resolveClicksProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                  CampaignData data) {
		String label = "Tactic " + n + " clicks proj:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		if (t == null) {
			return resolved(label, null);
		}
		return resolved(label, projectAtPace(t.planFlightClicks(), ratio(t.clicks(), monthPlanClicks(t))));
	}

	/**
	 * Resolves the reporting month's reach goal: the reach the delivered impressions should have bought
	 * at the media plan's planned weekly frequency.
	 *
	 * <p>Unlike the other month goals this one moves with delivery — the row measures frequency, not
	 * volume: at the planned frequency, this month's impressions imply this much reach.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered impressions, weekly frequency and window
	 * @return the implied reach as a grouped integer, or a dash when frequency or window is missing
	 */
	public Resolved resolveReachPlan(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " reach plan:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Double planned = monthPlanReach(data, n);
		return resolved(label, planned == null ? null : fmt.intGroup(planned));
	}

	/**
	 * Resolves the reach pacing: the delivered reach against this month's implied reach goal.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered reach and the month's goal
	 * @return the pacing as a whole percentage, or a dash when either side is missing
	 */
	public Resolved resolveReachPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                   CampaignData data) {
		String label = "Tactic " + n + " reach pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		return resolved(label, pacingPct(actualReach(n, sheetRows, adjRows, data), monthPlanReach(data, n)));
	}

	/**
	 * Resolves the full-flight reach goal from the media plan's own Reach column.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's planned reach
	 * @return the flight goal as a grouped integer, or a dash when the plan carries none
	 */
	public Resolved resolveReachPlanEoc(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                    CampaignData data) {
		String label = "Tactic " + n + " reach plan eoc:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, count(t == null ? null : t.planReach()));
	}

	/**
	 * Resolves the projected end-of-campaign reach, which the campaign is planned to land on: the media
	 * plan's own Reach figure, the same number the goal column carries.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's planned reach
	 * @return the projection as a grouped integer, or a dash when the plan carries none
	 */
	public Resolved resolveReachProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " reach proj:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, count(t == null ? null : t.planReach()));
	}

	/**
	 * Resolves the planned CPM: the month's planned spend over its planned impressions. For a
	 * CPM-bought tactic this is the unit price the user entered while matching — the planned
	 * impressions were derived from it — and for a CPC/CPV tactic it is the effective CPM its budget
	 * implies, which the media plan's unit price does not state.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned spend and impressions
	 * @return the planned CPM in dollars, or a dash when either side is missing
	 */
	public Resolved resolvePlannedCpm(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                  CampaignData data) {
		String label = "Tactic " + n + " planned cpm:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Double cpm = plannedCpm(data, n);
		return resolved(label, cpm == null ? null : money(cpm));
	}

	/**
	 * Resolves the delivered CPM: this month's spend over its delivered impressions.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered spend and impressions
	 * @return the delivered CPM in dollars, or a dash when nothing was delivered
	 */
	public Resolved resolveFactCpm(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                               CampaignData data) {
		String label = "Tactic " + n + " fact cpm:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Double cpm = factCpm(data, n);
		return resolved(label, cpm == null ? null : money(cpm));
	}

	/**
	 * Resolves the CPM variance as {@code planned − delivered}, so a positive figure means the tactic
	 * bought impressions more cheaply than planned. This is the opposite direction to every other row
	 * of the table, and deliberately so: on CPM, lower is better.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned and delivered CPM
	 * @return the signed variance ({@code "+ $0.30"}), or a dash when either side is missing
	 */
	public Resolved resolveCpmPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " cpm pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Double planned = plannedCpm(data, n);
		Double actual = factCpm(data, n);
		if (planned == null || actual == null) {
			return resolved(label, null);
		}
		return resolved(label, signedMoney(planned - actual));
	}

	/**
	 * Resolves the projected end-of-campaign CPM: midway between the planned and the delivered rate.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the planned and delivered CPM
	 * @return the projected CPM in dollars, or a dash when either side is missing
	 */
	public Resolved resolveCpmProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                               CampaignData data) {
		String label = "Tactic " + n + " cpm proj:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Double planned = plannedCpm(data, n);
		Double actual = factCpm(data, n);
		if (planned == null || actual == null) {
			return resolved(label, null);
		}
		return resolved(label, money((planned + actual) / 2));
	}

	/**
	 * Resolves the reporting month's budget pacing: spend delivered against the month's budget.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the delivered and planned spend
	 * @return the pacing as a whole percentage, or a dash when the budget is missing
	 */
	public Resolved resolveBudgetPacing(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                    CampaignData data) {
		String label = "Tactic " + n + " budget pacing:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		return resolved(label, pacingPct(t == null ? null : t.spend(), t == null ? null : t.planSpend()));
	}

	/**
	 * Resolves the full-flight spend goal from the media plan's own Total Cost column — the flight
	 * figure an EOM report otherwise loses, since its spend plan carries the reporting month's budget.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's flight cost
	 * @return the flight goal in dollars, or a dash when the plan carries none
	 */
	public Resolved resolveSpendPlanEoc(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                    CampaignData data) {
		String label = "Tactic " + n + " spend plan eoc:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		Double flightSpend = t == null ? null : t.planFlightSpend();
		return resolved(label, flightSpend == null ? null : money(flightSpend));
	}

	/**
	 * Resolves the projected end-of-campaign spend, which is the booked budget itself: a flight spends
	 * what it was booked for, so this column restates the plan rather than extrapolating delivery.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows searched for the labelled value
	 * @param adjRows   manual Adjustments grid rows that take precedence over the sheet
	 * @param data      campaign data providing the media plan's flight cost
	 * @return the projection in dollars, or a dash when the plan carries none
	 */
	public Resolved resolveSpendProj(int n, List<List<String>> sheetRows, List<List<String>> adjRows,
	                                 CampaignData data) {
		String label = "Tactic " + n + " spend proj:";
		Resolved manual = override(label, sheetRows, adjRows);
		if (manual != null) {
			return manual;
		}
		Tactic t = tactic(data, n);
		Double flightSpend = t == null ? null : t.planFlightSpend();
		return resolved(label, flightSpend == null ? null : money(flightSpend));
	}

	/**
	 * Returns the manual override for a label when the Adjustments or Media Plan grid carries one.
	 *
	 * @param label     the resolver's lookup label
	 * @param sheetRows Media Plan grid rows
	 * @param adjRows   manual Adjustments grid rows, which win over the sheet
	 * @return the override, or {@code null} when neither grid carries the label
	 */
	Resolved override(String label, List<List<String>> sheetRows, List<List<String>> adjRows) {
		String fromAdj = sheetUtils.findLabelValue(adjRows, label);
		if (fromAdj != null) {
			return new Resolved(label, fromAdj, "adj");
		}
		String fromSheet = sheetUtils.findLabelValue(sheetRows, label);
		return fromSheet == null ? null : new Resolved(label, fromSheet, "sheet");
	}

	/**
	 * Wraps a computed cell value, turning a missing one into the table's dash rather than an empty cell.
	 *
	 * @param label the resolver's lookup label
	 * @param value the computed value, or {@code null} when it could not be computed
	 * @return the resolved cell
	 */
	Resolved resolved(String label, String value) {
		return value == null
				? new Resolved(label, DASH, SOURCE_NOT_FOUND)
				: new Resolved(label + " (auto)", value, SOURCE_AUTO);
	}

	/**
	 * Looks up one tactic's aggregated data.
	 *
	 * @param data campaign data (may be {@code null})
	 * @param n    one-based tactic index
	 * @return the tactic, or {@code null} when absent
	 */
	Tactic tactic(CampaignData data, int n) {
		return data == null || data.tactics() == null ? null : data.tactics().get(n);
	}

	/**
	 * Returns the reporting month's planned clicks, falling back to the planned impressions at the
	 * planned CTR for a tactic that was not bought on clicks and therefore has no click plan of its own.
	 *
	 * @param t the tactic (may be {@code null})
	 * @return the month's click goal, or {@code null} when neither source is available
	 */
	Double monthPlanClicks(Tactic t) {
		if (t == null) {
			return null;
		}
		if (t.planClicks() != null && t.planClicks() > 0) {
			return t.planClicks();
		}
		if (t.planImps() == null || t.planCtr() == null) {
			return null;
		}
		return t.planImps() * t.planCtr() / FULL_PACE_PCT;
	}

	/**
	 * Computes the reporting month's implied reach goal: delivered impressions spread at the planned
	 * weekly frequency over the weeks the reporting window spans.
	 *
	 * @param data campaign data providing the tactic, its weekly frequency and the reporting window
	 * @param n    one-based tactic index
	 * @return the implied reach, or {@code null} when frequency or window is missing
	 */
	Double monthPlanReach(CampaignData data, int n) {
		Tactic t = tactic(data, n);
		Double weeklyFreq = t == null ? null : t.planWeeklyFreq();
		Double weeks = reportingWeeks(data);
		if (t == null || weeklyFreq == null || weeklyFreq <= 0 || weeks == null || t.imps() <= 0) {
			return null;
		}
		return t.imps() / (weeklyFreq * weeks);
	}

	/**
	 * Returns the delivered reach for a tactic by parsing the figure the reach resolver prints, so the
	 * pacing cell can never disagree with the reach cell beside it.
	 *
	 * @param n         one-based tactic index
	 * @param sheetRows Media Plan grid rows
	 * @param adjRows   manual Adjustments grid rows
	 * @param data      campaign data
	 * @return the delivered reach, or {@code null} when it could not be resolved
	 */
	Double actualReach(int n, List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {
		Resolved reach = tacticResolvers.resolveTacticReach(n, sheetRows, adjRows, data);
		if (!reach.found() || reach.value() == null) {
			return null;
		}
		double parsed = numbers.parseReportNumber(reach.value());
		return parsed > 0 ? parsed : null;
	}

	/**
	 * Returns the number of weeks the reporting window spans, the period the month's reach goal is
	 * planned over.
	 *
	 * @param data campaign data carrying the reporting window
	 * @return the week count, or {@code null} when the window is unknown or empty
	 */
	Double reportingWeeks(CampaignData data) {
		FlightDates window = data == null ? null : data.flightTs();
		if (window == null || window.start() == null || window.end() == null) {
			return null;
		}
		long days = ChronoUnit.DAYS.between(window.start(), window.end()) + 1;
		return days > 0 ? days / DAYS_PER_WEEK : null;
	}

	/**
	 * Computes the planned CPM from the reporting month's planned spend and impressions.
	 *
	 * @param data campaign data
	 * @param n    one-based tactic index
	 * @return the planned CPM, or {@code null} when either side is missing
	 */
	Double plannedCpm(CampaignData data, int n) {
		Tactic t = tactic(data, n);
		if (t == null || t.planSpend() == null || t.planImps() == null || t.planImps() <= 0) {
			return null;
		}
		return t.planSpend() / t.planImps() * CPM_UNITS;
	}

	/**
	 * Computes the delivered CPM from this month's spend and impressions.
	 *
	 * @param data campaign data
	 * @param n    one-based tactic index
	 * @return the delivered CPM, or {@code null} when nothing was delivered
	 */
	Double factCpm(CampaignData data, int n) {
		Tactic t = tactic(data, n);
		if (t == null || t.imps() <= 0) {
			return null;
		}
		return t.spend() / t.imps() * CPM_UNITS;
	}

	/**
	 * Computes an actual-against-goal ratio as a percentage.
	 *
	 * @param actual the delivered figure (may be {@code null})
	 * @param goal   the goal it is measured against (may be {@code null})
	 * @return the percentage, or {@code null} when the goal is missing or zero
	 */
	Double ratio(Double actual, Double goal) {
		if (actual == null || goal == null || goal <= 0) {
			return null;
		}
		return actual / goal * FULL_PACE_PCT;
	}

	/**
	 * Formats an actual-against-goal ratio as the table's whole-percentage pacing cell.
	 *
	 * @param actual the delivered figure (may be {@code null})
	 * @param goal   the goal it is measured against (may be {@code null})
	 * @return the pacing string ({@code "101%"}), or {@code null} when it is not computable
	 */
	String pacingPct(Double actual, Double goal) {
		Double pct = ratio(actual, goal);
		return pct == null ? null : Math.round(pct) + "%";
	}

	/**
	 * Carries a flight goal forward at the month's pace, flooring the result at the goal itself: a
	 * tactic pacing below plan projects to its plan rather than below it.
	 *
	 * @param flightGoal the full-flight goal (may be {@code null})
	 * @param pacePct    the month's pace as a percentage (may be {@code null})
	 * @return the projection as a grouped integer, or {@code null} when the goal is missing
	 */
	String projectAtPace(Double flightGoal, Double pacePct) {
		if (flightGoal == null || flightGoal <= 0) {
			return null;
		}
		if (pacePct == null || pacePct < FULL_PACE_PCT) {
			return fmt.intGroup(flightGoal);
		}
		return fmt.intGroup(Math.round(flightGoal * pacePct / FULL_PACE_PCT));
	}

	/**
	 * Formats a planned or delivered count as a grouped integer.
	 *
	 * @param value the count (may be {@code null})
	 * @return the formatted count, or {@code null} when absent or not positive
	 */
	String count(Double value) {
		return value == null || value <= 0 ? null : fmt.intGroup(value);
	}

	/**
	 * Formats a rate variance in percentage points, always signed.
	 *
	 * @param delta the difference between actual and goal, in percentage points
	 * @return the signed variance ({@code "+0.05pp"})
	 */
	String points(double delta) {
		return String.format(Locale.US, "%+.2fpp", delta);
	}

	/**
	 * Formats a money figure with cents, as every cell of the table's Spend and CPM rows prints it.
	 *
	 * @param value the amount
	 * @return the dollar-prefixed amount with two decimals
	 */
	String money(double value) {
		return "$" + fmt.dec2(value);
	}

	/**
	 * Formats a money variance with its sign spelled out before the amount, the spelling the channel
	 * slide uses ({@code "+ $0.30"}).
	 *
	 * @param delta the signed difference
	 * @return the signed amount
	 */
	String signedMoney(double delta) {
		return (delta < 0 ? "- " : "+ ") + money(Math.abs(delta));
	}
}
