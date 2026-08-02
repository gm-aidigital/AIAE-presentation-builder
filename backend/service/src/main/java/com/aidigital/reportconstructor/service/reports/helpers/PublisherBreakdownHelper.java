package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the "Top Publishers" breakdown slides' token values: the hand-entered publisher rows read
 * back from the generated sheet, plus the Claude-written KEY OBSERVATIONS bullets.
 *
 * <p>Exists because the deck's normal placeholder pass cannot fill these tokens: the breakdown slides
 * are duplicated from their masters only after the deck has been built and every token replaced, so
 * their values have to be handed to {@link ReportGenerationChartHelper#addBreakdownSlides} instead.
 */
public interface PublisherBreakdownHelper {

	/**
	 * Reads the publisher tables, fills the data-only slide tokens, and returns each tactic's
	 * {@link PublisherObservationInput} — WITHOUT calling Claude. The flow uses this to gather every section's
	 * inputs before making the publisher section's per-tactic call, then fills the observation tokens with
	 * {@link #writePublisherObservations}.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and totals
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's enabled tactics, per-tactic Claude inputs (non-empty tables only), and data tokens
	 */
	BreakdownSectionInputs<PublisherObservationInput> readPublisherInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Writes the KEY OBSERVATIONS tokens for every enabled tactic from the observations the publisher section call
	 * produced, blanking a tactic that came back with none and warning for one that had rows but no bullets.
	 *
	 * @param values           the accumulating token → value map to write into
	 * @param tactics          every tactic that enabled the Top Publishers breakdown
	 * @param sentTactics      the tactics whose tables were non-empty and were actually sent to Claude
	 * @param observations     tactic number → its four observation bullets, from the publisher section call
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name for warnings
	 * @return one warning per sent tactic that came back without observations; empty when all answered
	 */
	List<String> writePublisherObservations(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> observations, Map<String, String> flatReplacements);
}
