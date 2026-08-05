import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { DateRange } from "../lib/dateRange";

/**
 * GET /api/v1/admin/stats — team-wide statistics for one window of dates.
 *
 * The backend returns 403 for non-admins (allow-list gated); the Dashboard route is additionally
 * hidden/guarded on the client via the /auth/me `is_admin` flag.
 *
 * The previous window stays on screen while the next one loads, so changing dates dims the figures
 * rather than blanking the page and bouncing the layout.
 */
export function useAdminStats(range: DateRange) {
    return useQuery({
        queryKey: ["admin", "stats", range],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/admin/stats", {
                params: { query: { from: range.from, to: range.to } },
            });
            if (error) throw error;
            return data;
        },
        placeholderData: keepPreviousData,
        staleTime: 30_000,
        retry: false,
    });
}
