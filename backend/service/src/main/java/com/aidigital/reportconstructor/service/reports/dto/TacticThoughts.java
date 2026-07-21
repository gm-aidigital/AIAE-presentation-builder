package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The Step-3 output for one tactic: the four {@code {{thoughts on tactic n performance 1..4}}} strings that
 * fill the per-tactic "Thoughts on tactic performance" slide. The list holds up to four entries in slide
 * order, each length-capped like {@code {{thoughts on the performance N}}}; a tactic missing from the batch
 * result got no usable reply and its slide (if present) renders those tokens blank rather than invented copy.
 *
 * @param tacticNum the 1-based tactic number these thoughts belong to
 * @param thoughts  up to four length-capped thought strings, in slide order
 */
public record TacticThoughts(
		int tacticNum,
		List<String> thoughts
) {
}
