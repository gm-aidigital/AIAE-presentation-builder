package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A single strategic insight entry, pairing a headline point with its supporting explanation.
 *
 * @param point    short headline or title of the strategic insight
 * @param overview supporting detail or explanation backing the insight point
 */
public record StrategicInsight(String point, String overview) {
	// required
}
