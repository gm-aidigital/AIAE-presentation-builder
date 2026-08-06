import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/reports — the signed-in user's own report history for the
 * "My reports" screen. Never returns another user's reports (owner-scoped
 * server-side).
 */
export function useMyReports() {
    return useQuery({
        queryKey: ["reports", "mine"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/reports");
            if (error) throw error;
            return data;
        },
        staleTime: 30_000,
    });
}

/**
 * POST /api/v1/reports/{jobId}/dismiss — stops offering a draft in the history.
 * Neither the job nor the generated Google Sheet is deleted, so the list is
 * refetched rather than patched: the row simply stops being returned.
 */
export function useDismissReport() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (jobId: number) => {
            const { error } = await apiClient.POST("/api/v1/reports/{jobId}/dismiss", {
                params: { path: { jobId } },
            });
            if (error) throw error;
        },
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reports", "mine"] }),
    });
}
