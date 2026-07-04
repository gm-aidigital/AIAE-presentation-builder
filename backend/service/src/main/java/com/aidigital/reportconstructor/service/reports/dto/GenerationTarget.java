package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Which artifact a report-generation run produces from the resolved placeholder
 * map. The data-collection and placeholder-resolution pipeline is identical for
 * both targets; only the final render step and the unused-tactic trimming differ.
 */
public enum GenerationTarget {

	/**
	 * Clone the Google Slides deck template and fill it (the default "Generate Slides" flow).
	 */
	SLIDES,

	/**
	 * Clone the Google Sheets template and fill it (the "Generate Sheet" flow).
	 */
	SHEET,

	/**
	 * Clone the Google Slides deck template but fill it from a previously generated
	 * (and user-edited) Google Sheet rather than the raw input grids — step 2 of the
	 * sheet-as-source flow, where the sheet is the single input.
	 */
	SLIDES_FROM_SHEET
}
