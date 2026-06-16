package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.services.Labels;

/**
 * Harvests label/value chips from Media-Plan and Adjustments rows for the preview panel.
 */
public interface PlaceholderLabelCollector {

	/**
	 * Groups label chips from the sheet and adjustments row sets.
	 *
	 * @param payload constructor request whose sheet and adjustments rows are scanned
	 * @return chips grouped by sheet vs adjustments source
	 */
	Labels collectAllLabels(GeneratePayload payload);
}
