package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

	/**
	 * Service-account JSON key. Live Google beans are inactive when blank.
	 */
	private String serviceAccountJson = "";

	/**
	 * Source deck copied for every generated report.
	 */
	private String slidesTemplateId = "";

	/**
	 * Optional Drive folder the generated deck + chart copies are placed in.
	 */
	private String slidesTargetFolderId = "";

	/**
	 * Summary table object id for per-tactic row pruning (trimTactics).
	 */
	private String summaryTableObjectId = "";

	/**
	 * Template slide object ids per tactic slot (1-based keys).
	 */
	private Map<Integer, String> tacticSlideObjectIds = Map.of();

	/**
	 * Source Sheets workbook copied for every generated report in the "Generate
	 * Sheet" flow. Independent of {@link #slidesTemplateId}.
	 */
	private String sheetsTemplateId = "";

	/**
	 * Optional Drive folder the generated Sheet copy is placed in. Falls back to
	 * the copy's default location (the user's My Drive or the service account)
	 * when blank.
	 */
	private String sheetsTargetFolderId = "";

	/**
	 * Optional list of email addresses every generated deck is auto-shared with
	 * (as writers), so an admin/owner keeps access even when a report is created
	 * in another user's My Drive. Bound from the comma-separated
	 * {@code SLIDES_SHARE_WITH_EMAILS} env var; empty means no auto-share.
	 */
	private List<String> shareWithEmails = List.of();

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

	public String getSheetsTemplateId() {
		return sheetsTemplateId;
	}

	public void setSheetsTemplateId(String sheetsTemplateId) {
		this.sheetsTemplateId = sheetsTemplateId;
	}

	public String getSheetsTargetFolderId() {
		return sheetsTargetFolderId;
	}

	public void setSheetsTargetFolderId(String sheetsTargetFolderId) {
		this.sheetsTargetFolderId = sheetsTargetFolderId;
	}

	public List<String> getShareWithEmails() {
		return shareWithEmails;
	}

	public void setShareWithEmails(List<String> shareWithEmails) {
		this.shareWithEmails = shareWithEmails == null ? List.of() : shareWithEmails;
	}
}
