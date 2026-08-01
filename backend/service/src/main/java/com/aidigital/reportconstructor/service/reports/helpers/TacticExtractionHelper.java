package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.List;
import java.util.Map;

/**
 * Media-column tactic extraction, tactic counting, and tactic metadata lookups.
 */
public interface TacticExtractionHelper {

	/**
	 * Extracts tactic names from the Media column in top-to-bottom order, keeping only
	 * recognised tactics and skipping interleaved section-label and added-value rows.
	 *
	 * @param rows media-plan grid rows (may be {@code null})
	 * @return recognised tactic names under the Media header, in sheet order
	 */
	List<String> extractTacticsFromMedia(List<List<String>> rows);

	/**
	 * Counts whitelist-matching tactics under the Media header (0..7).
	 *
	 * @param rows media-plan grid rows (may be {@code null})
	 * @return number of known tactics found below Media
	 */
	int countTacticsInMediaPlan(List<List<String>> rows);

	/**
	 * Reports whether a Media-column cell names a recognised tactic, used to keep
	 * tactic-row scanning aligned across resolvers.
	 *
	 * @param mediaCell the Media-column cell value (may be {@code null})
	 * @return {@code true} when the value is a known tactic
	 */
	boolean isKnownTactic(String mediaCell);

	/**
	 * Maps a raw media-plan tactic name to its Slides display label.
	 *
	 * @param rawName tactic name from the Media column (may be {@code null})
	 * @return short display label, or the original name when unmapped
	 */
	String normalizeTacticDisplayName(String rawName);

	/**
	 * Returns the whitelist map used when resolving tactic lists.
	 *
	 * @return lowercase media name to canonical display name
	 */
	Map<String, String> knownTacticsWhitelist();

	/**
	 * Returns the BigQuery channel filter for a tactic name.
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return channel filter value, or {@code null} when unmapped
	 */
	String getTacticChannelFilter(String tacticName);

	/**
	 * Returns the KPI type ({@code ctr} or {@code vcr}) for a tactic name.
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return {@code ctr}, {@code vcr}, or {@code null} when unknown
	 */
	String getTacticKpiType(String tacticName);

	/**
	 * Returns the completion-rate label for a completion-led ({@code vcr}) tactic: {@code "ACR"} (audio
	 * completion rate) for audio/podcast tactics, otherwise {@code "VCR"}. Used so an Audio tactic's KPI
	 * type and benchmark read "ACR" rather than the video-specific "VCR".
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return {@code "ACR"} for audio/podcast tactics, otherwise {@code "VCR"}
	 */
	String getCompletionRateLabel(String tacticName);

	/**
	 * Returns the chart/pacing KPI-series token for a tactic: {@code "ctr"}, {@code "vcr"}, or {@code "acr"}
	 * (audio completion rate) — the latter for audio/podcast tactics that would otherwise be {@code "vcr"}.
	 * Series-selection code treats {@code "acr"} exactly like {@code "vcr"} (completions), but the sheet's
	 * KPI header then reads "ACR". Unlike {@link #getTacticKpiType(String)}, which resolver branching relies
	 * on staying {@code ctr}/{@code vcr}, this token is for the chart/pacing writers.
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return {@code "ctr"}, {@code "vcr"}, {@code "acr"}, or {@code null} when unknown
	 */
	String getTacticKpiSeries(String tacticName);

	/**
	 * Returns the maximum addressable-audience coefficient (fraction of {@code {{market volume}}}, always
	 * {@code < 1}) for a tactic, used to derive {@code {{tactic n volume}}}.
	 *
	 * @param tacticName the {@code {{tactic n}}} display name (may be {@code null})
	 * @return the channel coefficient in {@code (0, 1)}, or the default coefficient when unmapped or null
	 */
	double volumeCoefficient(String tacticName);

	/**
	 * Derives a deterministic reduced frequency from the planned max frequency.
	 *
	 * @param n       zero-based tactic index
	 * @param maxFreq planned maximum frequency
	 * @return reduced frequency rounded to two decimals
	 */
	double freqFromMax(int n, double maxFreq);

	/**
	 * Derives a deterministic reduced frequency from the media plan's per-week frequency: the weekly
	 * figure is scaled to the whole reporting period and then discounted, because a plan's weekly
	 * frequency is a ceiling the actual delivery never quite reaches.
	 *
	 * @param n          one-based tactic index, which fixes the discount so the value is reproducible
	 * @param weeklyFreq planned frequency per week from the media plan
	 * @param weeks      weeks covered by the reporting period (report days ÷ 7)
	 * @return the period frequency less a 2-20% discount, rounded to two decimals
	 */
	double freqFromWeekly(int n, double weeklyFreq, double weeks);

	/**
	 * Sanitizes text for safe insertion into Google Slides elements.
	 *
	 * @param value raw text (may be {@code null})
	 * @return cleaned text capped at 50000 characters
	 */
	String sanitizeForSlides(String value);
}
