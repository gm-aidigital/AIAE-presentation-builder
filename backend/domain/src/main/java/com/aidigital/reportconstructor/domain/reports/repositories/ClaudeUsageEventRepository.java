package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ClaudeUsageEventEntity}.
 *
 * <p>The admin aggregation reads the whole table so its headline figure is a true all-time total
 * rather than a window. The table grows by one row per Claude call — a few dozen per report — so
 * that stays cheap at this deployment's volume; if it stops being cheap, the fix is a projection
 * query here, not a silently narrowed date range.
 */
public interface ClaudeUsageEventRepository extends JpaRepository<ClaudeUsageEventEntity, Long> {

	/**
	 * Deletes every usage event recorded for one report job, so an admin who clears a failed job
	 * removes its spend rows along with it rather than leaving orphaned events behind.
	 *
	 * @param jobId the report job whose events are removed
	 * @return the number of events deleted
	 */
	long deleteByJobId(Long jobId);
}
