package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import com.aidigital.reportconstructor.domain.reports.repositories.ClaudeUsageEventRepository;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring bean implementation of {@link ClaudeUsageEventService}.
 */
@Component
@RequiredArgsConstructor
public class ClaudeUsageEventServiceImpl implements ClaudeUsageEventService {

	private final ClaudeUsageEventRepository events;

	@Transactional
	@Override
	public void save(ClaudeUsageEventEntity event) {
		events.save(event);
	}

	@Transactional(readOnly = true)
	@Override
	public List<ClaudeUsageEventEntity> listAll() {
		return events.findAll();
	}

	@Transactional(readOnly = true)
	@Override
	public List<ClaudeLabelUsage> byLabel(OffsetDateTime from, OffsetDateTime to) {
		return events.aggregateByLabel(from, to);
	}

	@Transactional(readOnly = true)
	@Override
	public List<ClaudeLabelUsage> unattributed(OffsetDateTime from, OffsetDateTime to) {
		return events.aggregateUnattributed(from, to);
	}

	@Transactional
	@Override
	public void deleteByJobId(Long jobId) {
		events.deleteByJobId(jobId);
	}
}
