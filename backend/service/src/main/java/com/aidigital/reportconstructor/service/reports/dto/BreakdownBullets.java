package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;
import java.util.Map;

/**
 * Every breakdown section's per-tactic slide copy for one run, as produced by the dedicated per-section
 * calls. Each map is keyed by 1-based tactic number and only holds the tactics whose section call returned a
 * usable reply, so a missing key means "not enabled, or came back with nothing" — the same contract the
 * section write helpers already read.
 *
 * <p>This is what the Step-3 thoughts inputs and the Step-4 campaign digests are built from, so the copy the
 * breakdown slides ship is the copy those calls reason over.
 *
 * @param publisher tactic number → the four "Top Publishers" bullets
 * @param creative  tactic number → the four "Creative analysis" bullets
 * @param geo       tactic number → the five "Geo analysis" strings (four insights then the recommendation)
 * @param audience  tactic number → the four "Audience analysis" strings (takeaway, worked, flag, reco)
 * @param device    tactic number → the four "Device breakdown" strings (takeaway, worked, flag, reco)
 */
public record BreakdownBullets(
		Map<Integer, List<String>> publisher,
		Map<Integer, List<String>> creative,
		Map<Integer, List<String>> geo,
		Map<Integer, List<String>> audience,
		Map<Integer, List<String>> device
) {
}
