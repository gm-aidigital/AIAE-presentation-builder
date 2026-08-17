package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The catalogue of "SO WHAT?" phrases — the one-line answer, per tactic, to what that tactic achieved
 * for the client in funnel terms. Printed in the EOC sheet's {@code {{so what N}}} column and carried
 * from there onto the deck's Platforms table.
 *
 * <p>Deliberately a small fixed set that repeats across reports rather than copy generated per campaign:
 * the phrase has to fit one narrow table cell, stay positive, and never contradict the RFP's strategy.
 * A fixed catalogue keyed on the tactic's own funnel goal does all three by construction — it cannot run
 * long, cannot come back blank, and cannot invent an achievement the plan never promised. The user edits
 * the sheet before the deck is built, so a phrase that needs sharpening for a specific campaign is one
 * cell edit away.
 *
 * <p>Every phrase is capped at {@link #MAX_LENGTH} characters, which is what the column fits.
 *
 * <p>Keywords are matched against the Media Plan's "Goal" column for that tactic — the same value the
 * {@code {{tactic N goal}}} funnel badge shows — so the phrase follows the funnel stage the plan itself
 * assigned. Matching is substring-based on lower-cased text, so "Awareness / Reach" hits {@code awareness}.
 */
public enum SoWhatPhrase {

	/** Upper funnel, non-video: the tactic bought presence in front of the target audience. */
	AWARENESS_REACH("Built reach across the target audience",
			List.of("awareness", "brand", "reach", "upper")),

	/** Upper funnel, video/CTV/audio: the tactic bought completed, full-attention exposure. */
	AWARENESS_ATTENTION("Held full-screen attention to completion",
			List.of("view", "video", "attention")),

	/** Mid funnel: the tactic kept the brand present while the audience was choosing. */
	CONSIDERATION("Kept the brand top of mind while choosing",
			List.of("consideration", "mid", "nurture", "research")),

	/** Mid funnel, click-led: the tactic converted interest into a measurable action on site. */
	ENGAGEMENT("Turned interest into active engagement",
			List.of("engagement", "traffic", "click", "visit", "interest")),

	/** Lower funnel: the tactic delivered the down-funnel outcome efficiently. */
	CONVERSION("Drove strong conversions for your business",
			List.of("conversion", "convert", "action", "performance", "sales", "lead", "purchase", "book")),

	/** Lower funnel, warm audiences: the tactic recovered audiences that had already engaged. */
	RETENTION("Brought past visitors back to convert",
			List.of("retarget", "retention", "remarket", "return", "loyal", "crm"));

	/** Longest phrase the "SO WHAT?" column fits before it wraps out of its row. */
	public static final int MAX_LENGTH = 45;

	private final String text;
	private final List<String> goalKeywords;

	SoWhatPhrase(String text, List<String> goalKeywords) {
		this.text = text;
		this.goalKeywords = goalKeywords;
	}

	/**
	 * Returns the phrase as it is printed in the sheet and the deck.
	 *
	 * @return the "so what" phrase, never longer than {@link #MAX_LENGTH} characters
	 */
	public String text() {
		return text;
	}

	/**
	 * Returns the lower-cased funnel keywords this phrase answers to.
	 *
	 * @return the goal-column keywords that select this phrase
	 */
	public List<String> goalKeywords() {
		return goalKeywords;
	}

	/**
	 * Reports whether the given tactic goal selects this phrase.
	 *
	 * @param lowerCasedGoal the tactic's goal text, already lower-cased and trimmed
	 * @return {@code true} when the goal contains one of this phrase's keywords
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
