package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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

	/**
	 * Lookup from each type's lowercase wire {@code code} to the type, for resolving codes sent by the
	 * frontend. Exposed as a shared {@code static final} constant (rather than a static factory method,
	 * which the structure rules forbid) so both modules can resolve a code without a static utility;
	 * callers normalise their input to lowercase before the lookup and treat a {@code null} result as
	 * "unrecognised". Unmodifiable and keyed on the same codes returned by {@link #code()}.
	 */
	public static final Map<String, BreakdownType> BY_CODE =
			Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(BreakdownType::code, type -> type));

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
}
