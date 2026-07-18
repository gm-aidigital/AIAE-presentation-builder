package com.aidigital.reportconstructor.externalservices.google;

/**
 * A breakdown slide's embedded chart element: its object id (the element to delete when relinking) plus
 * its captured position, so the replacement chart lands in the same spot.
 *
 * @param objectId  the chart page element's object id on the breakdown slide
 * @param transform the element's captured size/transform/slide, positioning the replacement chart
 */
public record ChartElementRef(String objectId, ElementTransform transform) {
}
