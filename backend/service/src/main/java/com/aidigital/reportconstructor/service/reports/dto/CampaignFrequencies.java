package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Computed campaign frequency figures fed into Claude Batch C so the generated
 * frequency narrative ({@code {{f_oppartunity}} / {{f_fact}} / {{f_storytelling}}}) embeds the exact
 * numbers. {@code plan} and {@code fact} are pre-formatted to two decimals (e.g. {@code "3.45"}) or
 * {@code null} when the underlying impressions/reach figures are unavailable.
 *
 * <p>{@code plan} is total impressions ÷ campaign reach (the same reach {@code {{reach}}} resolves), rounded
 * up to a whole number. {@code reachFact} is that same reach scaled once by a random 1–20% uplift, and
 * {@code fact} is total impressions ÷ {@code reachFact} (kept to two decimals). {@code reachFact} is carried
 * alongside {@code fact} so the {@code {{reach_f}} / {{reach_f_pres}}} deck placeholders resolve to the exact
 * same number that seeded the Claude {@code {{f_fact}}} narrative, instead of each drawing its own random
 * uplift.
 *
 * @param plan      planned frequency rounded up to a whole number, or {@code null} when not computable
 * @param fact      actual frequency formatted to two decimals, or {@code null} when not computable
 * @param reachFact the actual ("fact") reach used to compute {@code fact}, or {@code null} when not computable
 */
public record CampaignFrequencies(String plan, String fact, Double reachFact) {

}
