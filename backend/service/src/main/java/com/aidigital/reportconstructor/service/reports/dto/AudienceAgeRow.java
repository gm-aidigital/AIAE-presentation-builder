package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One hand-entered row of a tactic's age-distribution sub-table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so Claude reasons over exactly what the user reviewed.
 *
 * <p>The age-distribution sub-table is not shown on the slide as text — the master slide visualises
 * it as an embedded chart — so these rows never become slide tokens; they exist only to give the
 * audience-insights batch the numbers behind the chart. Both fields are the raw trimmed cell string
 * rather than a parsed number, for the same reason {@link GeoRow}'s are: re-formatting them here
 * would make the copy disagree with the sheet the user signed off on.
 *
 * @param ageGroup    the age bucket label (e.g. {@code "25-34"}), pre-filled by the template in the
 *                    {@code age} column
 * @param impressions the delivered impressions the user typed in the {@code impressions} column
 */
public record AudienceAgeRow(String ageGroup, String impressions) {
}
