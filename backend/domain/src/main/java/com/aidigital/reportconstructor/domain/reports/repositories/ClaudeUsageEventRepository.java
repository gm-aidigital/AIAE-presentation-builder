package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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

	/**
	 * Aggregates spend by pipeline stage, usage status and model, in the database.
	 *
	 * <p>This is the projection query the class comment above anticipated. The table grows by a few
	 * dozen rows per report, so reading it whole to add it up in Java stopped being cheap; grouping in
	 * SQL returns a row per stage instead of a row per call, and the all-time figure stays all-time.
	 *
	 * @return one row per (stage, status, model)
	 */
	@Query("""
			select new com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage(
				e.label, e.status, e.model, count(e),
				sum(e.inputTokens), sum(e.outputTokens),
				sum(e.cacheWriteTokens), sum(e.cacheReadTokens))
			from ClaudeUsageEventEntity e
			group by e.label, e.status, e.model
			""")
	List<ClaudeLabelUsage> aggregateByLabel();

	/**
	 * Aggregates the spend of calls that belong to no report job.
	 *
	 * <p>The line-item match runs inside a web request rather than a report run, so its calls are
	 * billed but never stamped onto a job. They are therefore absent from the {@code usage_daily}
	 * rollup, and without this query the dashboard's headline total would quietly omit them.
	 *
	 * @return one row per (stage, status, model) among calls with no job
	 */
	@Query("""
			select new com.aidigital.reportconstructor.domain.reports.projections.ClaudeLabelUsage(
				e.label, e.status, e.model, count(e),
				sum(e.inputTokens), sum(e.outputTokens),
				sum(e.cacheWriteTokens), sum(e.cacheReadTokens))
			from ClaudeUsageEventEntity e
			where e.jobId is null
			group by e.label, e.status, e.model
			""")
	List<ClaudeLabelUsage> aggregateUnattributed();
}
