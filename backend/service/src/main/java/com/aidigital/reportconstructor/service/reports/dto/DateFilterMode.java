package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Selects how raw-data (BigQuery export) rows are date-filtered before aggregation
 * and chart building.
 *
 * <p>This is intentionally decoupled from the media-plan flight window: the flight
 * window only feeds the {@code {{flight_dates}}} placeholder, while this mode drives
 * which delivery rows actually contribute to the report figures and charts.
 */
public enum DateFilterMode {

	/** Include every date present in the raw data (no date gating). */
	ALL,

	/** Include only rows whose date falls within the selected window, inclusive. */
	RANGE
}
