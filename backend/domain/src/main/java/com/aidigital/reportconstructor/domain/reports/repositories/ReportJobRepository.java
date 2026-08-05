package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link ReportJobEntity}. Public job id = {@link ReportJobEntity#getId()}.
 */
public interface ReportJobRepository extends JpaRepository<ReportJobEntity, Long> {

	/**
	 * Lists a single user's jobs, newest first, for the "My reports" history screen.
	 *
	 * @param ownerUserId internal owner id whose jobs are returned
	 * @return the owner's jobs ordered by creation time descending
	 */
	List<ReportJobEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

	/**
	 * Lists every job, newest first, for team-wide admin aggregation.
	 *
	 * @return all jobs ordered by creation time descending
	 */
	List<ReportJobEntity> findAllByOrderByCreatedAtDesc();

	/**
	 * Counts jobs in any of the given statuses, for the dashboard's live operational counters.
	 *
	 * <p>These counters read the jobs table directly rather than the {@code usage_daily} rollup: a
	 * queued or running job's whole point is that it is happening now, and a rollup refreshed on a
	 * timer would report it minutes late.
	 *
	 * @param statuses status codes to count
	 * @return the number of matching jobs
	 */
	int countByStatusIn(Collection<String> statuses);

	/**
	 * Lists the most recent jobs in any of the given statuses.
	 *
	 * @param statuses status codes to include
	 * @param pageable bound on how many are returned
	 * @return the matching jobs, newest first
	 */
	List<ReportJobEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses, Pageable pageable);

	/**
	 * Lists every job in any of the given statuses, unbounded.
	 *
	 * <p>For the admin action that clears the whole failures list, which must reach every failure
	 * rather than the page of them the dashboard happens to show. Bounded in practice by how many
	 * failures exist, which is the number the caller is asking about.
	 *
	 * @param statuses status codes to include
	 * @return the matching jobs, newest first
	 */
	List<ReportJobEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

	/**
	 * Lists the most recent jobs that completed but recorded generation warnings, i.e. reports that
	 * shipped degraded.
	 *
	 * <p>A clean run stores an empty JSON array rather than null, so null alone does not identify a
	 * degraded report and {@code '[]'} has to be excluded too.
	 *
	 * <p>There is deliberately no {@code <> ''} test beside it. {@code warnings_json} is {@code jsonb},
	 * so every literal it is compared against is parsed as JSON — and an empty string is not valid
	 * JSON, which made the whole query fail on the literal before it ever looked at a row. It could not
	 * have matched anything either: a jsonb column cannot hold an empty string, because Postgres would
	 * have rejected it on write. The comparison against {@code '[]'} is safe for the same reason it is
	 * also exact — jsonb compares by parsed value, so a stored {@code "[ ]"} still matches.
	 *
	 * @param pageable bound on how many are returned
	 * @return the matching jobs, newest first
	 */
	@Query("""
			select j from ReportJobEntity j
			where j.warningsJson is not null
				and j.warningsJson <> '[]'
			order by j.createdAt desc
			""")
	List<ReportJobEntity> findRecentWarned(Pageable pageable);

	/**
	 * Reports when the earliest job was created, so a full rollup rebuild knows how far back to reach.
	 *
	 * @return the earliest creation timestamp, or {@code null} when no job exists
	 */
	@Query("select min(j.createdAt) from ReportJobEntity j")
	OffsetDateTime earliestCreatedAt();

	/**
	 * Lists every distinct report owner with the email last recorded for it.
	 *
	 * <p>One row per user, so the per-user dashboard table can label the rollup's rows — which key on
	 * the internal id — without reading the jobs themselves. {@code max} picks the alphabetically last
	 * non-null email, which for a user whose address never changed is simply their address.
	 *
	 * @return one row per owner
	 */
	@Query("""
			select new com.aidigital.reportconstructor.domain.reports.projections.JobOwner(
				j.ownerUserId, max(j.ownerEmail), max(j.createdAt))
			from ReportJobEntity j
			group by j.ownerUserId
			""")
	List<JobOwner> listOwners();

	/**
	 * Lists deliverable report jobs — everything except a slide-deck flow's intermediate sheet step —
	 * one page at a time, ordered by whatever the caller asked for.
	 *
	 * <p>The page and the order are the database's job, not the server's: the "All reports" table used
	 * to fetch every row and sort the array in the browser, which is fine at a thousand reports and
	 * pointless work at a hundred thousand. Legacy rows with no target are treated as deliverables.
	 *
	 * @param sheetTarget wire code of the intermediate sheet target to exclude
	 * @param pageable    the page and sort order to apply
	 * @return the requested page, with the unfiltered total attached
	 */
	@Query("""
			select j from ReportJobEntity j
			where j.target is null or j.target <> :sheetTarget
			""")
	Page<ReportJobEntity> findDeliverables(@Param("sheetTarget") String sheetTarget, Pageable pageable);
}
