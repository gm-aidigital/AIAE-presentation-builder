package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Snapshot of a single Google Sheets worksheet used as a data source for a marketing report.
 *
 * @param sheetId the Google Sheets spreadsheet identifier this data was read from
 * @param title the human-readable spreadsheet (document) title
 * @param tab the name of the worksheet tab the rows were taken from
 * @param tabs the names of all worksheet tabs available in the spreadsheet
 * @param rows the total number of data rows in the selected tab
 * @param cols the total number of columns in the selected tab
 * @param headers the column header labels from the first row of the tab
 * @param preview a truncated sample of the tab's cells for display, as rows of string values
 * @param rawRows the full set of the tab's cells, as rows of string values
 */
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
) {
	// required
}
