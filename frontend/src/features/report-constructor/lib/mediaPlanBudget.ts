// Pure display helpers for the Line Item Matching modal. They pull the pieces
// that actually distinguish otherwise-identical rows:
//   • the media-plan budget/volume for each tactic (left panel), and
//   • the distinguishing "tail" of a BigQuery Level-1 naming string (right panel).
// No I/O — string + grid logic only, so they stay unit-testable.
import type { Rows2D } from "@/shared/api/types";
import { parseSheetDate } from "./sheetDates";

export interface TacticBudget {
    /** Planned units (impressions or clicks) as parsed from the plan. */
    units: number;
    /** Unit price (CPM/CPC/CPV rate) as parsed from the plan. */
    unitPrice: number;
    /** Rate type label from the plan ("CPM", "CPC", …); "" when absent. */
    rateType: string;
    /** Derived spend: CPM divides units by 1000, everything else is units × price. */
    amount: number;
    /** Flight Start from the plan as ISO yyyy-MM-dd; null when absent or unparseable. */
    flightStart: string | null;
    /** Flight End from the plan as ISO yyyy-MM-dd; null when absent or unparseable. */
    flightEnd: string | null;
}

function normName(value: string): string {
    return value.trim().toLowerCase().replace(/\s+/g, " ");
}

/** Parses a numeric cell, tolerating "$", thousands separators and stray spaces. */
export function parseNumber(cell: string | undefined): number {
    if (!cell) return 0;
    const cleaned = cell.replace(/[^0-9.-]/g, "");
    const n = Number.parseFloat(cleaned);
    return Number.isFinite(n) ? n : 0;
}

// Header synonyms that mark the budget/volume column sitting next to the "Media"
// tactic column — one must be present for a tab to parse as a media plan. Mirrors
// the backend's media-plan detection (CampaignDataCollector: a header row with a
// "media" cell plus a cost/budget or impressions column).
const BUDGET_HEADER_MARKERS = [
    "total cost",
    "cost",
    "budget",
    "impressions",
    "imps",
    "units",
    "unit price",
    "rate type",
];

/**
 * Lightweight sanity check that a manually-picked tab actually holds the media
 * plan: it must contain a header row with a "Media" cell (the tactic column) and,
 * in that same row, at least one budget/volume column. Kept deliberately loose —
 * it only guarantees the downstream tactic extraction has the columns it needs.
 */
export function looksLikeMediaPlan(rows: Rows2D | null): boolean {
    if (!rows) return false;
    for (const row of rows) {
        const cells = (row ?? []).map((c) => normName(c ?? ""));
        if (!cells.includes("media")) continue;
        if (cells.some((c) => BUDGET_HEADER_MARKERS.some((m) => c === m || c.startsWith(m)))) {
            return true;
        }
    }
    return false;
}

/** CPM bills per thousand units; CPC/CPV/flat bill per unit. */
export function deriveAmount(units: number, unitPrice: number, rateType: string): number {
    if (rateType.trim().toUpperCase() === "CPM") return (units / 1000) * unitPrice;
    return units * unitPrice;
}

/**
 * Normalizes the media plan's free-text "Rate Type" cell to one of the EOM rate types
 * (CPM/CPC/CPV), or undefined when it's blank or doesn't match one — the matching-time
 * dropdown pre-fills from this but never guesses a value the sheet didn't actually carry.
 */
export function normalizeRateType(rateType: string | undefined): "CPM" | "CPC" | "CPV" | undefined {
    const upper = (rateType ?? "").trim().toUpperCase();
    return upper === "CPM" || upper === "CPC" || upper === "CPV" ? upper : undefined;
}

// Header spellings that carry the negotiated rate. "rate type" is deliberately absent —
// it names the buy type, not the price.
const PRICE_HEADERS = new Set(["unit price", "unit cost", "unit rate", "rate", "net rate", "cpm", "cpm rate"]);

function headerIndex(header: string[], predicate: (h: string) => boolean): number {
    return header.findIndex((h) => predicate(normName(h ?? "")));
}

