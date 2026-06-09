package com.aidigital.reportconstructor.service.reports.engine;

import java.util.Locale;

import org.springframework.stereotype.Component;

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
     * Two decimals with comma grouping.
     *
     * @param v the metric value to format with two fractional digits
     * @return the value as a comma-grouped string fixed to two decimal places
     */
    public String dec2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    /**
     * Percentage that collapses to an em-dash when {@code <= 0} (the CTR/VCR dash-fallback rule).
     *
     * @param v the rate value (e.g. CTR or VCR) to render as a percentage
     * @return an em-dash when {@code v <= 0}, otherwise the value as a two-decimal percentage with a trailing {@code %}
     */
    public String pctOrDash(double v) {
        return v <= 0 ? "\u2014" : dec2(v) + "%";
    }
}
