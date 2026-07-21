package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the "Audience analysis" breakdown slides' token values: the hand-entered stat tiles and
 * top-audience-segment rows read back from the generated sheet, plus the Claude-written key takeaway,
 * "what worked", watch-out and recommended action. The audience counterpart of
 * {@link PublisherBreakdownHelper}, {@link CreativeBreakdownHelper} and {@link GeoBreakdownHelper},
 * and it exists for the same reason: the deck's normal placeholder pass cannot fill these tokens,
 * because the breakdown slides are duplicated from their masters only after the deck has been built
 * and every token replaced, so their values have to be handed to
 * {@link ReportGenerationChartHelper#addBreakdownSlides} instead.
 *
 * <p>The master slide's age-distribution chart is <em>not</em> filled here: it is a real embedded
 * Sheets chart the copies duplicate empty, which the text-only breakdown mechanism cannot populate.
 * Only the slide's text — the stat tiles, the segment table and the four Claude fields — is produced.
 */
public interface AudienceBreakdownHelper {

	/**
	 * Reads the audience blocks, fills the data-only slide tokens, and returns each tactic's
	 * {@link AudienceInsightInput} — WITHOUT calling Claude — for the combined per-tactic call. The copy
	 * tokens are filled later with {@link #writeAudienceInsights}.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and gender split
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's enabled tactics, per-tactic Claude inputs (non-empty blocks only), and data tokens
	 */
	BreakdownSectionInputs<AudienceInsightInput> readAudienceInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Writes the takeaway / what-worked / watch-out / recommendation tokens for every enabled tactic from the
	 * strings the combined call produced, blanking a tactic that came back with none and warning for one that
	 * had data but no strings.
	 *
	 * @param values           the accumulating token → value map to write into
	 * @param tactics          every tactic that enabled the Audience analysis breakdown
	 * @param sentTactics      the tactics whose blocks were non-empty and were actually sent to Claude
	 * @param insights         tactic number → its four strings (takeaway, worked, flag, reco)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name for warnings
	 * @return one warning per sent tactic that came back without copy; empty when all answered
	 */
	List<String> writeAudienceInsights(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> insights, Map<String, String> flatReplacements);
}
