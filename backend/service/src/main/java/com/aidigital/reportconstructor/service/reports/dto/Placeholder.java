package com.aidigital.reportconstructor.service.reports.dto;

public record Placeholder(String key, String label, String value, String source) {
    public static Placeholder notFound(String key, String label) {
        return new Placeholder(key, label, "", "not_found");
    }
}
