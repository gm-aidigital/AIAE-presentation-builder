import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

const STATS_KEY = ["admin", "stats"];

/** DELETE /api/v1/admin/failures/{jobId} — clear one failure/warning; refreshes the stats. */
export function useResolveFailure() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: async (jobId: number) => {
            const { error } = await apiClient.DELETE("/api/v1/admin/failures/{jobId}", {
                params: { path: { jobId } },
            });
            if (error) throw error;
        },
        onSuccess: () => {
            void qc.invalidateQueries({ queryKey: STATS_KEY });
        },
    });
}

/** DELETE /api/v1/admin/failures — clear every failure/warning at once; refreshes the stats. */
export function useClearFailures() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: async () => {
            const { error } = await apiClient.DELETE("/api/v1/admin/failures", {});
            if (error) throw error;
        },
        onSuccess: () => {
            void qc.invalidateQueries({ queryKey: STATS_KEY });
        },
    });
}
