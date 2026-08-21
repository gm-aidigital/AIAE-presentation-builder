package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Number formatting helpers using the US locale (comma thousands separator, dot
 * decimal). Used by the resolvers so report metrics render with stable, consistent
 * formatting across the deck.
 */
@Component
public class Fmt {

	/**
	 * Rounds to the nearest integer and formats with comma grouping and no decimals.
	 *
	 * @param v the metric value to round to the nearest integer
	 * @return the rounded value as a comma-grouped string with no decimals
	 */
	public String intGroup(double v) {
		return String.format(Locale.US, "%,d", Math.round(v));
	}

	/**
	 * Rounded integer prefixed with a dollar sign.
	 *
	 * @param v the monetary amount (e.g. spend or revenue) to round and format
	 * @return the value as a dollar-prefixed, comma-grouped integer string
	 */
	public String money(double v) {
		return "$" + intGroup(v);
	}

	/**
	 * Dollar-prefixed amount that keeps cents when the value is fractional and stays whole otherwise, so a
	 * planned spend such as {@code 11812.50} renders as {@code "$11,812.50"} rather than being rounded to
	 * {@code "$11,813"}, while a whole amount like {@code 45000} still renders cleanly as {@code "$45,000"}.
	 *
	 * @param v the monetary amount to format
	 * @return the value as a dollar-prefixed, comma-grouped string with two decimals only when non-integral
	 */
	public String moneyExact(double v) {
		return v == Math.rint(v) ? money(v) : "$" + dec2(v);
	}

	/**
	 * Two decimals with comma grouping.
	 *
	 * @param v the metric value to format with two fractional digits
	 * @return the value as a comma-grouped string fixed to two decimal places
	 */
	public String dec2(double v) {
		return String.format(Locale.US, "%,.2f", v);
	}

	/**
	 * Two decimals with no thousands separator, so the string stays parseable as a plain number by
	 * Google Sheets (a grouped {@code "1,200.00"} lands in a cell as text and drops out of any
	 * {@code =SUM(...)} over that column). Used for the EOM unit-rate cell.
	 *
	 * @param v the metric value to format with two fractional digits
	 * @return the value fixed to two decimal places, ungrouped
	 */
	public String dec2Plain(double v) {
		return String.format(Locale.US, "%.2f", v);
	}

	/**
	 * Percentage that collapses to an em-dash when {@code <= 0} (the CTR/VCR dash-fallback rule).
	 *
	 * @param v the rate value (e.g. CTR or VCR) to render as a percentage
	 * @return an em-dash when {@code v <= 0}, otherwise the value as a two-decimal percentage with a trailing
	 * {@code %}
	 */
	public String pctOrDash(double v) {
		return v <= 0 ? "\u2014" : dec2(v) + "%";
	}

	/**
	 * One-decimal percentage in {@code xx.x%} notation (e.g. {@code 2.53 \u2192 "2.5%"}), used for the
	 * unified {@code {{tactic n KPI}}} value that renders either the CTR or VCR rate depending on the
	 * tactic's KPI type. The input is an already-scaled percentage, not a {@code [0,1]} ratio.
	 *
	 * @param v the rate value as a percentage (e.g. {@code 2.5} for 2.5%)
	 * @return the value fixed to one fractional digit with a trailing {@code %}
	 */
	public String pct1(double v) {
		return String.format(Locale.US, "%.1f", v) + "%";
	}

	/**
	 * Renders a count in compact notation by truncating (not rounding) toward zero:
	 * millions become one-decimal {@code "M"} (e.g. {@code 1,234,567 \u2192 "1.2M"}) and
	 * thousands become whole-number lowercase {@code "k"} (e.g. {@code 74,542 \u2192 "74k"},
	 * {@code 702,431 \u2192 "702k"}). Values below 1,000 render as a grouped integer.
	 *
	 * @param v the count to abbreviate (e.g. campaign reach)
	 * @return the compact string with a {@code M}/{@code k} suffix, or a grouped integer when below 1,000
	 */
	public String compact(double v) {
		double abs = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (abs >= 1_000_000) {
			double millions = Math.floor(abs / 100_000) / 10.0;
			return sign + String.format(Locale.US, "%.1f", millions) + "M";
		}
		if (abs >= 1_000) {
			return sign + (long) (abs / 1_000) + "k";
		}
		return intGroup(v);
	}

	/**
	 * Compact notation with an upper-case thousands suffix ({@code "74K"} rather than {@code "74k"}),
	 * the spelling the EOM cover slide uses. Identical to {@link #compact(double)} in every other
	 * respect, so both spellings truncate the same way and can never disagree on the digits.
	 *
	 * @param v the count to abbreviate
	 * @return the compact string with a {@code M}/{@code K} suffix, or a grouped integer when below 1,000
	 */
	public String compactUpper(double v) {
		return compact(v).replace("k", "K");
	}

	/**
	 * Dollar-prefixed compact notation ({@code "$1.2M"}, {@code "$74K"}), for the headline tiles that have
	 * room for a figure but not for a grouped one. Shares {@link #compactUpper(double)} so a tile and the
	 * table cell beside it can never round the same amount two ways.
	 *
	 * @param v the monetary amount to abbreviate
	 * @return the amount as a dollar-prefixed compact string
	 */
	public String moneyCompact(double v) {
		return "$" + compactUpper(v);
	}
}
