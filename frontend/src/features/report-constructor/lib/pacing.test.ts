import { describe, expect, it } from "vitest";
import type { MappingEntry } from "@/shared/api/types";
import { estimatedUnits, pacingComplete, pacingReadyCount, pacingRowComplete } from "./pacing";

function entry(patch: Partial<MappingEntry>): MappingEntry {
    return { tacticNum: 1, tacticName: "Programmatic Display", ...patch } as MappingEntry;
}

describe("pacingRowComplete", () => {
    it("should accept a row with budget, buy type and rate test", () => {
        // Given / When / Then
        expect(pacingRowComplete(entry({ monthlyBudget: 1500, rateType: "CPM", unitPrice: 6 }))).toBe(true);
    });

    it("should reject a row missing the buy type test", () => {
        // Given / When / Then
        expect(pacingRowComplete(entry({ monthlyBudget: 1500, unitPrice: 6 }))).toBe(false);
    });

    it("should reject zero budget or zero rate test", () => {
        // Given / When / Then
        expect(pacingRowComplete(entry({ monthlyBudget: 0, rateType: "CPM", unitPrice: 6 }))).toBe(false);
        expect(pacingRowComplete(entry({ monthlyBudget: 1500, rateType: "CPM", unitPrice: 0 }))).toBe(false);
    });
});

describe("pacingReadyCount", () => {
    it("should count only the fully filled tactics test", () => {
        // Given
        const mapping = [
            entry({ monthlyBudget: 1500, rateType: "CPM", unitPrice: 6 }),
            entry({ tacticNum: 2, monthlyBudget: 1500 }),
        ];

        // When / Then
        expect(pacingReadyCount(mapping)).toBe(1);
        expect(pacingReadyCount(null)).toBe(0);
    });
});

describe("pacingComplete", () => {
    it("should be false for an empty mapping test", () => {
        // Given / When / Then
        expect(pacingComplete([])).toBe(false);
        expect(pacingComplete(null)).toBe(false);
    });

    it("should be true once every tactic is filled test", () => {
        // Given
        const mapping = [
            entry({ monthlyBudget: 1500, rateType: "CPM", unitPrice: 6 }),
            entry({ tacticNum: 2, monthlyBudget: 900, rateType: "CPC", unitPrice: 3 }),
        ];

        // When / Then
        expect(pacingComplete(mapping)).toBe(true);
    });
});

describe("estimatedUnits", () => {
    it("should price CPM per thousand impressions test", () => {
        // Given / When / Then
        expect(estimatedUnits(entry({ monthlyBudget: 1500, rateType: "CPM", unitPrice: 6 }))).toBe(250_000);
    });

    it("should price CPC per single click test", () => {
        // Given / When / Then
        expect(estimatedUnits(entry({ monthlyBudget: 900, rateType: "CPC", unitPrice: 3 }))).toBe(300);
    });

    it("should return zero while an input is missing test", () => {
        // Given / When / Then
        expect(estimatedUnits(entry({ rateType: "CPM", unitPrice: 6 }))).toBe(0);
    });
});
