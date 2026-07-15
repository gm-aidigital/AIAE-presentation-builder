package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Optional;

/**
 * The five per-tactic breakdown sections the user can toggle on Step 3 ("Breakdowns
 * per tactic"). Each constant carries the wire {@code code} the frontend sends and the
 * header label that anchors the section on the generated Sheet's "Breakdowns" tab (each
 * tactic block repeats these headers as {@code "<label> N"}, e.g. {@code "Geo analysis 3"}).
 *
 * <p>The section's column span on the sheet is not stored here: it is derived at clear
 * time from the position of the next section's anchor on the same header row, so the
 * feature survives column layout edits to the template.
 */
public enum BreakdownType {

	/** Top Publishers table (leftmost section of each tactic block). */
	TOP_PUBLISHERS("tp", "Top Publishers"),

	/** Creative analysis section. */
	CREATIVE("ca", "Creative analysis"),

	/** Geo analysis section. */
	GEO("geo", "Geo analysis"),

	/** Audience analysis section. */
	AUDIENCE("aud", "Audience analysis"),

	/** Device breakdown section (rightmost section of each tactic block). */
	DEVICE("dev", "Device breakdown");

	private final String code;
	private final String anchorLabel;

	BreakdownType(String code, String anchorLabel) {
		this.code = code;
		this.anchorLabel = anchorLabel;
	}

	/**
	 * Returns the wire code the frontend sends for this section (e.g. {@code "geo"}).
	 *
	 * @return the stable lowercase toggle id
	 */
	public String code() {
		return code;
	}

	/**
	 * Returns the sheet header label that anchors this section's block on the "Breakdowns"
	 * tab, without the trailing tactic number (e.g. {@code "Geo analysis"}).
	 *
	 * @return the header label prefix used to locate the section
	 */
	public String anchorLabel() {
		return anchorLabel;
	}

	/**
	 * Resolves a breakdown type from its wire {@code code}, tolerant of surrounding
	 * whitespace and case, so unknown ids sent by the client are ignored rather than fatal.
	 *
	 * @param code the wire toggle id, possibly {@code null}
	 * @return the matching type, or empty when {@code code} is null/blank or unrecognised
	 */
	public static Optional<BreakdownType> fromCode(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		String normalized = code.trim().toLowerCase();
		for (BreakdownType type : values()) {
			if (type.code.equals(normalized)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}
}
