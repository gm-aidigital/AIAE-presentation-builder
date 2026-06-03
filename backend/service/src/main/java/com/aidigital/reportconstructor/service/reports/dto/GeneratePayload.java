package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/** GeneratePayload (report engine DTO). */
public record GeneratePayload(
    String brief,
    String reportType,
    List<List<String>> sheetRows,
    List<List<String>> adjRows,
    List<List<String>> audienceRows,
    List<List<String>> estimatesRows,
    List<List<String>> geoRows,
    List<LineItemMapping> lineItemMapping,
    String bqSheetId
) {
    /** LineItemMapping (report engine DTO). */
    public record LineItemMapping(String tactic, String lineItemId, Integer tacticNum) {}
}
