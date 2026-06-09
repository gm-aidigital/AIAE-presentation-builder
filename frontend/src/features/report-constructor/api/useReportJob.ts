import { apiClient } from "@/shared/api/client";
import type { GenerateRequest, ReportJob } from "@/shared/api/types";

/** POST /report-jobs — enqueue the async job; returns the new job id. */
export async function startReportJob(body: GenerateRequest): Promise<number> {
    const { data, error } = await apiClient.POST("/api/v1/report-jobs", { body });
    if (error || !data) throw new Error("Failed to start report generation.");
    return data.jobId;
}

/**
 * GET /report-jobs/{jobId} — current progress. Returns null on a transient miss
 * (network blip / job row not yet visible) so the caller's poll loop keeps going
 * instead of aborting — this is what keeps the generating overlay stable.
 */
export async function fetchReportJob(jobId: number): Promise<ReportJob | null> {
    const { data } = await apiClient.GET("/api/v1/report-jobs/{jobId}", {
        params: { path: { jobId } },
    });
    return data ?? null;
}
