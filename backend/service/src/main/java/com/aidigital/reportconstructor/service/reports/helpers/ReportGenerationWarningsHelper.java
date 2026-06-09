package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.List;

/**
 * Parses and serialises report-job chart warning lists stored as JSON on {@code ReportJobEntity}.
 */
public interface ReportGenerationWarningsHelper {

    /**
     * Deserialises a stored warnings JSON array into a list of warning strings.
     *
     * @param warningsJson JSON array of warning strings, or null/blank when none were recorded
     * @return the parsed warnings, or an empty list when absent or malformed
     */
    List<String> parseWarnings(String warningsJson);

    /**
     * Serialises chart warnings for persistence on the report job.
     *
     * @param warnings chart or slide warnings collected during generation; null or empty yields null
     * @return JSON array text suitable for {@code ReportJobEntity.warningsJson}, or null when empty
     */
    String serializeWarnings(List<String> warnings);
}
