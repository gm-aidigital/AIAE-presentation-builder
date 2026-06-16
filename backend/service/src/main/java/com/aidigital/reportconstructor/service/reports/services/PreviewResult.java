package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;

import java.util.List;

/**
 * Preview output for the constructor UI.
 *
 * @param sections   resolved placeholder sections
 * @param labels     "all labels" chips for the sheet/adj panels
 * @param found      number of resolved placeholders
 * @param total      total number of placeholders
 * @param sheetCount number of Media-Plan (sheet) rows in the request
 * @param adjCount   number of Adjustments rows in the request
 */
public record PreviewResult(
		List<PreviewSection> sections, Labels labels, int found, int total,
		int sheetCount, int adjCount
) {

}
