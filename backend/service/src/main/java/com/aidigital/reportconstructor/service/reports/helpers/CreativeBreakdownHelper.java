package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSectionInputs;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the "Creative analysis" breakdown slides' token values: the hand-entered creative rows and
 * stat tiles read back from the generated sheet, plus the Claude-written KEY TAKEAWAYS bullets. The
 * creative counterpart of {@link PublisherBreakdownHelper}, and it exists for the same reason: the
 * deck's normal placeholder pass cannot fill these tokens, because the breakdown slides are duplicated
 * from their masters only after the deck has been built and every token replaced, so their values have
 * to be handed to {@link ReportGenerationChartHelper#addBreakdownSlides} instead.
 */
public interface CreativeBreakdownHelper {

	/**
	 * Reads the creative blocks, fills the data-only slide tokens, and returns each tactic's
	 * {@link CreativeTakeawayInput} — WITHOUT calling Claude — for the combined per-tactic call. Takeaway
	 * tokens are filled later with {@link #writeCreativeTakeaways}.
	 *
	 * @param sheetUrl         URL of the generated, user-reviewed Google Sheet
	 * @param selections       the Step-3 per-tactic breakdown selections from the request (may be null)
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name and KPI type
	 * @param userGoogleToken  OAuth token for Google Sheets API, or null when unavailable
	 * @return the section's enabled tactics, per-tactic Claude inputs (non-empty blocks only), and data tokens
	 */
	BreakdownSectionInputs<CreativeTakeawayInput> readCreativeInputs(
			String sheetUrl, List<BreakdownSelection> selections,
			Map<String, String> flatReplacements, String userGoogleToken);

	/**
	 * Writes the KEY TAKEAWAYS tokens for every enabled tactic from the takeaways the combined call produced,
	 * blanking a tactic that came back with none and warning for one that had data but no bullets.
	 *
	 * @param values           the accumulating token → value map to write into
	 * @param tactics          every tactic that enabled the Creative analysis breakdown
	 * @param sentTactics      the tactics whose blocks were non-empty and were actually sent to Claude
	 * @param takeaways        tactic number → its four takeaway bullets, from the combined call
	 * @param flatReplacements the deck's resolved placeholder map, source of each tactic's name for warnings
	 * @return one warning per sent tactic that came back without takeaways; empty when all answered
	 */
	List<String> writeCreativeTakeaways(
			Map<String, String> values, Set<Integer> tactics, Set<Integer> sentTactics,
			Map<Integer, List<String>> takeaways, Map<String, String> flatReplacements);
}
