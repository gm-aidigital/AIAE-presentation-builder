package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Size;

/**
 * Position + size of a slide element plus the page it lives on.
 *
 * @param size      the element's bounding-box dimensions (width/height with units) as reported by the Slides API
 * @param transform the element's affine transform (scale, shear and translation) positioning it on the slide
 * @param slideId   the object ID of the slide page on which the element resides
 */
public record ElementTransform(Size size, AffineTransform transform, String slideId) {

}
