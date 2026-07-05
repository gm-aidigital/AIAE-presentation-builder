package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDate;

/**
 * Report volume for a single day, for the "This week" mini bar chart.
 *
 * @param date  the calendar day
 * @param label short weekday label, e.g. {@code Mon}
 * @param count reports created that day
 */
public record AdminDayVolume(LocalDate date, String label, int count) {
}
