import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";
import type { ReportType } from "@/shared/api/types";

/**
 * POST /api/v1/reports/adopt-sheet — registers a workbook the user filled themselves as a
 * resumable draft, so the constructor can jump straight to the review step.
 *
 * The response carries the new draft, but the caller navigates to `?resume=<jobId>` rather than
 * seeding from it directly: that URL is what makes the adopted sheet survive a reload, which is
 * the whole point of the draft.
 */
export function useAdoptSheet() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (body: { sheetUrl: string; reportType: ReportType }) => {
            const { data, error } = await apiClient.POST("/api/v1/reports/adopt-sheet", { body });
            if (error || !data) {
                // The server's message names what is actually wrong with the sheet ("no tactics
                // found", "could not read"), which is far more useful than a generic failure.
                throw new Error(
                    error?.message ||
                        "Could not use that sheet — check the link and that it follows the report template."
                );
            }
            return data;
        },
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reports", "mine"] }),
    });
}
