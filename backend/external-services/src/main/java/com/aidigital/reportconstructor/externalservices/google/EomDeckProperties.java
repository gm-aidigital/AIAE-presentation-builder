package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Typed configuration for the EOM deck, bound from {@code external.eom-deck.*}. Deliberately separate
 * from {@link GoogleProperties}: an EOM deck is built from its own template with its own slide model, and
 * keeping its settings in their own class is what stops an EOM change from reaching the EOC deck.
 *
 * <p>{@link #slidesTemplateId} is the single switch for the whole EOM deck template: blank means the EOM
 * deck keeps being built from the EOC template (today's behaviour), non-blank moves it onto its own. The
 * published app clears the backing {@code EOM_SLIDES_TEMPLATE_ID} variable in
 * {@code scripts/lib/deploy-env.sh}, so production cannot pick the new template up from a shared
 * environment while it is still being tested in the Replit workspace.
 */
@Component
@ConfigurationProperties(prefix = "external.eom-deck")
public class EomDeckProperties {

	/**
	 * Source deck copied for EOM reports. Blank falls back to the EOC template
	 * ({@code external.google.slides-template-id}), which is the safe no-op default.
	 */
	private String slidesTemplateId = "";

	/**
	 * Object ids of the slides an EOM deck must never ship. Ids survive the Drive copy the deck is made
	 * from, so unlike a title they cannot drift when the template's wording is edited. Ids configured but
	 * absent from the deck are skipped. Slides not covered here fall back to {@link #dropSlideTitles}.
	 */
	private List<String> dropSlideObjectIds = List.of();

	/**
	 * Text phrases identifying the slides an EOM deck must never ship, used for slides not covered by
	 * {@link #dropSlideObjectIds} and less robust: an edit to the template's wording silently stops the
	 * match. Phrases must be static template text — the pass runs after the deck is filled, so a
	 * {@code {{token}}} is already gone by then.
	 */
	private List<String> dropSlideTitles = List.of();

	/**
	 * Returns the EOM deck template id.
	 *
	 * @return the configured template id, or an empty string when the EOM deck has no template of its own
	 */
	public String getSlidesTemplateId() {
		return slidesTemplateId;
	}

	/**
	 * Sets the EOM deck template id, treating {@code null} as unconfigured.
	 *
	 * @param slidesTemplateId the EOM Google Slides template id (may be {@code null})
	 */
	public void setSlidesTemplateId(String slidesTemplateId) {
		this.slidesTemplateId = slidesTemplateId == null ? "" : slidesTemplateId;
	}

	/**
	 * Returns the object ids of the slides an EOM deck drops.
	 *
	 * @return the configured slide object ids, never {@code null}
	 */
	public List<String> getDropSlideObjectIds() {
		return dropSlideObjectIds;
	}

	/**
	 * Sets the object ids of the slides an EOM deck drops, treating {@code null} as none.
	 *
	 * @param dropSlideObjectIds the slide object ids to delete (may be {@code null})
	 */
	public void setDropSlideObjectIds(List<String> dropSlideObjectIds) {
		this.dropSlideObjectIds = dropSlideObjectIds == null ? List.of() : List.copyOf(dropSlideObjectIds);
	}

	/**
	 * Returns the text phrases identifying the slides an EOM deck drops.
	 *
	 * @return the configured title phrases, never {@code null}
	 */
	public List<String> getDropSlideTitles() {
		return dropSlideTitles;
	}

	/**
	 * Sets the text phrases identifying the slides an EOM deck drops, treating {@code null} as none.
	 *
	 * @param dropSlideTitles the title phrases to match (may be {@code null})
	 */
	public void setDropSlideTitles(List<String> dropSlideTitles) {
		this.dropSlideTitles = dropSlideTitles == null ? List.of() : List.copyOf(dropSlideTitles);
	}
}
