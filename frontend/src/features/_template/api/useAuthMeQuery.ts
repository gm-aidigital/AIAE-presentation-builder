import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

/** Example query hook — copy to features/<name>/api/useXxxQuery.ts */
export function useAuthMeQuery() {
    return useQuery({
        queryKey: ["auth", "me"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/auth/me");
            if (error) throw error;
            return data;
        },
        retry: false,
    });
}
