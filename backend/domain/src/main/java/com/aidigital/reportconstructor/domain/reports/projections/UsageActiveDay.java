package com.aidigital.reportconstructor.domain.reports.projections;

import java.time.LocalDate;

/**
 * One (day, user) pair on which that user generated something.
 *
 * <p>Active users are counted from these pairs rather than from a {@code count(distinct …)} grouped
 * by month in SQL, because one user active on several days of a month must count once for the month
 * and once for each week they appeared in. Summing a per-month count would answer only one of those
 * questions; the pairs answer every bucket granularity — week, month, quarter — from a single read,
 * and the read stays small because the rollup has already collapsed each user's day to one row.
 *
 * @param day         the calendar day
 * @param ownerUserId internal id of the user who was active on it
 */
public record UsageActiveDay(LocalDate day, String ownerUserId) {
}
