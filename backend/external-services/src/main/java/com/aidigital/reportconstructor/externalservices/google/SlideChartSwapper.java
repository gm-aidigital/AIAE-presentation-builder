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

    /** Position + size of a slide element plus the page it lives on. */
    public record ElementTransform(Size size, AffineTransform transform, String slideId) {}

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
