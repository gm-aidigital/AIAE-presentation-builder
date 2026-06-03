package com.aidigital.reportconstructor.service.reports.dto;

import java.time.LocalDate;

/** Parsed flight window boundaries (moved out of engine {@code SheetUtils} for dto purity). */
public record FlightDates(LocalDate start, LocalDate end) {}
