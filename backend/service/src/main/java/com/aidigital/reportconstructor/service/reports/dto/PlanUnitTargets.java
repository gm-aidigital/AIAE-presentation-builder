package com.aidigital.reportconstructor.service.reports.dto;

/**
 * The three planned delivery targets an EOM tactic's summary row carries, derived together from the
 * tactic's spend, unit price, rate type and the planned CTR/VCR benchmarks. Whichever unit the tactic
 * was bought in comes straight out of {@code spend / unit price}; the other two are derived from it
 * through the planned rates, so all three columns stay internally consistent.
 *
 * @param impressions planned impressions ({@code null} when not derivable)
 * @param clicks      planned clicks ({@code null} when not derivable)
 * @param completions planned completions/views ({@code null} when not derivable)
 */
public record PlanUnitTargets(Double impressions, Double clicks, Double completions) {
}
