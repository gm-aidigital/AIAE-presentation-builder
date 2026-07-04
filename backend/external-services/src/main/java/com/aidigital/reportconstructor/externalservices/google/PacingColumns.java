package com.aidigital.reportconstructor.externalservices.google;

/**
 * Columns located for one tactic's Daily pacing (or Monthly pacing) block, relative to
 * its {@code "Daily pacing N"} / {@code "Monthly pacing N"} anchor cell. A value of
 * {@code -1} means that column could not be located within the block's search window.
 *
 * @param dataStartRow row directly above the first data row (i.e. the row carrying the
 *                     {@code {{tactic n date}}} token), or {@code -1} when not found
 * @param dateCol      column carrying {@code {{tactic n date}}} / {@code {{tactic n date mon}}}
 * @param impsCol      column carrying {@code {{tactic n impressions}}} / {@code {{tactic n impressions mon}}}
 * @param metricCol    column carrying {@code {{tactic n amount}}} / {@code {{tactic n amount mon}}}
 * @param kpiHeaderRow row of the {@code {{tactic n kpi type}}} header cell, or {@code -1} when not found
 * @param kpiHeaderCol column of the {@code {{tactic n kpi type}}} header cell, or {@code -1} when not found
 */
record PacingColumns(
		int dataStartRow,
		int dateCol,
		int impsCol,
		int metricCol,
		int kpiHeaderRow,
		int kpiHeaderCol) {

}
