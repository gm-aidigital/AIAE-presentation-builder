package com.aidigital.reportconstructor.service.reports.dto;

import java.time.LocalDate;

/**
 * User-selected date window applied to the raw-data rows when building a report.
 *
 * <p>When {@code mode} is {@link DateFilterMode#ALL} (or this whole filter is
 * {@code null}) every date in the raw data is included. When {@code mode} is
 * {@link DateFilterMode#RANGE} only rows within {@code [start, end]} inclusive
 * contribute. The window is independent of the media-plan flight dates.
 *
 * @param mode  inclusion mode; {@code null} is treated as {@link DateFilterMode#ALL}
 * @param start first day to include (inclusive); used only when {@code mode} is RANGE
 * @param end   last day to include (inclusive); used only when {@code mode} is RANGE
 */
public record DateFilter(DateFilterMode mode, LocalDate start, LocalDate end) {

	/**
	 * Resolves this filter to the concrete day window used to gate raw-data rows.
	 *
	 * @return the inclusive {@link FlightDates} window when a valid RANGE is selected,
	 *         or {@code null} to include every date (ALL / missing bounds)
	 */
	public FlightDates toWindow() {
		if (mode != DateFilterMode.RANGE || start == null || end == null) {
			return null;
		}
		return new FlightDates(start, end);
	}
}
