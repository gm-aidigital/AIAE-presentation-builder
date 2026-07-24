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
	 * Source deck copied for EOM reports. Blank falls back to {@link #slidesTemplateId} (safe no-op
	 * until the EOM deck template is configured), same convention as the other blank-disables-feature
	 * template ids in this class.
	 */
	private String eomSlidesTemplateId = "";

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
	 * Master breakdown-slide object ids, keyed by the {@code BreakdownType} wire code
	 * ({@code tp}, {@code ca}, {@code geo}, {@code aud}, {@code dev}). Each id points at a
	 * single generic master slide in the template (tokens carry the literal {@code n} tactic
	 * variable). For every enabled (tactic, breakdown) pair the master is duplicated, its
	 * {@code n} tokens are renumbered to the tactic number, and the copy is placed after that
	 * tactic's main slide; the masters themselves are deleted at the end. An empty map disables
	 * the feature (safe no-op) — populate it once the master slides are added to the live deck.
	 */
	private Map<String, String> breakdownMasterSlideObjectIds = Map.of();

	/**
	 * Master object id of the single generic "Thoughts on tactic performance" slide in the template. The
	 * slide carries the literal {@code n} tactic variable in its {@code {{thoughts on tactic n performance
	 * 1..4}}} tokens. It is duplicated once per tactic that enables more than two breakdown sections, placed
	 * right after that tactic's last breakdown copy, and the master itself is deleted at the end. Blank
	 * disables the feature (safe no-op) — set it once the master slide is added to the live deck.
	 */
	private String thoughtsMasterSlideObjectId = "";

	/**
	 * Source Sheets workbook copied for every generated report in the "Generate
	 * Sheet" flow. Independent of {@link #slidesTemplateId}.
	 */
	private String sheetsTemplateId = "";

	/**
	 * Source Sheets workbook copied for EOM reports in the "Generate Sheet" flow. Blank falls back to
	 * {@link #sheetsTemplateId} (safe no-op until the EOM sheet template is configured).
	 */
	private String eomSheetsTemplateId = "";

	/**
	 * Optional Drive folder the generated Sheet copy is placed in. Falls back to
	 * the copy's default location (the user's My Drive or the service account)
	 * when blank.
	 */
	private String sheetsTargetFolderId = "";

	/**
	 * Optional list of email addresses every generated file (deck and EOC sheet) is
	 * auto-shared with (as writers), so an admin/owner keeps access even when a report
	 * is created in another user's My Drive. Bound from the comma-separated
	 * {@code SLIDES_SHARE_WITH_EMAILS} env var. Admins are shared with regardless of
	 * this list — see {@code DriveShareRecipients}.
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

	/**
	 * Returns the EOM deck template id, or a blank string when unconfigured (callers fall back to
	 * {@link #getSlidesTemplateId()}).
	 *
	 * @return the EOM Google Slides template id, or blank when unconfigured
	 */
	public String getEomSlidesTemplateId() {
		return eomSlidesTemplateId;
	}

	/**
	 * Sets the EOM deck template id, defaulting to a blank string when null.
	 *
	 * @param eomSlidesTemplateId the EOM Google Slides template id (may be null)
	 */
	public void setEomSlidesTemplateId(String eomSlidesTemplateId) {
		this.eomSlidesTemplateId = eomSlidesTemplateId == null ? "" : eomSlidesTemplateId;
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

	/**
	 * Returns the master breakdown-slide object ids keyed by {@code BreakdownType} wire code; an
	 * empty map disables per-tactic breakdown slides.
	 *
	 * @return map of breakdown wire code ({@code tp}/{@code ca}/{@code geo}/{@code aud}/{@code dev})
	 *         to its master slide object id
	 */
	public Map<String, String> getBreakdownMasterSlideObjectIds() {
		return breakdownMasterSlideObjectIds;
	}

	/**
	 * Sets the master breakdown-slide object ids, defaulting to an empty map when null.
	 *
	 * @param breakdownMasterSlideObjectIds map of breakdown wire code to master slide object id (may be null)
	 */
	public void setBreakdownMasterSlideObjectIds(Map<String, String> breakdownMasterSlideObjectIds) {
		this.breakdownMasterSlideObjectIds =
				breakdownMasterSlideObjectIds == null ? Map.of() : breakdownMasterSlideObjectIds;
	}

	/**
	 * Returns the master object id of the "Thoughts on tactic performance" slide; blank disables the
	 * per-tactic thoughts slide.
	 *
	 * @return the thoughts master slide object id, or a blank string when unconfigured
	 */
	public String getThoughtsMasterSlideObjectId() {
		return thoughtsMasterSlideObjectId;
	}

	/**
	 * Sets the thoughts master slide object id, defaulting to a blank string when null.
	 *
	 * @param thoughtsMasterSlideObjectId the master slide object id (may be null)
	 */
	public void setThoughtsMasterSlideObjectId(String thoughtsMasterSlideObjectId) {
		this.thoughtsMasterSlideObjectId = thoughtsMasterSlideObjectId == null ? "" : thoughtsMasterSlideObjectId;
	}

	public String getSheetsTemplateId() {
		return sheetsTemplateId;
	}

	public void setSheetsTemplateId(String sheetsTemplateId) {
		this.sheetsTemplateId = sheetsTemplateId;
	}

	/**
	 * Returns the EOM sheet template id, or a blank string when unconfigured (callers fall back to
	 * {@link #getSheetsTemplateId()}).
	 *
	 * @return the EOM Google Sheets template id, or blank when unconfigured
	 */
	public String getEomSheetsTemplateId() {
		return eomSheetsTemplateId;
	}

	/**
	 * Sets the EOM sheet template id, defaulting to a blank string when null.
	 *
	 * @param eomSheetsTemplateId the EOM Google Sheets template id (may be null)
	 */
	public void setEomSheetsTemplateId(String eomSheetsTemplateId) {
		this.eomSheetsTemplateId = eomSheetsTemplateId == null ? "" : eomSheetsTemplateId;
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
