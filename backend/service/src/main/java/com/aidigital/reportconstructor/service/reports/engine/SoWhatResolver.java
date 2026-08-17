package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.SoWhatPhrase;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Picks each tactic's "SO WHAT?" phrase — the one line that tells the client what the tactic achieved in
 * funnel terms. Chosen from the fixed {@link SoWhatPhrase} catalogue rather than written per campaign, for
 * the reasons that enum documents.
 *
 * <p>Priority, highest first: a value the user typed into the Adjustments or Media Plan grid always wins,
 * then the tactic's own funnel goal decides, and a tactic with no usable goal falls back on what its KPI
 * says about where in the funnel it sits. The chain never returns nothing, so the column is never blank
 * and the token can never ship raw.
 */
@Component
public class SoWhatResolver {

	private final CampaignResolvers campaignResolvers;
	private final TacticExtractionHelper tacticExtraction;

	/**
	 * Creates the resolver.
	 *
	 * @param campaignResolvers shared adj-then-sheet manual lookup, so a hand-typed phrase always wins
	 * @param tacticExtraction  tactic-name → KPI-type mapping, used by the no-goal fallback
	 */
	public SoWhatResolver(CampaignResolvers campaignResolvers, TacticExtractionHelper tacticExtraction) {
		this.campaignResolvers = campaignResolvers;
		this.tacticExtraction = tacticExtraction;
	}

	/**
	 * Resolves the "so what" phrase for one tactic.
	 *
	 * @param n          one-based tactic index
	 * @param tacticName the tactic's channel name, used to derive its KPI type when the goal is unusable
	 * @param sheetRows  Media Plan tab rows
	 * @param adjRows    manual Adjustments tab rows (take priority over the sheet)
	 * @param goal       the tactic's resolved funnel goal (the Media Plan "Goal" column), may be {@code null}
	 * @return the resolved phrase, tagged {@code "adj"}/{@code "sheet"} when hand-entered, {@code "auto"} otherwise
	 */
	public Resolved resolveSoWhat(
			int n, String tacticName, List<List<String>> sheetRows, List<List<String>> adjRows, Resolved goal) {
		String label = "So what " + n + ":";
		Resolved manual = campaignResolvers.resolve(sheetRows, adjRows, label);
		if (manual.found()) {
			return manual;
		}
		String goalText = goal == null || goal.value() == null ? "" : goal.value().trim().toLowerCase(Locale.ROOT);
		return new Resolved(label + " (auto: funnel goal)", phraseFor(goalText, tacticName).text(), "auto");
	}

	/**
	 * Selects the catalogue phrase for a goal, falling back to the tactic's KPI type when the goal names no
	 * funnel stage the catalogue knows.
	 *
	 * <p>The catalogue is scanned in declaration order, so a goal naming several stages ("Awareness &amp;
	 * Conversion") resolves to the upper-funnel phrase — the stage such a plan line is bought against.
	 *
	 * @param lowerCasedGoal the tactic's goal text, lower-cased and trimmed (may be empty)
	 * @param tacticName     the tactic's channel name (may be {@code null})
	 * @return the phrase to print
	 */
	SoWhatPhrase phraseFor(String lowerCasedGoal, String tacticName) {
		if (!lowerCasedGoal.isEmpty()) {
			for (SoWhatPhrase phrase : SoWhatPhrase.values()) {
				if (phrase.matches(lowerCasedGoal)) {
					return phrase;
				}
			}
		}
		return phraseForKpi(tacticName);
	}

	/**
	 * The no-goal fallback: a completion-rate channel (video, CTV, audio) is bought for attention, a
	 * click-rate channel for engagement, and anything unmapped is treated as the reach play most plan lines
	 * are — never a down-funnel claim the plan did not promise.
	 *
	 * @param tacticName the tactic's channel name (may be {@code null})
	 * @return the phrase implied by the channel's KPI type
	 */
	SoWhatPhrase phraseForKpi(String tacticName) {
		String kpiType = tacticExtraction.getTacticKpiType(tacticName);
		if ("vcr".equals(kpiType)) {
			return SoWhatPhrase.AWARENESS_ATTENTION;
		}
		if ("ctr".equals(kpiType)) {
			return SoWhatPhrase.ENGAGEMENT;
		}
		return SoWhatPhrase.AWARENESS_REACH;
	}
}
