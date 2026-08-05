package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link ClaudeUsageEventService} for tests of collaborators that merely have to record
 * usage without a database, and for asserting on what they recorded.
 */
public class NoOpClaudeUsageEventService implements ClaudeUsageEventService {

	private final List<ClaudeUsageEventEntity> saved = new ArrayList<>();

	@Override
	public void save(ClaudeUsageEventEntity event) {
		saved.add(event);
	}

	@Override
	public List<ClaudeUsageEventEntity> listAll() {
		return List.copyOf(saved);
	}

	@Override
	public List<ClaudeLabelUsage> byLabel(OffsetDateTime from, OffsetDateTime to) {
		// The aggregate queries are the database's job; collaborators under test only ever record.
		return List.of();
	}

	@Override
	public List<ClaudeLabelUsage> unattributed(OffsetDateTime from, OffsetDateTime to) {
		return List.of();
	}

	@Override
	public void deleteByJobId(Long jobId) {
		saved.removeIf(event -> jobId != null && jobId.equals(event.getJobId()));
	}
}
