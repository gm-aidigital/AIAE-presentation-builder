import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { GoogleConnectionStatus } from "@/shared/api/types";

/** GET /google/status — service-account connectivity + mock-mode flag. */
export function useGoogleStatus() {
    return useQuery<GoogleConnectionStatus, Error>({
        queryKey: ["google", "status"],
        retry: false,
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/api/v1/google/status");
            if (error || !data) throw new Error("Failed to read Google status.");
            return data;
        },
    });
}
