package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;

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
}
