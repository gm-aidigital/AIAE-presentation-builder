package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One hand-entered row of a tactic's "Geo analysis" table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so the deck shows exactly what the user reviewed.
 *
 * <p>Every field is the raw trimmed cell string rather than a parsed number, for the same reason
 * {@link CreativeRow}'s and {@link PublisherRow}'s are: the values are copied straight onto the
 * slide, so re-formatting them here would make the deck disagree with the sheet the user signed
 * off on.
 *
 * @param name        the geo/market name, from the {@code Geo} column
 * @param impressions the delivered impressions, from the {@code IMPS} column
 * @param kpi         the geo's lead-KPI value (CTR/VCR/ACR), from the KPI column whose header is the
 *                    tactic's own {@code {{tactic n KPI type}}}
 */
public record GeoRow(String name, String impressions, String kpi) {
}
