package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The three columns of the EOM deck's "Focus next month" slide — what we carry forward unchanged, what we
 * pivot on the data, and what we test for the first time — each holding one short line per slot
 * ({@code {{carry forward 1..3}}}, {@code {{pivot 1..3}}}, {@code {{test 1..3}}}).
 *
 * <p>The three lists travel together because they are answers to one question asked three ways, and the
 * slide only reads if they stay distinct: a pivot listed as something we carry forward unchanged, or a test
 * that is really this month's pivot restated, makes the slide contradict itself. Written by one call over
 * one set of conclusions, they can be held to that; written apart, nothing could be.
 *
 * <p>Each list is positional — entry 1 fills slot 1 — and may be shorter than the slide's three slots, in
 * which case the remaining tokens render as dashes.
 *
 * @param carryForward proven plays that keep their budget unchanged, in slot order
 * @param pivots       directed corrections to what underperformed this month, in slot order
 * @param tests        new hypotheses to try next month at controlled risk, in slot order
 */
public record FocusNextMonth(
		List<String> carryForward,
		List<String> pivots,
		List<String> tests
) {
}
