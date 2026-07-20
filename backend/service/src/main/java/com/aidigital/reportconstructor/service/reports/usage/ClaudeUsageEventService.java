package com.aidigital.reportconstructor.service.reports.usage;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;

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
	 * Lists every recorded call, for the admin spend aggregation.
	 *
	 * @return all usage events
	 */
	List<ClaudeUsageEventEntity> listAll();

	/**
	 * Deletes every usage event recorded for one report job.
	 *
	 * @param jobId the report job whose events are removed
	 */
	void deleteByJobId(Long jobId);
}
