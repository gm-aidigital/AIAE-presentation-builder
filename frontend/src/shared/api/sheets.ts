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
        throw new Error(`Failed to read tab "${tab}".`);
    }
    return data;
}
