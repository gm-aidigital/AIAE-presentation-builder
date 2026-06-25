package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Computed campaign frequency figures fed into Claude Batch C so the generated
 * frequency narrative ({@code {{f_oppartunity}} / {{f_fact}} / {{f_storytelling}}}) embeds the exact
 * numbers. Both values are pre-formatted to two decimals (e.g. {@code "3.45"}) or {@code null} when the
 * underlying impressions/reach figures are unavailable.
 *
 * <p>{@code plan} is the planned frequency (total impressions ÷ campaign reach); {@code fact} is the
 * actual frequency (total impressions ÷ proposal reach reduced by a random 1–10%), so it reads slightly
 * higher than {@code plan}. The figures are intermediate inputs only — they are not registered as their
 * own deck placeholders.
 *
 * @param plan planned frequency formatted to two decimals, or {@code null} when not computable
 * @param fact actual frequency formatted to two decimals, or {@code null} when not computable
 */
public record CampaignFrequencies(String plan, String fact) {

}
