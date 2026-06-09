package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.List;
import java.util.Map;

/**
 * Media-column tactic extraction, tactic counting, and tactic metadata lookups.
 */
public interface TacticExtractionHelper {

    /**
     * Extracts tactic names from the Media column in top-to-bottom order.
     *
     * @param rows media-plan grid rows (may be {@code null})
     * @return tactic names under the Media header, in sheet order
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
     * Derives a deterministic reduced frequency from the planned max frequency.
     *
     * @param n       zero-based tactic index
     * @param maxFreq planned maximum frequency
     * @return reduced frequency rounded to two decimals
     */
    double freqFromMax(int n, double maxFreq);

    /**
     * Sanitizes text for safe insertion into Google Slides elements.
     *
     * @param value raw text (may be {@code null})
     * @return cleaned text capped at 50000 characters
     */
    String sanitizeForSlides(String value);
}
