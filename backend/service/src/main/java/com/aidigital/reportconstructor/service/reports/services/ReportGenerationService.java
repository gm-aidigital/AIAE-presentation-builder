package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;

/**
 * Business orchestration for marketing report generation: enqueues report jobs, runs the
 * asynchronous build pipeline (campaign data, Claude copy batches, Google Slides deck and
 * charts), and exposes per-job progress for the UI.
 */
public interface ReportGenerationService {

    /**
     * Validates the brief, enqueues a new report job, and kicks off the async build,
     * returning the freshly persisted job so the caller can poll its progress.
     *
     * @param userId      internal id of the report owner, used for job ownership and later access checks
     * @param clerkUserId Clerk identity used to look up the user's Google OAuth token for Slides/Drive access
     * @param payload     full generation request (brief, report type, sheet rows, mappings, geo rows, etc.)
     * @return the persisted, queued {@link ReportJobEntity} whose build runs asynchronously
     */
    ReportJobEntity start(String userId, String clerkUserId, GeneratePayload payload);

    /**
     * Creates and persists a new report job in the {@code queued} state with a total of 7
     * build steps, stamping owner, report type, and creation/update timestamps.
     *
     * @param userId  internal id of the owner stored on the job for later access control
     * @param payload generation request, used here only for its report-type code
     * @return the saved {@link ReportJobEntity} with a generated id, ready to be processed
     */
    ReportJobEntity enqueue(String userId, GeneratePayload payload);

    /**
     * Executes the full async report pipeline for a queued job: collects campaign data,
     * runs the three Claude copy batches (strategic, tactical, executive) plus the geo
     * summary when needed, builds the Slides deck, trims unused tactic placeholders,
     * renders charts, and finally marks the job {@code done} (or {@code error} on failure).
     *
     * @param jobId       id of the previously enqueued {@link ReportJobEntity} to build and update
     * @param payload     generation request driving data collection, Claude prompts, and chart inputs
     * @param clerkUserId Clerk identity used to fetch the Google access token for deck/chart creation
     */
    void run(Long jobId, GeneratePayload payload, String clerkUserId);

    /**
     * Returns the current progress snapshot for a job owned by the given user, throwing
     * if the job is unknown or not owned by that user; null string fields are normalised
     * to empty strings and warnings are parsed from the stored JSON.
     *
     * @param userId internal id that must match the job's owner for access to be granted
     * @param jobId  id of the {@link ReportJobEntity} to report progress for
     * @return a {@link ProgressView} with step/total, label, status, slide URL, error message, and warnings
     */
    ProgressView progress(String userId, Long jobId);
}
