import { describe, expect, it } from "vitest";
import type { MappingEntry } from "@/shared/api/types";
import { keepActive, toPayloadMapping } from "./tacticSelection";

function entry(patch: Partial<MappingEntry>): MappingEntry {
    return { tacticNum: 1, tacticName: "Programmatic Display", autoMatched: false, ...patch } as MappingEntry;
}

describe("toPayloadMapping", () => {
    it("should renumber the reported tactics 1..N and pin their plan positions test", () => {
        // Given: the second and third plan lines survived, the first was dropped
        const active = [
            entry({ tacticNum: 2, tacticName: "Meta (CPM)", planTacticNum: 2 }),
            entry({ tacticNum: 5, tacticName: "Programmatic CTV", planTacticNum: 5 }),
        ];

        // When
        const payload = toPayloadMapping(active);

        // Then
        expect(payload.map((m) => m.tacticNum)).toEqual([1, 2]);
        expect(payload.map((m) => m.planTacticNum)).toEqual([2, 5]);
        expect(payload.map((m) => m.tacticName)).toEqual(["Meta (CPM)", "Programmatic CTV"]);
    });

    it("should fall back to the slot number when no plan position was carried test", () => {
        // Given: a mapping from before row exclusion existed
        const active = [entry({ tacticNum: 3, planTacticNum: undefined })];

        // When
        const payload = toPayloadMapping(active);

        // Then
        expect(payload[0]?.planTacticNum).toBe(3);
        expect(payload[0]?.tacticNum).toBe(1);
    });

    it("should return an empty payload for an empty selection test", () => {
        // Given / When / Then
        expect(toPayloadMapping([])).toEqual([]);
    });
});

describe("keepActive", () => {
    it("should keep the per-tactic values of the surviving rows only test", () => {
        // Given: three plan lines, the middle one dropped
        const mapping = [
            entry({ tacticNum: 1 }),
            entry({ tacticNum: 2, tacticName: "Meta (CPM)" }),
            entry({ tacticNum: 3, tacticName: "Meta (CPM)" }),
        ];
        const budgets = ["first", "second", "third"];

        // When
        const kept = keepActive(budgets, mapping, [2]);

        // Then: the third line keeps its own figure instead of inheriting the dropped one's
        expect(kept).toEqual(["first", "third"]);
    });

    it("should keep everything when nothing is excluded test", () => {
        // Given
        const mapping = [entry({ tacticNum: 1 }), entry({ tacticNum: 2 })];

        // When / Then
        expect(keepActive([10, 20], mapping, [])).toEqual([10, 20]);
    });

    it("should drop values with no mapping entry behind them test", () => {
        // Given: more values than mapping rows (stale parse)
        const mapping = [entry({ tacticNum: 1 })];

        // When / Then
        expect(keepActive([10, 20], mapping, [])).toEqual([10]);
    });
});
