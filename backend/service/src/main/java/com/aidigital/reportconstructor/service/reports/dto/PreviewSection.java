package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One section of a report preview, grouping the placeholders that render under a shared heading.
 *
 * @param title        the display heading shown above this section in the rendered preview
 * @param placeholders the ordered campaign/tactic placeholders that belong to this section and are
 *                     resolved when the preview is generated
 */
public record PreviewSection(String title, List<Placeholder> placeholders) {
	// required
}
