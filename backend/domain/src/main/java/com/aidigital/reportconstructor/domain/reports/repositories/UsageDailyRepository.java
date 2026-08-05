package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.UsageDailyEntity;
import com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring Data repository for the {@code usage_daily} rollup.
 *
 * <p>Every read here returns an aggregate, never entities: the point of the rollup is that the admin
 * dashboard stops pulling rows into memory to add them up. The reads are JPQL constructor
 * expressions rather than native SQL so Hibernate validates them at startup — a mistyped column
 * fails the build instead of the dashboard.
 *
 * <p>The rebuild is the exception and is deliberately native: it is a set-based
 * {@code INSERT … SELECT … GROUP BY} over {@code report_jobs} that never materialises a job in the
 * JVM, and it uses PostgreSQL's {@code FILTER} aggregates, which JPQL cannot express.
 */
public interface UsageDailyRepository extends JpaRepository<UsageDailyEntity, Long> {

	/**
	 * Drops every rollup row in a day window, so the window can be rebuilt from scratch.
	 *
	 * <p>Rebuilding is delete-then-insert rather than an upsert because jobs can disappear: an admin
	 * clearing failures deletes job rows, and an upsert would leave their contribution behind forever
	 * as a row that no longer has anything to recompute it.
	 *
	 * @param from first day to clear, inclusive
	 * @param to   day to stop at, exclusive
	 * @return the number of rollup rows dropped
	 */
	@Modifying
	@Query("delete from UsageDailyEntity u where u.day >= :from and u.day < :to")
	int deleteWindow(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/**
	 * Rebuilds a day window of the rollup straight from {@code report_jobs}, in the database.
	 *
	 * <p>The day a job belongs to is its {@code created_at} rendered in the database session's time
	 * zone, so the dashboard's days line up with the server's calendar rather than with UTC.
	 * Generation seconds are floored at zero: a clock adjustment between a job's creation and its
	 * last update must not subtract time from the automation's measured cost.
	 *
	 * @param from first day to rebuild, inclusive
	 * @param to   day to stop at, exclusive
	 * @return the number of rollup rows written
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO usage_daily (
				day, owner_user_id, report_type_code, target, claude_model,
				jobs, jobs_with_usage, failed_jobs,
				claude_calls, input_tokens, output_tokens, cache_write_tokens, cache_read_tokens,
				slides, jobs_with_slides, generation_seconds, refreshed_at)
			SELECT
				CAST(j.created_at AS date),
				j.owner_user_id,
				COALESCE(NULLIF(UPPER(TRIM(j.report_type_code)), ''), 'OTHER'),
				COALESCE(NULLIF(TRIM(j.target), ''), 'UNKNOWN'),
				COALESCE(NULLIF(TRIM(j.claude_model), ''), 'UNKNOWN'),
				COUNT(*),
				COUNT(*) FILTER (WHERE COALESCE(j.claude_calls, 0) > 0),
				COUNT(*) FILTER (WHERE j.status = 'error'),
				COALESCE(SUM(j.claude_calls), 0),
				COALESCE(SUM(j.input_tokens), 0),
				COALESCE(SUM(j.output_tokens), 0),
				COALESCE(SUM(j.cache_write_tokens), 0),
				COALESCE(SUM(j.cache_read_tokens), 0),
				COALESCE(SUM(j.slide_count), 0),
				COUNT(*) FILTER (WHERE COALESCE(j.slide_count, 0) > 0),
				COALESCE(SUM(GREATEST(EXTRACT(EPOCH FROM (j.updated_at - j.created_at)), 0)), 0),
				now()
			FROM report_jobs j
			WHERE CAST(j.created_at AS date) >= :from AND CAST(j.created_at AS date) < :to
			GROUP BY 1, 2, 3, 4, 5
			""")
	int rebuildWindow(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/**
	 * Sums the rollup by day, keeping the report type, target and model dimensions so the read side
	 * can price the tokens and decide which jobs count as reports.
	 *
	 * <p>This is what every trend series is built from: weeks and months are folded out of these
	 * daily rows in Java rather than by a second database round trip, because the rollup has already
	 * reduced the volume to a handful of rows per day.
	 *
	 * @param from first day to include, inclusive
	 * @return one row per (day, report type, target, model), oldest first
	 */
	@Query("""
			select new com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket(
				u.day, u.reportTypeCode, u.target, u.claudeModel,
				sum(u.jobs), sum(u.jobsWithUsage), sum(u.failedJobs),
				sum(u.claudeCalls), sum(u.inputTokens), sum(u.outputTokens),
				sum(u.cacheWriteTokens), sum(u.cacheReadTokens),
				sum(u.slides), sum(u.jobsWithSlides), sum(u.generationSeconds))
			from UsageDailyEntity u
			where u.day >= :from
			group by u.day, u.reportTypeCode, u.target, u.claudeModel
			order by u.day asc
			""")
	List<UsageDailyBucket> aggregateByDay(@Param("from") LocalDate from);

	/**
	 * Sums the rollup by user, keeping the same read-side dimensions as {@link #aggregateByDay}.
	 *
	 * <p>The current-month slice is a conditional sum inside the same pass rather than a second query,
	 * so the "this month" and all-time columns of a row are read from one consistent snapshot.
	 *
	 * @param from       first day to include, inclusive
	 * @param monthStart first day of the current calendar month
	 * @return one row per (user, report type, target, model)
	 */
	@Query("""
			select new com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow(
				u.ownerUserId, u.reportTypeCode, u.target, u.claudeModel,
				sum(u.jobs),
				sum(case when u.day >= :monthStart then u.jobs else 0 end),
				sum(u.jobsWithUsage),
				sum(u.claudeCalls), sum(u.inputTokens), sum(u.outputTokens),
				sum(u.cacheWriteTokens), sum(u.cacheReadTokens),
				sum(u.slides), sum(u.jobsWithSlides), sum(u.generationSeconds))
			from UsageDailyEntity u
			where u.day >= :from
			group by u.ownerUserId, u.reportTypeCode, u.target, u.claudeModel
			""")
	List<UsageDailyUserRow> aggregateByUser(
			@Param("from") LocalDate from, @Param("monthStart") LocalDate monthStart);

	/**
	 * Lists the distinct (day, user) pairs in a window, from which active users are counted for any
	 * bucket granularity.
	 *
	 * @param from first day to include, inclusive
	 * @return one pair per day a user was active on, oldest first
	 */
	@Query("""
			select distinct new com.aidigital.reportconstructor.domain.reports.projections.UsageActiveDay(
				u.day, u.ownerUserId)
			from UsageDailyEntity u
			where u.day >= :from
			order by u.day asc
			""")
	List<UsageActiveDay> activeDays(@Param("from") LocalDate from);

	/**
	 * Reports when the rollup was last rebuilt, so a stale dashboard can say so rather than present
	 * old numbers as current.
	 *
	 * @return the most recent refresh timestamp, or {@code null} when the rollup is empty
	 */
	@Query("select max(u.refreshedAt) from UsageDailyEntity u")
	OffsetDateTime lastRefreshedAt();

	/**
	 * Reports the earliest day the rollup covers, used to decide how far a full rebuild must reach.
	 *
	 * @return the earliest rolled-up day, or {@code null} when the rollup is empty
	 */
	@Query("select min(u.day) from UsageDailyEntity u")
	LocalDate earliestDay();
}
