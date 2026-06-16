package com.aidigital.reportconstructor.service.reports.dto;

import java.time.LocalDate;

/**
 * Parsed flight window boundaries (moved out of engine {@code SheetUtils} for dto purity).
 *
 * @param start first day of the campaign flight window (inclusive)
 * @param end   last day of the campaign flight window (inclusive)
 */
public record FlightDates(LocalDate start, LocalDate end) {
	// required
}
