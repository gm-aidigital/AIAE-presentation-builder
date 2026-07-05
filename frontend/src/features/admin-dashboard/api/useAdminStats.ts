import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/admin/stats — team-wide report statistics and technical health.
 * The backend returns 403 for non-admins (allow-list gated); the Dashboard route
 * is additionally hidden/guarded on the client via the /auth/me `is_admin` flag.
 */
export function useAdminStats() {
    return useQuery({
        queryKey: ["admin", "stats"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/admin/stats");
            if (error) throw error;
            return data;
        },
        staleTime: 30_000,
        retry: false,
    });
}
