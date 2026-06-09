package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.CreateSheetsChartRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.PageElementProperties;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.Size;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures slide element transforms and swaps placeholder charts for LINKED Sheets charts.
 */
@Component
public class SlideChartSwapper {

    private final ChartTemplateCatalog templates;

    public SlideChartSwapper(ChartTemplateCatalog templates) {
        this.templates = templates;
    }

    /**
     * Position + size of a slide element plus the page it lives on.
     *
     * @param size      the element's bounding-box dimensions (width/height with units) as reported by the Slides API
     * @param transform the element's affine transform (scale, shear and translation) positioning it on the slide
     * @param slideId   the object ID of the slide page on which the element resides
     */
    public record ElementTransform(Size size, AffineTransform transform, String slideId) { }

    /**
     * Reads the presentation layout and captures every page element's size, transform and owning slide,
     * keyed by element object ID, so charts can later be re-created in the same spot.
     *
     * @param slides         the authenticated Slides API client used to fetch the presentation
     * @param presentationId the ID of the Google Slides presentation to scan
     * @param errors         accumulator to which a human-readable message is appended if the layout cannot be read
     * @param tag            label prefixed to any error message to identify the failing report/section
     * @return a map from each page element's object ID to its captured {@link ElementTransform}; empty if the read failed
     */
    public Map<String, ElementTransform> loadTransforms(
            Slides slides, String presentationId, List<String> errors, String tag) {
        Map<String, ElementTransform> out = new LinkedHashMap<>();
        try {
            Presentation pres = slides.presentations().get(presentationId).execute();
            if (pres.getSlides() != null) {
                for (Page slide : pres.getSlides()) {
                    String slideId = slide.getObjectId();
                    if (slide.getPageElements() == null) {
                        continue;
                    }
                    for (PageElement el : slide.getPageElements()) {
                        if (el.getObjectId() != null) {
                            out.put(el.getObjectId(), new ElementTransform(el.getSize(), el.getTransform(), slideId));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            errors.add(tag + ": could not read presentation layout — " + ex.getMessage());
        }
        return out;
    }

    /**
     * Deletes the placeholder chart and inserts a LINKED Sheets chart from the new spreadsheet at the
     * captured element position, in a single batch update so the slide chart reflects the report's data.
     *
     * @param slides           the authenticated Slides API client used to issue the batch update
     * @param presentationId   the ID of the Google Slides presentation being edited
     * @param oldObjectId      the object ID of the existing placeholder chart element to delete
     * @param newSpreadsheetId the ID of the Sheets spreadsheet whose chart is linked into the slide
     * @param xform            the previously captured size/transform/slide of the old chart; when complete,
     *                         positions the new chart identically, otherwise the API places it at a default location
     * @throws IOException if the batch update request to the Slides API fails
     */
    public void replaceChartOnSlide(
            Slides slides,
            String presentationId,
            String oldObjectId,
            String newSpreadsheetId,
            ElementTransform xform) throws IOException {
        String newObjectId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        CreateSheetsChartRequest create = new CreateSheetsChartRequest()
            .setObjectId(newObjectId)
            .setSpreadsheetId(newSpreadsheetId)
            .setChartId(templates.getChartIdInSheet())
            .setLinkingMode("LINKED");

        if (xform != null && xform.size() != null && xform.transform() != null) {
            create.setElementProperties(new PageElementProperties()
                .setPageObjectId(xform.slideId() == null ? "" : xform.slideId())
                .setSize(xform.size())
                .setTransform(xform.transform()));
        }

        List<com.google.api.services.slides.v1.model.Request> requests = List.of(
            new com.google.api.services.slides.v1.model.Request()
                .setDeleteObject(new DeleteObjectRequest().setObjectId(oldObjectId)),
            new com.google.api.services.slides.v1.model.Request().setCreateSheetsChart(create)
        );

        slides.presentations().batchUpdate(presentationId,
            new BatchUpdatePresentationRequest().setRequests(requests)).execute();
    }
}
