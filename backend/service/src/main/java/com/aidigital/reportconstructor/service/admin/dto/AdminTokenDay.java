package com.aidigital.reportconstructor.service.admin.dto;

import java.time.LocalDate;

/**
 * Token spend for a single day, for the token tab's trend bars.
 *
 * @param date        the calendar day
 * @param label       short weekday label, e.g. {@code Mon}
 * @param totalTokens every token the day's reports consumed
 * @param costUsd     estimated cost of that day at configured list prices
 */
public record AdminTokenDay(LocalDate date, String label, long totalTokens, double costUsd) {
}
