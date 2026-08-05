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
 * @param plan              planned frequency rounded up to a whole number, or {@code null} when not computable
 * @param fact              actual frequency formatted to two decimals, or {@code null} when not computable
 * @param reachFact         the actual ("fact") reach used to compute {@code fact}, or {@code null} when not
 *                             computable
 * @param remainingAudience the addressable market volume minus {@code reachFact} — the in-market audience still
 *                             available for upcoming flights, or {@code null} when the market volume is unknown
 * @param reachPlan         the campaign's planned reach behind {@code plan}, summed from the reported tactics'
 *                             own Reach figures and de-duplicated once by a random factor, or {@code null} when
 *                             the media plan carries no per-tactic reach. Carried here for the same reason as
 *                             {@code reachFact}: {@code {{reach}} / {{reach_p}}} must resolve to the exact number
 *                             the frequency was computed from, not draw their own factor.
 */
public record CampaignFrequencies(String plan, String fact, Double reachFact, Double remainingAudience,
                                  Double reachPlan) {

	/**
	 * Backward-compatible constructor for callers that predate the summed plan reach.
	 *
	 * @param plan              planned frequency, or {@code null}
	 * @param fact              actual frequency, or {@code null}
	 * @param reachFact         the actual reach behind {@code fact}, or {@code null}
	 * @param remainingAudience the market volume left after {@code reachFact}, or {@code null}
	 */
	public CampaignFrequencies(String plan, String fact, Double reachFact, Double remainingAudience) {
		this(plan, fact, reachFact, remainingAudience, null);
	}
}
