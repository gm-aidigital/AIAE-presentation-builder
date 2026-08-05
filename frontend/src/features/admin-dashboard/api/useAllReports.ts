import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/** Columns the server can order the team-wide report history by. */
export type ReportSortKey = "createdAt" | "tokens" | "slides" | "owner" | "type" | "status";

/** How a page of the report history is being asked for. */
export interface ReportQuery {
    page: number;
    size: number;
    sort: ReportSortKey;
    dir: "asc" | "desc";
}

/**
 * GET /api/v1/admin/reports — one page of every user's report history (admin only).
 *
 * Paged and sorted by the server rather than in the browser: the history grows without bound, and
 * sorting a fetched page would reorder fifty rows out of thousands while looking like it had ordered
 * the whole table.
 *
 * The previous page is kept on screen while the next one loads, so paging and re-sorting do not
 * blank the table between requests.
 */
export function useAllReports(query: ReportQuery, enabled = true) {
    return useQuery({
        queryKey: ["admin", "reports", query],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/admin/reports", {
                params: { query },
            });
            if (error) throw error;
            return data;
        },
        enabled,
        placeholderData: keepPreviousData,
        staleTime: 30_000,
        retry: false,
    });
}
