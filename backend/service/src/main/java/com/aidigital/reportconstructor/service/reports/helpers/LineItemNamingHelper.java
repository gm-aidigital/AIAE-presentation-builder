package com.aidigital.reportconstructor.service.reports.helpers;

/**
 * Extracts numeric line item IDs from BigQuery Level 1 Naming strings.
 */
public interface LineItemNamingHelper {

    /**
     * Parses the 9th underscore-delimited segment as a numeric line item ID.
     *
     * @param naming Level 1 Naming cell value (may be {@code null})
     * @return the numeric ID, or {@code null} when absent or invalid
     */
    String extractLineItemId(String naming);

    /**
     * Chart pivot variant that returns an empty string instead of {@code null}.
     *
     * @param naming Level 1 Naming cell value (may be {@code null})
     * @return the numeric ID, or {@code ""} when absent or invalid
     */
    String extractLineItemIdOrBlank(String naming);
}
