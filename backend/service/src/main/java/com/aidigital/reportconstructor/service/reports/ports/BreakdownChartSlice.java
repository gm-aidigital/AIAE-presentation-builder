package com.aidigital.reportconstructor.service.reports.ports;

/**
 * One slice of a per-tactic breakdown chart: a category label and its delivered impressions.
 *
 * <p>Only impressions are carried — the breakdown chart's other column ("share of voice") is a live
 * formula in the linked source spreadsheet, so writing the impressions alone is enough to make the
 * chart render. The label is the sheet's own device/age-bucket label (e.g. {@code "Mobile"},
 * {@code "CTV"}, {@code "25-34"}); the chart-source writer matches it against the source sheet's
 * category column and tolerates the {@code CTV} &harr; {@code Connected TV} spelling difference.
 *
 * @param label       the category label as read from the workbook's "Breakdowns" tab
 * @param impressions the delivered impressions for the category; slices at or below zero are dropped
 *                    by the caller so an unparseable or blank cell never zeroes the chart
 */
public record BreakdownChartSlice(String label, double impressions) {
}