/**
 * Aligns a media-plan budget to each tactic in {@code tacticNames} order.
 *
 * Mirrors the backend's tactic extraction: it finds the "Media" header cell,
 * then walks the rows beneath it and matches each tactic name sequentially, so
 * duplicated tactic labels ("Programmatic Display" twice) still map to their own
 * row. Section-label and added-value rows never match a tactic name and are
 * skipped. Returns a parallel array; an entry is null when no confident row was
 * found (fail-safe: never show a mismatched budget).
 */
export function extractTacticBudgets(rows: Rows2D | null, tacticNames: string[]): (TacticBudget | null)[] {
    const out: (TacticBudget | null)[] = tacticNames.map(() => null);
    if (!rows || rows.length === 0) return out;

    let mediaRow = -1;
    let mediaCol = -1;
    for (let i = 0; i < rows.length && mediaRow < 0; i++) {
        const row = rows[i] ?? [];
        for (let j = 0; j < row.length; j++) {
            if (normName(row[j] ?? "") === "media") {
                mediaRow = i;
                mediaCol = j;
                break;
            }
        }
    }
    if (mediaRow < 0) return out;

    const header = rows[mediaRow] ?? [];
    const rateCol = headerIndex(header, (h) => h === "rate type");
    const unitsCol = headerIndex(header, (h) => h.startsWith("units"));
    const priceCol = headerIndex(header, (h) => PRICE_HEADERS.has(h) || h.startsWith("unit price"));
    const startCol = headerIndex(header, (h) => h.startsWith("flight start") || h === "start date" || h === "start");
    const endCol = headerIndex(header, (h) => h.startsWith("flight end") || h === "end date" || h === "end");

    // Collect candidate tactic rows (non-empty Media cell) in document order. Flight dates carry
    // down: plans often write them once and leave the cells below blank for the same flight.
    const candidates: { name: string; budget: TacticBudget }[] = [];
    let lastStart: string | null = null;
    let lastEnd: string | null = null;
    for (let i = mediaRow + 1; i < rows.length; i++) {
        const row = rows[i] ?? [];
        const start = parseSheetDate(row[startCol]);
        const end = parseSheetDate(row[endCol]);
        if (start) lastStart = start;
        if (end) lastEnd = end;
        const media = (row[mediaCol] ?? "").trim();
        if (!media) continue;
        const units = parseNumber(row[unitsCol]);
        const unitPrice = parseNumber(row[priceCol]);
        const rateType = (row[rateCol] ?? "").trim();
        candidates.push({
            name: media,
            budget: {
                units,
                unitPrice,
                rateType,
                amount: deriveAmount(units, unitPrice, rateType),
                flightStart: start ?? lastStart,
                flightEnd: end ?? lastEnd,
            },
        });
    }

    // Sequential name match: advance a pointer so repeated names line up in order.
    let p = 0;
    for (let k = 0; k < tacticNames.length; k++) {
        const target = normName(tacticNames[k] ?? "");
        for (let c = p; c < candidates.length; c++) {
            const cand = normName(candidates[c].name);
            if (cand === target || cand.startsWith(target) || target.startsWith(cand)) {
                out[k] = candidates[c].budget;
                p = c + 1;
                break;
            }
        }
    }
    return out;
}

/**
 * Returns the part of a Level-1 naming string that distinguishes line items
 * sharing a channel — everything after the numeric id token. E.g.
 * "…_Display_Prospecting_CPM_-_616641_Contextual" → "Contextual". Falls back to
 * the last non-id segment, then to a trimmed tail, so it never returns "".
 */
export function namingTail(naming: string, id: string): string {
    if (!naming) return "";
    const clean = (s: string) => s.replace(/[_\-]+/g, " ").replace(/\s+/g, " ").trim();

    if (id) {
        const marker = `_${id}_`;
        const at = naming.indexOf(marker);
        if (at >= 0) {
            const tail = clean(naming.slice(at + marker.length));
            if (tail) return tail;
        }
    }
    const parts = naming.split("_").map((p) => p.trim()).filter(Boolean);
    for (let i = parts.length - 1; i >= 0; i--) {
        if (parts[i] !== "-" && !/^\d+$/.test(parts[i])) return clean(parts[i]);
    }
    return clean(naming);
}
