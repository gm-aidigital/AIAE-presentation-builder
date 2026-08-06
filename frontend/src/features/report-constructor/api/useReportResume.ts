import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/reports/{jobId}/resume — the draft behind a "Continue" click: the generated
 * workbook plus the wizard state the interrupted session was carrying. Everything else the
 * deck needs is read back from the workbook itself, so this is a small object.
 *
 * Not retried and never stale: it is fetched once, seeded into the wizard, and from then on
 * the wizard owns that state — refetching it would fight the user's own edits.
 */
export function useReportResume(jobId: number | null) {
    return useQuery({
        queryKey: ["reports", "resume", jobId],
        enabled: jobId !== null,
        retry: false,
        staleTime: Infinity,
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/reports/{jobId}/resume", {
                params: { path: { jobId: jobId as number } },
            });
            if (error || !data) throw new Error("This draft is no longer available.");
            return data;
        },
    });
}
