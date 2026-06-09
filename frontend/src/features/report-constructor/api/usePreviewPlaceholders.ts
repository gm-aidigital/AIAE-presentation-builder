import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { PreviewRequest, PreviewResult } from "@/shared/api/types";

/** POST /placeholders/preview — dry-run placeholder map (no Drive writes). */
export function usePreviewPlaceholders() {
    return useMutation<PreviewResult, Error, PreviewRequest>({
        mutationFn: async (body) => {
            const { data, error } = await apiClient.POST("/api/v1/placeholders/preview", {
                body,
            });
            if (error || !data) throw new Error("Placeholder preview failed.");
            return data;
        },
    });
}
