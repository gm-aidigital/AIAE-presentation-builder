package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * Service-layer view of a report job's progress, returned by
 * {@code ReportGenerationService#progress}. Carries already-parsed warnings and
 * null-safe fields so the application layer only has to map it to the API
 * model.
 *
 * @param step     current progress step (0–7)
 * @param total    total number of steps
 * @param label    human-readable step label
 * @param status   job status (queued/running/done/error)
 * @param slideUrl resulting deck URL (empty until done)
 * @param error    error message (empty unless failed)
 * @param warnings per-chart warnings collected during generation
 */
public record ProgressView(
		int step,
		int total,
		String label,
		String status,
		String slideUrl,
		String error,
		List<String> warnings
) {
	// required
}
