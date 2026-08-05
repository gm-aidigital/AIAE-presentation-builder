package com.aidigital.reportconstructor.service.admin.dto;

import com.aidigital.reportconstructor.service.admin.enums.AdminPeriodUnit;

import java.time.LocalDate;

/**
 * The window of days a dashboard payload covers, and the trend granularity that suits it.
 *
 * <p>Both ends are inclusive, because that is how a person reading "1–31 July" understands it; the
 * queries that need an exclusive bound add the day themselves rather than making the caller reason
 * about it.
 *
 * <p>{@code unit} travels with the range so the server and the client cannot disagree about what
 * "the trend" means for a given window. It is chosen from the span: a short window is only legible
 * week by week, and a long one only month by month. The client may still override it — the choice is
 * a sensible default, not a rule.
 *
 * @param from first day covered, inclusive
 * @param to   last day covered, inclusive
 * @param unit the trend granularity this span reads best at
 */
public record AdminDateRange(LocalDate from, LocalDate to, AdminPeriodUnit unit) {
}
