package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * A marketing-funnel stage the EOM north-star slide lists its channels under.
 *
 * <p>The slide carries one text slot per stage ({@code {{awareness channels}}},
 * {@code {{consideration channels}}}, {@code {{conversions channels}}}), each filled with the tactics the
 * media plan marked as belonging to that stage — printed exactly as {@code {{tactic N}}} prints them, so a
 * channel reads the same on this slide as it does on every table that follows.
 *
 * <p>The stage a tactic belongs to is read off the media plan's "Goal" / "Funnel" column — the same column
 * {@code {{tactic N goal}}} reads — matched substring-wise on lower-cased text exactly as
 * {@link SoWhatPhrase} matches it. A plan line marked for two stages ("Awareness &amp; Consideration")
 * legitimately matches both and is listed under both — that is what the plan says the line was bought for.
 */
public enum FunnelChannelGroup {

	/** Upper funnel: the line was bought for presence in front of the audience. */
	AWARENESS("{{awareness channels}}", "Awareness channels:",
			List.of("awareness", "brand", "reach", "upper", "prospect", "tofu", "visibility")),

	/** Mid funnel: the line was bought to keep the brand present while the audience was choosing. */
	CONSIDERATION("{{consideration channels}}", "Consideration channels:",
			List.of("consideration", "engagement", "traffic", "click", "visit", "interest", "intent",
					"mid", "nurture", "research", "view", "mofu")),

	/** Lower funnel: the line was bought for a measurable down-funnel outcome. */
	CONVERSIONS("{{conversions channels}}", "Conversions channels:",
			List.of("conversion", "convert", "action", "performance", "sales", "lead", "purchase",
					"book", "retarget", "remarket", "retention", "acquisition", "lower", "bofu"));

	private final String token;
	private final String label;
	private final List<String> goalKeywords;

	FunnelChannelGroup(String token, String label, List<String> goalKeywords) {
		this.token = token;
		this.label = label;
		this.goalKeywords = goalKeywords;
	}

	/**
	 * Returns the deck token this stage's channel list is written to.
	 *
	 * @return the full placeholder token, braces included
	 */
	public String token() {
		return token;
	}

	/**
	 * Returns the label a hand-entered override is looked up under, in the workbook's Info block and in the
	 * Adjustments tab.
	 *
	 * @return the label cell text, colon included
	 */
	public String label() {
		return label;
	}

	/**
	 * Returns the lower-cased goal keywords that put a tactic in this stage.
	 *
	 * @return the goal-column keywords this stage answers to
	 */
	public List<String> goalKeywords() {
		return goalKeywords;
	}

	/**
	 * Reports whether a tactic's goal text places it in this funnel stage.
	 *
	 * @param lowerCasedGoal the tactic's goal text, already lower-cased and trimmed
	 * @return {@code true} when the goal contains one of this stage's keywords
	 */
	public boolean matches(String lowerCasedGoal) {
		for (String keyword : goalKeywords) {
			if (lowerCasedGoal.contains(keyword)) {
				return true;
			}
		}
		return false;
	}
}
