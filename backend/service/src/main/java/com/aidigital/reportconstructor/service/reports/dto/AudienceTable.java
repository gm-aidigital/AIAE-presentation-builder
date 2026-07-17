package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's hand-entered "Audience analysis" block on the generated workbook's "Breakdowns" tab:
 * the two stat tiles above the tables plus the two sub-tables the block carries — the
 * age-distribution rows (age &rarr; impressions) and the top-audience-segments rows (segment &rarr;
 * affinity index).
 *
 * <p>The {@code TOP SEGMENT} stat tile is not stored separately: the slide's {@code {{aud_N_1}}}
 * token is the first segment row's name, so it is derived from {@link #segmentRows()} rather than
 * read twice. The age rows never become slide tokens — the master slide renders them as an embedded
 * chart — but are kept so the audience-insights batch can reason over the numbers behind that chart.
 *
 * @param ageDistribution    the {@code AGE DISTRIBUTION} stat tile ({@code {{age_N_gr}}}), the
 *                           dominant age group as the user typed it
 * @param genderDemographics the {@code GENDER DEMOGRAPHICS} stat tile ({@code {{gender_N}}}), the
 *                           gender split as the user typed it
 * @param ageRows            the age-distribution sub-table's filled rows, in sheet order; never
 *                           padded with empty rows
 * @param segmentRows        the top-audience-segments sub-table's filled rows, in sheet order; never
 *                           padded with empty rows
 */
public record AudienceTable(
		String ageDistribution, String genderDemographics,
		List<AudienceAgeRow> ageRows, List<AudienceSegmentRow> segmentRows) {

	/**
	 * Returns the empty table, used for a tactic whose block is missing from the sheet or was left
	 * entirely blank, so callers never have to null-check the block itself.
	 *
	 * @return a table with blank stat tiles and no rows
	 */
	public static AudienceTable empty() {
		return new AudienceTable("", "", List.of(), List.of());
	}

	/**
	 * Reports whether the user filled in nothing at all for this tactic. Such a tactic still gets its
	 * slide (the toggle was on) but is never sent to Claude — there would be nothing to observe.
	 *
	 * @return true when both stat tiles are blank and neither sub-table carries a row
	 */
	public boolean isEmpty() {
		return ageRows.isEmpty() && segmentRows.isEmpty()
				&& isBlank(ageDistribution) && isBlank(genderDemographics);
	}

	/**
	 * Null-tolerant blank check for one stat-tile cell.
	 *
	 * @param value the cell value, possibly {@code null}
	 * @return true when the cell is null or holds only whitespace
	 */
	static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
