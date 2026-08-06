package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.projections.JobOwner;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persists report-job lifecycle transitions and enforces ownership when loading jobs for progress polling.
 */
public interface ReportJobProgressHelper {

	/**
	 * Creates and saves a new report job in the {@code queued} state with seven build steps.
	 *
	 * @param userId         internal id of the report owner
	 * @param reportTypeCode report type code from the generation request
	 * @return the persisted job ready for async processing
	 */
	ReportJobEntity createQueuedJob(String userId, String reportTypeCode);

	/**
	 * Stamps the owner's email, the generation target, and the connected source-sheet URLs
	 * onto an existing job (owner email feeds the admin per-user breakdown; target lets the
	 * history hide intermediate SHEET-assembly jobs; the source URLs let admins review the
	 * user's inputs). A no-op when the job no longer exists.
	 *
	 * @param jobId        id of the job to stamp
	 * @param ownerEmail   lowercased email of the report owner
	 * @param target       generation target name (e.g. {@code SLIDES_FROM_SHEET})
	 * @param mediaPlanUrl Media Plan source sheet URL the user connected, or {@code null}
	 * @param elevateUrl   Elevate source sheet URL the user connected, or {@code null}
	 */
	void recordJobContext(Long jobId, String ownerEmail, String target, String mediaPlanUrl, String elevateUrl);

	/**
	 * Stamps the generated artifact's file name and the source Google Sheet URL onto a job
	 * as it completes, for the report-history rows. A no-op when the job no longer exists.
	 *
	 * @param jobId        id of the job to stamp
	 * @param artifactName human file name of the generated deck/sheet
	 * @param sheetUrl     source/associated Google Sheet URL, or {@code null} when none
	 */
	void recordArtifact(Long jobId, String artifactName, String sheetUrl);

	/**
	 * Stamps the serialised resume state onto a job as its sheet build completes, so the report can
	 * be finished from a later browser session. A no-op when the job no longer exists or there is
	 * nothing to store.
	 *
	 * @param jobId       id of the job to stamp
	 * @param payloadJson serialised {@code ReportResumeState}, or {@code null}
	 */
	void recordResumeState(Long jobId, String payloadJson);

	/**
	 * Marks a job's resumable draft as dismissed by its owner, hiding it from the history without
	 * deleting the job or the workbook it produced. Idempotent: dismissing an already-dismissed
	 * draft keeps the original timestamp. A no-op when the job no longer exists.
	 *
	 * @param jobId id of the job to dismiss
	 */
	void dismissJob(Long jobId);

	/**
	 * Stamps the run's Claude token consumption onto a job, for the admin token-spend dashboard.
	 * Called once the run finishes, whether it succeeded or failed — tokens spent before a failure
	 * were still billed. A no-op when the job no longer exists or when the run made no Claude calls.
	 *
	 * @param jobId id of the job to stamp
	 * @param usage the run's accumulated token consumption
	 */
	void recordTokenUsage(Long jobId, ClaudeUsage usage);

	/**
	 * Stamps the number of slides a finished deck shipped with, for the admin dashboard's saved-hours
	 * figure. A no-op when the job no longer exists or when the count is not positive — an unmeasured
	 * deck must leave the column null so the savings calculation falls back to the configured
	 * per-report-type default, rather than treating the report as having produced nothing.
	 *
	 * @param jobId      id of the job to stamp
	 * @param slideCount slides in the finished deck, after surplus template slides were deleted
	 */
	void recordSlideCount(Long jobId, int slideCount);

	/**
	 * Lists a single owner's jobs, newest first, for the "My reports" history screen.
	 *
	 * @param userId internal owner id whose jobs are listed
	 * @return the owner's jobs ordered newest first
	 */
	List<ReportJobEntity> listJobsByOwner(String userId);

	/**
	 * Lists every job, newest first, for team-wide admin aggregation.
	 *
	 * <p>Reads the whole table, so it is not a dashboard query: the admin figures are aggregated from
	 * the {@code usage_daily} rollup instead. Kept for the paths that genuinely need every row.
	 *
	 * @return all jobs ordered newest first
	 */
	List<ReportJobEntity> listAllJobs();

