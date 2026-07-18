package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Structured output of Claude Batch D (narrative alignment) — a final harmonisation pass that reads the
 * already-generated campaign-level conclusions from Batch A ({@link ClaudeStrategic}) and Batch C
 * ({@link ClaudeResults}), plus a read-only digest of the per-tactic breakdown conclusions, and rewrites
 * the cross-cutting story fields so the whole deck tells one consistent narrative that stays faithful to
 * the campaign brief.
 *
 * <p>The pass never touches the fields no single storyline should override — {@code audienceAge} /
 * {@code audienceSegments} (owned by the reviewed sheet), the per-tactic overviews, and the optimisation
 * recommendations — so the returned records carry those through from the originals unchanged. Only the
 * proposal overview, the four strategic insights, the per-group results overviews, the performance thoughts,
 * and the three frequency-narrative strings are rewritten.
 *
 * <p>When the alignment call fails, times out, or is not live, the client returns the originals verbatim, so
 * an unaligned deck is always at least as good as it was before this pass ran.
 *
 * @param strategic the Batch A record with its {@code proposalOverview} and {@code strategicInsights} aligned,
 *                  and every other field carried through from the original
 * @param results   the Batch C record with its {@code resultsOverviews}, {@code thoughtsOnPerformance} and
 *                  frequency-narrative fields aligned, and every other field carried through from the original
 */
public record ClaudeNarrative(
		ClaudeStrategic strategic,
		ClaudeResults results
) {

}
