package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/** SheetData (report engine DTO). */
public record SheetData(
    String sheetId,
    String title,
    String tab,
    List<String> tabs,
    int rows,
    int cols,
    List<String> headers,
    List<List<String>> preview,
    List<List<String>> rawRows
) {}
