import { useQuery } from "@tanstack/react-query";
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
