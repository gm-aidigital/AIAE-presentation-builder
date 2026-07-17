package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's hand-entered "Creative analysis" block on the generated workbook's "Breakdowns" tab:
 * the four summary cells above the table plus the table's filled rows.
 *
 * <p>The summary cells are read as typed rather than derived from {@link #rows()} — the user may
 * scope them differently from the top-five table (creatives live counts every creative, not just the
 * five shown), so recomputing them here would contradict the sheet the user reviewed.
 *
 * @param creativesLive the {@code CREATIVES LIVE} count
 * @param bestKpi       the {@code BEST CTR / VCR} value, whichever KPI the tactic is led by
 * @param avgKpi        the {@code AVG. CTR / VCR} value, whichever KPI the tactic is led by
 * @param topCreative   the {@code TOP CREATIVE} name
 * @param rows          the table's filled rows, in sheet order; never padded with empty rows
 */
public record CreativeTable(
		String creativesLive, String bestKpi, String avgKpi, String topCreative, List<CreativeRow> rows) {

	/**
	 * Returns the empty table, used for a tactic whose block is missing from the sheet or was left
	 * entirely blank, so callers never have to null-check the block itself.
	 *
	 * @return a table with blank summary cells and no rows
	 */
	public static CreativeTable empty() {
		return new CreativeTable("", "", "", "", List.of());
	}

	/**
	 * Reports whether the user filled in nothing at all for this tactic. Such a tactic still gets its
	 * slide (the toggle was on) but is never sent to Claude — there would be nothing to observe.
	 *
	 * @return true when every summary cell is blank and the table carries no rows
	 */
	public boolean isEmpty() {
		return rows.isEmpty() && isBlank(creativesLive) && isBlank(bestKpi) && isBlank(avgKpi) && isBlank(topCreative);
	}

	/**
	 * Null-tolerant blank check for one summary cell.
	 *
	 * @param value the cell value, possibly {@code null}
	 * @return true when the cell is null or holds only whitespace
	 */
	static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
