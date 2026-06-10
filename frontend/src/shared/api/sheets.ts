// Sheet-read helpers shared by the two wizard connect steps. Tab-name literals
// are load-bearing constants (exact spelling/case matters — see fidelity rules).
import { apiClient } from "./client";
import type { SheetReadResult } from "./types";

export const MEDIA_PLAN_PRIMARY_TAB = "Proposal";
export const MEDIA_PLAN_OPTIONAL_TABS = ["Audience&Inventory", "Estimates", "Geo"] as const;
export const ELEVATE_TAB = "Basic";

/** Legacy gate: "Pull data" is enabled only for a Google Sheets URL. */
export function isGoogleSheetUrl(url: string): boolean {
    return /docs\.google\.com\/spreadsheets/.test(url.trim());
}

/**
 * Reads one tab via POST /sheets/read. Throws on transport/HTTP failure; on
 * success returns the dual ok/error contract body — the caller inspects
 * `ok`/`error` (e.g. `tab_not_found`) to drive the Estimates→Proposal fallback.
 */
export async function readSheetTab(url: string, tab: string): Promise<SheetReadResult> {
    const { data, error } = await apiClient.POST("/api/v1/sheets/read", {
        body: { url: url.trim(), tab },
    });
    if (error || !data) {
        // Surface the backend's real message (ApiErrorV1.message) instead of a
        // generic string, so the user sees *why* the read failed (no access,
        // bad link, tab missing, …) rather than "Failed to read tab".
        const backendMsg =
            (error as { message?: string } | undefined)?.message?.trim() ||
            (data as { message?: string } | undefined)?.message?.trim();
        throw new Error(
            backendMsg ||
                `Couldn't read the "${tab}" tab — check the sheet link and that it's shared with the report service account.`
        );
    }
    return data;
}
