package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Everything a finished SHEET run has to remember so its user can walk away, fill the generated
 * workbook by hand over the next day or two, and come back to build the deck from it — persisted
 * as the job's {@code payload_json}.
 *
 * <p>Deliberately small. The slides-from-sheet flow reads every number, name and date back out of
 * the reviewed workbook, so the raw media-plan/Elevate grids, the line-item mapping and the
 * BigQuery sheet id are not needed to finish a report and are not stored here — keeping the whole
 * source workbook bundle in a jsonb column would cost megabytes per draft for values that are
 * blanked before the deck job ever sees them.
 *
 * @param reportType            report template code the run was started with (EOC/EOM); picks the
 *                              Claude prompt flavour and which slides the deck drops
 * @param brief                 free-text campaign brief, as the fallback for a workbook whose
 *                              {@code {{RFP info}}} cell was emptied
 * @param changeLog             free-text change log, fallback for {@code {{change log}}} the same way
 * @param marketVolume          maximum addressable audience volume entered in the UI
 * @param dateFilter            user-confirmed flight window
 * @param estimateDaypartGender whether Claude may estimate the dayparting/gender tokens
 * @param breakdownSelections   the Step-3 per-tactic breakdown toggles, so the deck inserts exactly
 *                              the breakdown slides the sheet was prepared for
 * @param tacticNames           the reported tactics in order, so the resumed review table has labels
 *                              before the sheet summary read lands
 */
public record ReportResumeState(
		String reportType,
		String brief,
		String changeLog,
		String marketVolume,
		DateFilter dateFilter,
		Boolean estimateDaypartGender,
		List<BreakdownSelection> breakdownSelections,
		List<String> tacticNames) {
}
