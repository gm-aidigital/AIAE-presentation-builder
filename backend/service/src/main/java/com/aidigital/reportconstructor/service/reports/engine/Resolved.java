package com.aidigital.reportconstructor.service.reports.engine;

/**
 * Resolver return shape: a {@code label}, a {@code value}, and a {@code source} tag.
 *
 * <p>{@code value == null} means unresolved; the builder turns that into an em-dash
 * {@code "—"} when flattening for the Slides API.
 *
 * @param label  human-readable display name of the resolved placeholder/field
 * @param value  resolved string value, or {@code null} when the resolver found nothing
 * @param source origin tag identifying how the value was resolved (e.g. the resolver key), or {@code "not_found"} when unresolved
 */
public record Resolved(String label, String value, String source) {

    /**
     * Reports whether a resolver successfully produced a value for this entry.
     *
     * @return {@code true} when {@code source} is not {@code "not_found"}, i.e. a value was resolved
     */
    public boolean found() {
        return !"not_found".equals(source);
    }
}
