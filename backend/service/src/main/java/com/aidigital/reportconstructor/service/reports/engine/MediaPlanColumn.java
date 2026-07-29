package com.aidigital.reportconstructor.service.reports.engine;

import lombok.Getter;

import java.util.Set;

/**
 * A media-plan grid column whose header wording varies across proposal/RFP templates.
 *
 * <p>In most real media plans a field like the campaign geography or funnel stage is not a
 * {@code label : value} pair but a <em>column</em>: the header names the field once and the actual
 * values run down the rows beneath it (often across several line items and campaign sections). The
 * exact header text differs per template ({@code "Geo"}, {@code "Targeted Locations"}, {@code "Goal"},
 * {@code "Funnel Stage"}, …), so each constant carries the full set of header synonyms that identify
 * its column.
 *
 * <p>Synonyms are stored already normalised — lowercase, with every run of non-alphanumeric
 * characters collapsed to a single space and trimmed — matching the normalisation applied to sheet
 * header cells before comparison.
 */
@Getter
public enum MediaPlanColumn {

	/** The geographic-targeting column ({@code {{geo_locations}}}). */
	GEO(Set.of(
			"geo", "geos", "geo targeting", "geography", "geographies",
			"geo location", "geo locations", "location", "locations",
			"target location", "target locations", "targeted location", "targeted locations",
			"market", "markets", "dma", "dmas", "region", "regions")),

	/** The marketing-funnel / campaign-goal column ({@code {{funnel_stages}}}). */
	FUNNEL(Set.of(
			"goal", "goals", "funnel", "funnel stage", "funnel stages",
			"objective", "objectives", "campaign objective", "campaign objectives",
			"marketing objective", "marketing objectives", "stage", "stages")),

	/**
	 * The per-line-item flight-start-date column. Paired with {@link #FLIGHT_END}, the earliest start
	 * across every line item gives the whole campaign's planned flight window for EOM pacing (as
	 * opposed to the raw-data delivery window, which only covers what's been delivered so far).
	 */
	FLIGHT_START(Set.of(
			"flight start", "flight start date", "start date", "flight begin", "flight begin date")),

	/** The per-line-item flight-end-date column, paired with {@link #FLIGHT_START}. */
	FLIGHT_END(Set.of(
			"flight end", "flight end date", "end date", "flight finish", "flight finish date"));

	private final Set<String> synonyms;

	/**
	 * @param synonyms normalised header texts (lowercase, punctuation collapsed to single spaces) that identify this
	 *                 column
	 */
	MediaPlanColumn(Set<String> synonyms) {
		this.synonyms = synonyms;
	}
}
