import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { LineItemMatchResult, Rows2D } from "@/shared/api/types";

/** POST /line-items/match — deterministic rules engine (no LLM). */
export function useMatchLineItems() {
    return useMutation<LineItemMatchResult, Error, { bqRows: Rows2D; planRows: Rows2D }>({
        mutationFn: async ({ bqRows, planRows }) => {
            const { data, error } = await apiClient.POST("/api/v1/line-items/match", {
                body: { bqRows, planRows },
            });
            if (error || !data) throw new Error("Line-item matching failed.");
            return data;
        },
    });
}
