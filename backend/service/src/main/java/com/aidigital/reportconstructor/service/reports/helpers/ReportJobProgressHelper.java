package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;

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
}
