// Pure helpers for the EOM "Pacing & rates" step: what counts as a filled-in tactic
// and how many units the entered budget buys at the entered rate. No I/O.
import type { MappingEntry } from "@/shared/api/types";

/** A tactic is ready once it has a monthly budget, a buy type and a rate. */
export function pacingRowComplete(row: MappingEntry): boolean {
    return (row.monthlyBudget ?? 0) > 0 && !!row.rateType && (row.unitPrice ?? 0) > 0;
}

/** How many mapped tactics already carry full pacing input. */
export function pacingReadyCount(mapping: MappingEntry[] | null): number {
    return (mapping ?? []).filter(pacingRowComplete).length;
}

/** True once every mapped tactic is filled in — the gate for confirming pacing. */
export function pacingComplete(mapping: MappingEntry[] | null): boolean {
    const rows = mapping ?? [];
    return rows.length > 0 && rows.every(pacingRowComplete);
}

/**
 * Units the entered budget buys at the entered rate — CPM prices a thousand units,
 * CPC/CPV price a single one. Returns 0 while either side is still missing.
 */
export function estimatedUnits(row: MappingEntry): number {
    const budget = row.monthlyBudget ?? 0;
    const price = row.unitPrice ?? 0;
    if (budget <= 0 || price <= 0) return 0;
    const units = budget / price;
    return row.rateType === "CPM" ? units * 1000 : units;
}
