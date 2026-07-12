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
	 * Summary-table object ids per tactic group (1-based keys: group 1 → tactics 1–7,
	 * group 2 → 8–14, …), used to prune the last partial group's unused rows in trimTactics.
	 */
	private Map<Integer, String> summaryTableObjectIds = Map.of();

	/**
	 * Summary-slide object ids per tactic group (1-based keys). Groups with no tactics are
	 * deleted whole in trimTactics.
	 */
	private Map<Integer, String> summarySlideObjectIds = Map.of();

	/**
	 * "Our results" slide object ids per tactic group (1-based keys). Groups with no tactics are
	 * deleted whole in trimTactics.
	 */
	private Map<Integer, String> resultsSlideObjectIds = Map.of();

	/**
	 * Template slide object ids per tactic slot (1-based keys, 1..28).
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

	/**
	 * Returns the summary-table object ids keyed by tactic group, used to prune the last partial
	 * group's unused rows in {@code trimTactics}.
	 *
	 * @return map of 1-based tactic-group number to its summary-table object id
	 */
	public Map<Integer, String> getSummaryTableObjectIds() {
		return summaryTableObjectIds;
	}

	/**
	 * Sets the per-group summary-table object ids, defaulting to an empty map when null.
	 *
	 * @param summaryTableObjectIds map of 1-based tactic-group number to summary-table object id (may be null)
	 */
	public void setSummaryTableObjectIds(Map<Integer, String> summaryTableObjectIds) {
		this.summaryTableObjectIds = summaryTableObjectIds == null ? Map.of() : summaryTableObjectIds;
	}

	/**
	 * Returns the summary-slide object ids keyed by tactic group; slides for empty groups are deleted whole.
	 *
	 * @return map of 1-based tactic-group number to its summary-slide object id
	 */
	public Map<Integer, String> getSummarySlideObjectIds() {
		return summarySlideObjectIds;
	}

	/**
	 * Sets the per-group summary-slide object ids, defaulting to an empty map when null.
	 *
	 * @param summarySlideObjectIds map of 1-based tactic-group number to summary-slide object id (may be null)
	 */
	public void setSummarySlideObjectIds(Map<Integer, String> summarySlideObjectIds) {
		this.summarySlideObjectIds = summarySlideObjectIds == null ? Map.of() : summarySlideObjectIds;
	}

	/**
	 * Returns the "Our results" slide object ids keyed by tactic group; slides for empty groups are deleted whole.
	 *
	 * @return map of 1-based tactic-group number to its "Our results" slide object id
	 */
	public Map<Integer, String> getResultsSlideObjectIds() {
		return resultsSlideObjectIds;
	}

	/**
	 * Sets the per-group "Our results" slide object ids, defaulting to an empty map when null.
	 *
	 * @param resultsSlideObjectIds map of 1-based tactic-group number to "Our results" slide object id (may be null)
	 */
	public void setResultsSlideObjectIds(Map<Integer, String> resultsSlideObjectIds) {
		this.resultsSlideObjectIds = resultsSlideObjectIds == null ? Map.of() : resultsSlideObjectIds;
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
