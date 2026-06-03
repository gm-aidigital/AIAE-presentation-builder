package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One resolved placeholder entry for preview sections ({@code key}, label, value, source).
 */
public record Placeholder(String key, String label, String value, String source) {
}
