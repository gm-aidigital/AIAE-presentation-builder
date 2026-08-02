// "Evenly paced" budgets for the EOM Pacing & rates step. Instead of the user typing
// what each tactic should spend this month, the figure is derived: take the tactic's
// full-flight plan spend, spread it evenly over the days it is actually live (first day
// with delivery in the raw data → Flight End in the media plan), then bill the reporting
// month for the days it covers.
//
//   plan $10,000, media plan says 1 Apr–31 Jul, first BigQuery row 3 Apr
//   → live 3 Apr–31 Jul = 120 days → $83.33/day
//   → June (30 days) = $2,499.90
//
// The real live window is used on purpose: a tactic that launched two days late must
// still spend its whole plan, so its daily rate is higher than the media plan implies.
// Pure functions over the grids already in the wizard — no I/O.
import type { Rows2D } from "@/shared/api/types";
import { daysInclusive, overlapDays, parseSheetDate } from "./sheetDates";

export interface EvenPacing {
    /** First day the line item delivered, ISO. */
    firstDate: string;
    /** Media-plan Flight End for the tactic, ISO. */
    flightEnd: string;
    /** Days from first delivery to flight end, both ends counted. */
    flightDays: number;
    /** Days of the reporting window that fall inside the live flight. */
    windowDays: number;
    /** Plan spend divided by flightDays. */
    daily: number;
    /** daily × windowDays — the monthly budget written into the pacing row. */
    budget: number;
}

export interface EvenPacingInput {
    /** Full-flight plan spend for the tactic (media plan units × unit price). */
    spendPlan: number;
    /** First delivery date from the raw data, ISO; null when the line item has no rows. */
    firstDate: string | null;
    /** Flight End from the media plan, ISO; null when the plan has no end date. */
    flightEnd: string | null;
    /** Reporting window (the confirmed flight dates on step 2), ISO. */
    windowStart: string;
    windowEnd: string;
}

/**
 * Spreads a tactic's plan spend evenly across its live days and returns the share that
 * falls in the reporting window. Returns null when any input is missing or the tactic did
 * not run in the window at all — the caller then leaves that row for manual entry rather
 * than writing a made-up number.
 */
export function evenPacedBudget(input: EvenPacingInput): EvenPacing | null {
    const { spendPlan, firstDate, flightEnd, windowStart, windowEnd } = input;
    if (!(spendPlan > 0) || !firstDate || !flightEnd || !windowStart || !windowEnd) return null;

    const flightDays = daysInclusive(firstDate, flightEnd);
    if (flightDays <= 0) return null;

    const windowDays = overlapDays(firstDate, flightEnd, windowStart, windowEnd);
    if (windowDays <= 0) return null;

    const daily = spendPlan / flightDays;
    return {
        firstDate,
        flightEnd,
        flightDays,
        windowDays,
        daily,
        budget: Math.round(daily * windowDays * 100) / 100,
    };
}

const DATE_HEADERS = new Set(["date"]);
const LINE_ITEM_HEADERS = new Set(["line item id", "line_item_id", "lineitemid", "line item"]);
/** Position of the line-item id inside an underscore-split Level 1 naming string. */
const NAMING_ID_PART = 8;

function norm(cell: string | undefined): string {
    return (cell ?? "").trim().toLowerCase();
}

function num(cell: string | undefined): number {
    const n = Number.parseFloat((cell ?? "").replace(/[^0-9.-]/g, ""));
    return Number.isFinite(n) ? n : 0;
}

/**
 * The first day each line item actually delivered, keyed by line-item id.
 *
 * Mirrors the backend collector's read of the raw-data ("Basic") grid: the header row is
 * the one carrying Date + Channel + Cost + Impressions, the id comes from a Line Item ID
 * column when present and otherwise from the ninth underscore-part of the Level 1 naming
 * string. Only rows with spend or impressions count — a zero row on launch day would
 * otherwise hide a late start, which is exactly what the even pacing has to catch.
 */
export function firstDeliveryDateByLineItem(adjRows: Rows2D | null): Record<string, string> {
    const out: Record<string, string> = {};
    if (!adjRows || adjRows.length === 0) return out;

    let headerIdx = -1;
    let dateCol = -1;
    let costCol = -1;
    let impsCol = -1;
    let liCol = -1;
    let namingCol = -1;
    for (let i = 0; i < adjRows.length && headerIdx < 0; i++) {
        const row = adjRows[i] ?? [];
        let date = -1;
        let channel = -1;
        let cost = -1;
        let imps = -1;
        let li = -1;
        let naming = -1;
        for (let j = 0; j < row.length; j++) {
            const v = norm(row[j]);
            if (DATE_HEADERS.has(v)) date = j;
            if (v === "channel") channel = j;
            if (v === "cost") cost = j;
            if (v === "impressions") imps = j;
            if (LINE_ITEM_HEADERS.has(v)) li = j;
            if (v.includes("level 1 naming")) naming = j;
        }
        if (date >= 0 && channel >= 0 && cost >= 0 && imps >= 0) {
            headerIdx = i;
            dateCol = date;
            costCol = cost;
            impsCol = imps;
            liCol = li;
            namingCol = naming;
        }
    }
    if (headerIdx < 0 || (liCol < 0 && namingCol < 0)) return out;

    for (let i = headerIdx + 1; i < adjRows.length; i++) {
        const row = adjRows[i] ?? [];
        const date = parseSheetDate(row[dateCol]);
        if (!date) continue;
        if (num(row[costCol]) <= 0 && num(row[impsCol]) <= 0) continue;

        const id = liCol >= 0 ? (row[liCol] ?? "").trim() : namingLineItemId(row[namingCol]);
        if (!id) continue;
        const seen = out[id];
        if (!seen || date < seen) out[id] = date;
    }
    return out;
}

/**
 * Pulls the numeric line-item id out of a Level 1 naming string, used when the raw data has
 * no Line Item ID column of its own. Returns "" when that part is missing or not numeric.
 */
export function namingLineItemId(naming: string | undefined): string {
    const parts = (naming ?? "").split("_");
    const candidate = (parts[NAMING_ID_PART] ?? "").trim();
    return /^\d+$/.test(candidate) ? candidate : "";
}
