import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/admin/reports — every user's report history (admin only). Same shape
 * as My reports but across all users, with the owner on each row.
 */
export function useAllReports(enabled = true) {
    return useQuery({
        queryKey: ["admin", "reports"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/admin/reports");
            if (error) throw error;
            return data;
        },
        enabled,
        staleTime: 30_000,
        retry: false,
    });
}
