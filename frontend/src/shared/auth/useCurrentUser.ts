import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/**
 * GET /api/v1/auth/me — the authenticated user, including the server-computed
 * `is_admin` flag that gates the Dashboard nav item and the /admin surface. This
 * is the ONLY admin signal the client trusts (the backend also enforces it).
 */
export function useCurrentUser() {
    return useQuery({
        queryKey: ["auth", "me"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/auth/me");
            if (error) throw error;
            return data;
        },
        staleTime: 5 * 60_000,
        retry: false,
    });
}
