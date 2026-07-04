package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured output of the single Claude "sheet" batch used by the Generate Sheet flow.
 *
 * <p>The sheet template only consumes the audience fields of Batch A and the per-tactic gender/daypart
 * fields of Batch B; it never uses the Batch A proposal/strategic narrative or any Batch C copy. This DTO
 * therefore carries exactly those reused fields so the sheet path can resolve them from one Claude call
 * instead of the three slide batches.
 *
 * @param audienceAge      Claude-generated narrative describing the target audience's age profile (may be null)
 * @param audienceSegments Claude-generated description of the distinct audience segments (may be null)
 * @param byTactic         per-tactic gender/daypart insights keyed by the 1-based tactic number (may be null)
 */
public record ClaudeSheetBatch(
		String audienceAge,
		String audienceSegments,
		Map<Integer, TacticInsight> byTactic
) {

}
