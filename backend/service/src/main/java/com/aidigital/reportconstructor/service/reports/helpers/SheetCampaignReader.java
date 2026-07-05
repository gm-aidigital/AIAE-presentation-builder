package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;

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
	 * @param tacticCount      number of active tactics to reconstruct (1..7)
	 * @return the reconstructed campaign data for the Claude prompts
	 */
	CampaignData read(Map<String, String> flatReplacements, int tacticCount);

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
