package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;

import java.util.Map;

/**
 * Reconstructs a {@link CampaignData} from the placeholder values read back out of a filled
 * EOC workbook, so the "Slides from Sheet" flow can feed the Claude executive/strategic batches
 * the same campaign context they get in the raw-data flow — without re-reading any source grid.
 *
 * <p>This is used purely as <em>prompt context</em>: the deck's final numbers still come from the
 * sheet placeholder map (which is overlaid on top of the Claude output), so any rounding lost in
 * the string-to-number parsing here never reaches the rendered deck.
 */
public interface SheetCampaignReader {

	/**
	 * Builds the campaign context from a sheet-read placeholder map.
	 *
	 * @param flatReplacements the {@code {{token}} → value} map read back from the sheet
	 * @param tacticCount      number of active tactics to reconstruct (1..28)
	 * @return the reconstructed campaign data for the Claude prompts
	 */
	CampaignData read(Map<String, String> flatReplacements, int tacticCount);

	/**
	 * Builds the campaign context from a sheet-read placeholder map, additionally restoring the report's
	 * calendar cadence. The sheet carries no date window of its own, so the reporting window is passed in
	 * from the request that already defined it in step 1; the flight's month count and the reporting
	 * month's place in it are read back from the workbook's own {@code {{total mon no}}}/{@code {{mon no}}}
	 * cells when it carries them. Without this the EOM cover slide would rebuild with no dates at all.
	 *
	 * @param flatReplacements the {@code {{token}} → value} map read back from the sheet
	 * @param tacticCount      number of active tactics to reconstruct (1..28)
	 * @param reportType       report template code; only {@code "EOM"} restores the month cadence
	 * @param reportingWindow  the window the report covers, from the request's date filter (may be {@code null})
	 * @return the reconstructed campaign data for the Claude prompts and the cover slide
	 */
	CampaignData read(Map<String, String> flatReplacements, int tacticCount, String reportType,
	                  FlightDates reportingWindow);

	/**
	 * Reconstructs the campaign frequency figures from a sheet-read placeholder map, deterministically
	 * and consistently with what the reviewed sheet shows — the planned frequency is total impressions
	 * ({@code {{total imps}}}) ÷ campaign reach ({@code {{reach}}}), and the actual frequency is the value
	 * the sheet already carries ({@code {{reach_f}}}). Unlike the raw-data path this never draws a fresh
	 * random reach uplift, so the Claude frequency narrative and the deck's figures match the sheet.
	 *
	 * @param flatReplacements the {@code {{token}} → value} map read back from the sheet
	 * @return the reconstructed frequency figures; any field is {@code null} when its inputs are missing
	 */
	CampaignFrequencies readFrequencies(Map<String, String> flatReplacements);
}
