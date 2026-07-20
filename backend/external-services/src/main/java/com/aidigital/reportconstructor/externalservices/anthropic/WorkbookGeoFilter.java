package com.aidigital.reportconstructor.externalservices.anthropic;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reduces a whole media-plan workbook down to the handful of rows that can plausibly carry the campaign's
 * geographic targeting.
 *
 * <p>The workbook arrives as every tab flattened into one grid, each tab introduced by a
 * {@code "### TAB: <name> ###"} marker row. Sending that bundle verbatim was what let a fat client plan
 * push a single Claude request past the model's context window — the geo answer is one short string, but
 * the prompt carried megabytes of budgets, creative specs and pacing tables to produce it. Only rows whose
 * text mentions a geography word ({@link #GEO_WORDS}) survive here, together with the tab marker of every
 * tab that contributed at least one row, so the model still knows where each row came from.
 */
@Component
public class WorkbookGeoFilter {

	/** Marker prefix the frontend writes ahead of each tab's rows. */
	static final String TAB_MARKER_PREFIX = "### TAB:";

	/**
	 * Whole words that mark a row as geography-related. Matched case-insensitively on word boundaries so
	 * "state" does not drag in "real estate" and "market" does not drag in "marketing objectives".
	 */
	static final Pattern GEO_WORDS = Pattern.compile(
			"\\b(geo|geos|geography|geographic|geographical|geotargeting|geo-targeting|geofencing|"
					+ "dma|dmas|msa|msas|state|states|market|markets|city|cities|region|regions|"
					+ "zip|zips|zipcode|zipcodes|postal|county|counties|country|countries|location|locations|"
					+ "nationwide|national|local|metro|metros|territory|territories)\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Keeps only the geography-bearing rows of a flattened workbook, each rendered as its cells joined
	 * with {@code " | "}.
	 *
	 * <p>A tab's {@code "### TAB: <name> ###"} marker is emitted lazily: it is written only once a row
	 * from that tab is kept, so tabs with no geography content contribute nothing at all.
	 *
	 * @param geoRows every tab of the workbook flattened into one grid; {@code null} rows are skipped
	 * @return the kept rows in workbook order, tab markers included; empty when nothing matched
	 */
	public List<String> keepGeoRows(List<List<String>> geoRows) {
		List<String> kept = new ArrayList<>();
		if (geoRows == null) {
			return kept;
		}
		String pendingTab = null;
		for (List<String> row : geoRows) {
			if (row == null) {
				continue;
			}
			String text = String.join(" | ", row).trim();
			if (text.isEmpty()) {
				continue;
			}
			if (isTabMarker(text)) {
				pendingTab = text;
				continue;
			}
			if (!GEO_WORDS.matcher(text).find()) {
				continue;
			}
			if (pendingTab != null) {
				kept.add(pendingTab);
				pendingTab = null;
			}
			kept.add(text);
		}
		return kept;
	}

	/**
	 * Reports whether a rendered row is one of the frontend's tab-separator markers.
	 *
	 * @param text the rendered row text
	 * @return {@code true} when the row introduces a new tab rather than carrying plan data
	 */
	boolean isTabMarker(String text) {
		return text.startsWith(TAB_MARKER_PREFIX);
	}
}
