package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's Step-3 breakdown toggle state, carried in the generate request for the
 * SHEET flow. Lists the breakdown section codes ({@code tp, ca, geo, aud, dev}) the user
 * enabled for this tactic; every section not listed is cleared on the generated Sheet's
 * "Breakdowns" tab.
 *
 * @param tacticNum  1-based tactic number this selection applies to
 * @param breakdowns enabled breakdown section codes for this tactic; empty/{@code null} means none
 */
public record BreakdownSelection(Integer tacticNum, List<String> breakdowns) {
	// carried verbatim from the API request
}
