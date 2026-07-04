package com.aidigital.reportconstructor.externalservices.google;

/**
 * Rows located for one tactic's Channel Distribution block, relative to its
 * {@code "Channel Distribution N"} anchor cell. A value of {@code -1} means that row
 * could not be located within the block's search window.
 *
 * @param labelCol  column carrying the {@code {{tactic n}}} slice label (and, one row down,
 *                  the "Other"/"Total" label); the impressions value is written one column right
 * @param tacticRow row carrying the {@code {{tactic n}}} slice label, or {@code -1} when not found
 * @param otherRow  row carrying the "Other"/"Total"/"Rest" slice label below {@code tacticRow},
 *                  or {@code -1} when not found
 */
record DistributionColumns(int labelCol, int tacticRow, int otherRow) {

}
