// The report's tactic list, once the user has dropped the media-plan rows they don't want reported.
//
// The wizard keeps every plan row in `mapping` (dropping is undoable, and the plan-order figures stay
// aligned), so the dense 1..N numbering the backend builds slides, sheet blocks and narrative around is
// produced here, at the payload edge, and only here. Each renumbered entry carries `planTacticNum`, its
// original position, which is what the backend re-aligns the plan-side figures (Estimates tab) by.
import type { MappingEntry } from "@/shared/api/types";

/**
 * Renumbers the reported tactics 1..N, pinning each one's original media-plan position.
 *
 * @param active the surviving mapping entries, in media-plan order
 */
export function toPayloadMapping(active: MappingEntry[]): MappingEntry[] {
    return active.map((m, i) => ({
        ...m,
        tacticNum: i + 1,
        planTacticNum: m.planTacticNum ?? m.tacticNum,
    }));
}

/**
 * Keeps only the entries of a per-tactic array whose plan row survived, in the same order — used to
 * line media-plan figures (parsed over the whole plan) up with the reported tactics.
 *
 * @param values   one value per entry of the full mapping
 * @param mapping  the full mapping the values were parsed against
 * @param excluded original tacticNums of the dropped rows
 */
export function keepActive<T>(values: T[], mapping: MappingEntry[], excluded: number[]): T[] {
    return values.filter((_, i) => {
        const entry = mapping[i];
        return entry !== undefined && !excluded.includes(entry.tacticNum);
    });
}
