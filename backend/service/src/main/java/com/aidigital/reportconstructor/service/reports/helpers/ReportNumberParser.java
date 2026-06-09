package com.aidigital.reportconstructor.service.reports.helpers;

/**
 * Parses numeric values from formatted report and chart cell strings.
 */
public interface ReportNumberParser {

    /**
     * Strips commas and non-numeric characters, then parses the leading number.
     *
     * @param raw formatted metric text (may be {@code null})
     * @return parsed value, or {@code 0.0} when blank or unparseable
     */
    double parseReportNumber(String raw);
}
