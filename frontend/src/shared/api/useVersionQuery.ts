import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/version — the running backend build's git commit. Polled so the header build badge
 * reflects a fresh deploy without requiring a page reload.
 */
export function useVersionQuery() {
    return useQuery({
        queryKey: ["version"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/version");
            if (error) throw error;
            return data;
        },
        staleTime: 60_000,
        refetchInterval: 60_000,
        retry: false,
    });
}
