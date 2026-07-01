import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { DateRangeResult, Rows2D } from "@/shared/api/types";

/**
 * POST /placeholders/date-range — detect the raw-data ("Basic" tab) delivery
 * date range so the UI can show the flight window for the user to confirm or
 * correct before generation. No Drive writes.
 */
export function useDetectDateRange() {
    return useMutation<DateRangeResult, Error, Rows2D>({
        mutationFn: async (adjRows) => {
            const { data, error } = await apiClient.POST("/api/v1/placeholders/date-range", {
                body: { adjRows },
            });
            if (error || !data) throw new Error("Could not detect the raw-data date range.");
            return data;
        },
    });
}
