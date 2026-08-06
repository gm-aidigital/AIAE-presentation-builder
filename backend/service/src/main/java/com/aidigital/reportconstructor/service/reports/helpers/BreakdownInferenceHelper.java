package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;

import java.util.List;

/**
 * Works out which breakdown sections a workbook was prepared with, by looking at what its
 * "Breakdowns" tab actually carries.
 *
 * <p>Needed when a user brings a workbook the constructor never built: there is no Step-3 toggle
 * state to reuse, and without it the deck would insert no breakdown slides at all. The two kinds
 * of workbook both fall out of one rule — <em>a section counts as enabled when its table carries
 * data</em>:
 *
 * <ul>
 *   <li>A workbook this app generated has the unselected sections already blanked (see
 *       {@code ReportSheetHelper#clearUnselectedBreakdowns}), so those tables read back empty.</li>
 *   <li>A workbook filled by hand from the template still has every section's scaffolding, so
 *       "did the user type anything into it" is the only thing that distinguishes them.</li>
 * </ul>
 *
 * <p>Consequence worth knowing: a section that was enabled but left blank is inferred as disabled,
 * and its slide is dropped. For an adopted workbook that is the honest answer — the sheet is the
 * only statement of intent there is, and a blank section would have produced a blank slide.
 */
public interface BreakdownInferenceHelper {

	/**
	 * Infers the per-tactic breakdown selections from a workbook's "Breakdowns" tab.
	 *
	 * @param sheetUrl        URL of the workbook to inspect
	 * @param tacticCount     number of tactics the workbook reports; sections above it are ignored
	 * @param userGoogleToken OAuth token for Google Sheets API, or {@code null} when unavailable
	 * @return one entry per tactic in {@code 1..tacticCount}, each listing the section codes that
	 *         tactic carries data for (an empty list when it carries none)
	 */
	List<BreakdownSelection> infer(String sheetUrl, int tacticCount, String userGoogleToken);
}
