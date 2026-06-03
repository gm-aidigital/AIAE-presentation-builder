package com.aidigital.reportconstructor.service.reports.engine;

import java.util.Locale;

/**
 * Number formatting helpers that mirror PHP {@code number_format()} with the
 * US locale (comma thousands separator, dot decimal). Used by the resolvers so
 * the deck reads identically to the legacy PHP output.
 */
public final class Fmt {

    private Fmt() {}

    /** PHP {@code number_format(round($v))} — integer, comma grouping, no decimals. */
    public static String intGroup(double v) {
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    /** PHP {@code '$' . number_format(round($v))}. */
    public static String money(double v) {
        return "$" + intGroup(v);
    }

    /** PHP {@code number_format($v, 2)} — two decimals, comma grouping. */
    public static String dec2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    /** Percentage that collapses to an em-dash when {@code <= 0} (PHP CTR/VCR rule). */
    public static String pctOrDash(double v) {
        return v <= 0 ? "\u2014" : dec2(v) + "%";
    }
}
