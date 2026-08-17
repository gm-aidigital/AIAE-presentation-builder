package com.aidigital.reportconstructor.externalservices.google;

/**
 * Where a rendered chart lands on the deck: the placeholder chart element it replaces, that element's
 * captured position, and which embedded chart to pull out of the freshly copied source workbook.
 *
 * <p>Resolved per (tactic, chart type) before rendering, because the two deck models name the placeholder
 * differently: the legacy template has one configured object id per drawn tactic slot, while the master
 * model can only learn a copy's chart element ids by scanning the copy after it was duplicated.
 *
 * @param objectId       the placeholder chart page element's object id on the tactic slide
 * @param transform      the element's captured size/transform/slide, positioning the replacement chart;
 *                       {@code null} lets the API place the chart at its default location
 * @param chartIdInSheet the embedded chart's id within the copied source workbook
 */
public record ChartTarget(String objectId, ElementTransform transform, int chartIdInSheet) {
}
