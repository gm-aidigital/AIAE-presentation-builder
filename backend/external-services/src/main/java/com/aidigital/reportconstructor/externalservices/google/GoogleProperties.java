package com.aidigital.reportconstructor.externalservices.google;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the Google Slides / Drive integration, bound from
 * {@code external.google.*} (which maps the {@code GOOGLE_SERVICE_ACCOUNT_JSON},
 * {@code SLIDES_TEMPLATE_ID} and {@code SLIDES_TARGET_FOLDER_ID} env vars). The
 * live credential factory and slides/chart providers activate only when
 * {@code service-account-json} is non-blank.
 */
@Component
@ConfigurationProperties(prefix = "external.google")
public class GoogleProperties {

    /** Service-account JSON key. Live Google beans are inactive when blank. */
    private String serviceAccountJson = "";

    /** Source deck copied for every generated report. */
    private String slidesTemplateId = "";

    /** Optional Drive folder the generated deck + chart copies are placed in. */
    private String slidesTargetFolderId = "";

    /** Summary table object id for per-tactic row pruning (trimTactics). */
    private String summaryTableObjectId = "";

    /** Template slide object ids per tactic slot (1-based keys). */
    private Map<Integer, String> tacticSlideObjectIds = Map.of();

    public String getServiceAccountJson() {
        return serviceAccountJson;
    }

    public void setServiceAccountJson(String serviceAccountJson) {
        this.serviceAccountJson = serviceAccountJson;
    }

    public String getSlidesTemplateId() {
        return slidesTemplateId;
    }

    public void setSlidesTemplateId(String slidesTemplateId) {
        this.slidesTemplateId = slidesTemplateId;
    }

    public String getSlidesTargetFolderId() {
        return slidesTargetFolderId;
    }

    public void setSlidesTargetFolderId(String slidesTargetFolderId) {
        this.slidesTargetFolderId = slidesTargetFolderId;
    }

    public String getSummaryTableObjectId() {
        return summaryTableObjectId;
    }

    public void setSummaryTableObjectId(String summaryTableObjectId) {
        this.summaryTableObjectId = summaryTableObjectId;
    }

    public Map<Integer, String> getTacticSlideObjectIds() {
        return tacticSlideObjectIds;
    }

    public void setTacticSlideObjectIds(Map<Integer, String> tacticSlideObjectIds) {
        this.tacticSlideObjectIds = tacticSlideObjectIds == null ? Map.of() : tacticSlideObjectIds;
    }
}
