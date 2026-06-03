package com.aidigital.reportconstructor.service.reports.engine;

/**
 * Mirror of the PHP resolver return shape {@code ['label'=>..,'value'=>..,'source'=>..]}.
 *
 * <p>{@code value == null} means unresolved (PHP {@code not_found}); the builder turns
 * that into an em-dash {@code "—"} when flattening for the Slides API.
 */
public record Resolved(String label, String value, String source) {

    public static Resolved notFound(String label) {
        return new Resolved(label, null, "not_found");
    }

    public boolean found() {
        return !"not_found".equals(source);
    }
}