	/**
	 * Counts jobs currently queued or running.
	 *
	 * <p>Read live rather than from the rollup: an in-flight job's whole point is that it is happening
	 * now, and a rollup refreshed on a timer would report it minutes late.
	 *
	 * @return the number of jobs in flight
	 */
	int countInFlight();

	/**
	 * Counts jobs that ended in error.
	 *
	 * @return the number of failed jobs
	 */
	int countFailed();

	/**
	 * Lists the most recent job issues — hard failures and reports that shipped with warnings.
	 *
	 * @param limit how many of each kind to return at most
	 * @return the matching jobs, newest first, failures and degraded reports interleaved by date
	 */
	List<ReportJobEntity> listRecentIssues(int limit);

	/**
	 * Lists every job issue — hard failures and reports that shipped with warnings — unbounded.
	 *
	 * <p>For the admin action that clears the whole failures list: it must reach every failure, not
	 * just the page the dashboard shows. Reads only jobs that are actually issues, so it stays bounded
	 * by the thing the caller is asking about rather than by the size of the jobs table.
	 *
	 * @return the matching jobs, newest first
	 */
	List<ReportJobEntity> listAllIssues();

	/**
	 * Reports when the earliest job was created, so a full rollup rebuild knows how far back to reach.
	 *
	 * @return the earliest creation timestamp, or {@code null} when no job exists
	 */
	OffsetDateTime earliestJobCreatedAt();

	/**
	 * Lists every distinct report owner with the email last recorded for it, so the per-user dashboard
	 * table can label rollup rows that key on the internal id alone.
	 *
	 * @return one row per owner
	 */
	List<JobOwner> listOwners();

	/**
	 * Lists one page of deliverable report jobs — everything except a slide-deck flow's intermediate
	 * sheet step — in the order the page request asks for.
	 *
	 * @param pageable the page and sort order to apply
	 * @return the requested page, with the unfiltered total attached
	 */
	Page<ReportJobEntity> listDeliverables(Pageable pageable);

	/**
	 * Moves a job to {@code running} at the given pipeline step and updates its progress label.
	 *
	 * @param jobId id of the job to update
	 * @param step  current 1-based step number (out of 7)
	 * @param label human-readable step description shown in the UI
	 */
	void markJobRunningAtStep(Long jobId, int step, String label);

	/**
	 * Marks a job {@code done}, stores the slide URL and warnings JSON, and sets the final step label.
	 *
	 * @param jobId        id of the job to complete
	 * @param slideUrl     URL of the generated Google Slides deck
	 * @param warningsJson serialised chart warnings, or null when none
	 */
	void markJobDone(Long jobId, String slideUrl, String warningsJson);

	/**
	 * Marks a job {@code error} and records the failure message.
	 *
	 * @param jobId        id of the failed job
	 * @param errorMessage exception message or other failure detail for the UI
	 */
	void markJobFailed(Long jobId, String errorMessage);

	/**
	 * Loads a job by id or throws when it does not exist.
	 *
	 * @param jobId id of the job required for an in-flight pipeline step
	 * @return the persisted job entity
	 */
	ReportJobEntity loadRequiredJob(Long jobId);

	/**
	 * Loads a job only when it exists and belongs to the given owner.
	 *
	 * @param userId internal owner id that must match the stored job
	 * @param jobId  id of the job to load
	 * @return the owned job entity
	 */
	ReportJobEntity loadJobForOwner(String userId, Long jobId);

	/**
	 * Loads a job by id when it exists, without throwing when it does not.
	 *
	 * @param jobId id of the job to load
	 * @return the job, or empty when no such job exists
	 */
	Optional<ReportJobEntity> findJob(Long jobId);

	/**
	 * Deletes a job by id, doing nothing when it does not exist.
	 *
	 * @param jobId id of the job to delete
	 */
	void deleteJob(Long jobId);

	/**
	 * Clears a job's recorded generation warnings, so a report that shipped degraded stops being
	 * flagged as an issue while the report itself is kept. No-op when the job does not exist.
	 *
	 * @param jobId id of the job whose warnings are cleared
	 */
	void clearJobWarnings(Long jobId);
}
