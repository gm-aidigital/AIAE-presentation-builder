package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One hand-entered row of a tactic's "PERFORMANCE BY DEVICE" table on the generated workbook's
 * "Breakdowns" tab, read back verbatim so the deck shows exactly what the user reviewed.
 *
 * <p>The device labels ({@code Mobile}, {@code CTV}, {@code Desktop}, {@code Tablet}) are pre-filled
 * by the template, so a row is kept only where the user typed an impressions value; the label, not
 * the metrics, is what maps the row onto the slide's fixed per-device tokens
 * ({@code {{mobile_imps_N}}}, {@code {{ctv_vcr_N}}}, …). Every field is the raw trimmed cell string
 * rather than a parsed number, for the same reason {@link CreativeRow}'s are: the values are copied
 * straight onto the slide, so re-formatting them here would make the deck disagree with the sheet.
 *
 * @param device      the device label, from the {@code Device} column (e.g. {@code "Mobile"})
 * @param impressions the delivered impressions, from the {@code Impressions} column
 * @param ctr         the click-through rate, from the {@code CTR} column
 * @param vcr         the completion rate, from the {@code VCR} column
 * @param spend       the spend, from the {@code Spend} column
 */
public record DeviceRow(String device, String impressions, String ctr, String vcr, String spend) {
}
