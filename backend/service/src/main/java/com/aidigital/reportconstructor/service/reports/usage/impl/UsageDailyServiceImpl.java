package com.aidigital.reportconstructor.service.reports.usage.impl;

import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.domain.reports.repositories.UsageDailyRepository;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.UsageDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring bean implementation of {@link UsageDailyService}.
 *
 * <p>The earliest job date a full rebuild needs comes from the report-job entity service rather than
 * from a second repository injected here, so the rollup stays the only table this class reaches into
 * directly.
 */
@Component
@RequiredArgsConstructor
public class UsageDailyServiceImpl implements UsageDailyService {

	private final UsageDailyRepository rollup;
	private final ReportJobProgressHelper jobs;

	@Transactional
	@Override
	public int rebuild(LocalDate from, LocalDate to) {
		if (from == null || to == null || !from.isBefore(to)) {
			return 0;
		}
		rollup.deleteWindow(from, to);
		return rollup.rebuildWindow(from, to);
	}

	@Transactional
	@Override
	public int rebuildAll() {
		OffsetDateTime earliest = jobs.earliestJobCreatedAt();
		if (earliest == null) {
			return 0;
		}
		// Exclusive upper bound one day past today, so today's own jobs are included.
		return rebuild(earliest.toLocalDate(), LocalDate.now().plusDays(1));
	}

	@Transactional(readOnly = true)
	@Override
	public List<UsageDailyBucket> byDay(LocalDate from, LocalDate to) {
		return rollup.aggregateByDay(from, to);
	}

	@Transactional(readOnly = true)
	@Override
	public List<UsageDailyUserRow> byUser(LocalDate from, LocalDate to) {
		return rollup.aggregateByUser(from, to);
	}

	@Transactional(readOnly = true)
	@Override
	public List<UsageActiveDay> activeDays(LocalDate from, LocalDate to) {
		return rollup.activeDays(from, to);
	}

	@Transactional(readOnly = true)
	@Override
	public OffsetDateTime lastRefreshedAt() {
		return rollup.lastRefreshedAt();
	}
}
