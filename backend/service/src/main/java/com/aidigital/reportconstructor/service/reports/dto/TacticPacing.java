package com.aidigital.reportconstructor.service.reports.dto;

/**
 * The channel-slide pacing narrative for one tactic: the three key-takeaway paragraphs above the METRIC
 * table and the one-line directive for the month ahead. A tactic missing from the batch result got no
 * usable reply, and its four tokens are dashed rather than filled with invented copy.
 *
 * @param tacticNum  the 1-based tactic number this narrative belongs to
 * @param whatWorked what drove the channel's result, {@code {{what worked pacing n}}}
 * @param watchOuts  the risk or opportunity visible in the channel's trend, {@code {{watch outs pacing n}}}
 * @param actions    the optimisation being run inside the channel now, {@code {{actions pacing n}}}
 * @param nextMonth  the channel's directive for the month ahead, {@code {{pacing n next month}}}
 */
public record TacticPacing(
		int tacticNum,
		String whatWorked,
		String watchOuts,
		String actions,
		String nextMonth
) {
}
