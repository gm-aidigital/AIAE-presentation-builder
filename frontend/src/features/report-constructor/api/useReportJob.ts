import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { GenerateRequest, ReportJob } from "@/shared/api/types";

/** POST /report-jobs — enqueue the async generation job, returns the job id. */
export function useCreateReportJob() {
    return useMutation<number, Error, GenerateRequest>({
        mutationFn: async (body) => {
            const { data, error } = await apiClient.POST("/api/v1/report-jobs", { body });
            if (error || !data) throw new Error("Failed to start report generation.");
            return data.jobId;
        },
    });
}

/**
 * GET /report-jobs/{jobId} — polls every 1500ms (TanStack Query refetchInterval,
 * not setInterval) until the job is done or errored.
 */
export function useReportJob(jobId: number | null) {
    return useQuery<ReportJob, Error>({
        queryKey: ["report-job", jobId],
        enabled: jobId != null,
        retry: false,
        refetchInterval: (query) => {
            const status = query.state.data?.status;
            return status === "done" || status === "error" ? false : 1500;
        },
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/report-jobs/{jobId}", {
                params: { path: { jobId: jobId as number } },
            });
            if (error || !data) throw new Error("Failed to poll report job.");
            return data;
        },
    });
}
