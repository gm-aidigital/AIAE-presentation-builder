package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;

import java.util.List;

/**
 * Entity service for {@code claude_usage_events} — the only door to its repository.
 */
public interface ClaudeUsageEventService {

	/**
	 * Persists one Claude call's token consumption.
	 *
	 * @param event the call to record
	 */
	void save(ClaudeUsageEventEntity event);

	/**
	 * Lists every recorded call.
	 *
	 * <p>Reads the whole table, so it is not a dashboard query — the per-stage breakdown uses
	 * {@link #byLabel()} instead. Kept for callers that genuinely need every event.
	 *
	 * @return all usage events
	 */
	List<ClaudeUsageEventEntity> listAll();

	/**
	 * Aggregates spend by pipeline stage, usage status and model, in the database.
	 *
	 * @return one row per (stage, status, model)
	 */
	List<ClaudeLabelUsage> byLabel();

	/**
	 * Aggregates the spend of calls that belong to no report job, which the per-job rollup cannot see.
	 *
	 * @return one row per (stage, status, model) among calls with no job
	 */
	List<ClaudeLabelUsage> unattributed();

	/**
	 * Deletes every usage event recorded for one report job.
	 *
	 * @param jobId the report job whose events are removed
	 */
	void deleteByJobId(Long jobId);
}
