import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Example mutation hook — after a real POST/PATCH/DELETE, invalidate the queries
 * that read the changed data. Copy to features/<name>/api/useXxxMutation.ts.
 */
export function useRefreshSessionMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async () => {
            await queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
        },
    });
}
