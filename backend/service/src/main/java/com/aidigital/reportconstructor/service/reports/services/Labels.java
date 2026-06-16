package com.aidigital.reportconstructor.service.reports.services;

import java.util.List;

/**
 * Label chips grouped by their source row set, shown in the preview "all labels" panel.
 *
 * @param sheet chips harvested from the Media-Plan (sheet) rows
 * @param adj   chips harvested from the manual Adjustments rows
 */
public record Labels(List<LabelChip> sheet, List<LabelChip> adj) {

}
