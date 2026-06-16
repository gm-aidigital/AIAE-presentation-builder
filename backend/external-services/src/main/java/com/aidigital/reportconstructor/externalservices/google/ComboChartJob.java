package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.Pivot;

/**
 * Inputs for one combo (daily or monthly) chart.
 *
 * @param templateId  source chart-template spreadsheet id
 * @param oldObjectId slide object id of the placeholder chart to replace
 * @param copyName    name for the copied spreadsheet
 * @param pivot       pivoted chart data
 * @param tag         label used in error messages
 */
record ComboChartJob(String templateId, String oldObjectId, String copyName, Pivot pivot, String tag) {

}
