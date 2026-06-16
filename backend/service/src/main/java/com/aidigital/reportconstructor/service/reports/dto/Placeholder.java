package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One resolved placeholder entry for preview sections ({@code key}, label, value, source).
 *
 * @param key    the placeholder token identifier as it appears in the template (e.g. {@code campaign_name})
 * @param label  the human-readable display name shown for this placeholder in the preview UI
 * @param value  the resolved substitution text that replaces the token in the rendered report
 * @param source the origin of the resolved value (e.g. the data field, computed metric, or AI-gen. copy it came from)
 */
public record Placeholder(String key, String label, String value, String source) {

}
