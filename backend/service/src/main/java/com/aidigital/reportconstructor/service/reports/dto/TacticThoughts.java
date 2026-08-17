package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * The Step-3 output for one tactic: the five {@code {{thoughts on tactic n performance 1..5}}} strings that
 * fill the per-tactic "Thoughts on tactic performance" slide. The list holds up to five entries in slide
 * order — four analytical thoughts and then the closing story, which is a narrative under its own larger
 * character budget rather than a fifth bullet. A tactic missing from the batch result got no usable reply
 * and its slide (if present) renders those tokens blank rather than invented copy.
 *
 * @param tacticNum the 1-based tactic number these thoughts belong to
 * @param thoughts  up to five length-capped strings, in slide order, the last being the tactic's story
 */
public record TacticThoughts(
		int tacticNum,
		List<String> thoughts
) {
}
