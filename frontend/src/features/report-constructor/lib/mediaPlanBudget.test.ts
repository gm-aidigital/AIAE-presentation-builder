import { describe, expect, it } from "vitest";
import { deriveAmount, extractTacticBudgets, namingTail, parseNumber } from "./mediaPlanBudget";

// A trimmed Proposal grid: header row with "Media", then two Programmatic
// Display rows that differ only by units/price, then a section label and a
// Google SEM row.
const HEADER = ["Flight Start", "Flight End", "Geo", "Media", "Comments", "Rate Type", "Units (Imps/ Clicks)", "Unit Price"];
const planRows: string[][] = [
    ["Client name:", "Visit Grapevine"],
    HEADER,
    ["Evergreen"], // section label — empty Media cell
    ["2026-06-08", "2026-09-30", "See Geo Tab", "Programmatic Display", "3P, Contextual", "CPM", "9142858", "7.0"],
    ["2026-06-08", "2026-09-30", "See Geo Tab", "Programmatic Display", "Whitelist Strategy", "CPM", "1200000", "10.0"],
    ["2026-03-01", "2026-09-30", "See Geo Tab", "Google SEM", "Even-paced", "CPC", "3429", "3.5"],
    ["Totals:", "", "", "", "", "", "10346287"],
];

describe("parseNumber", () => {
    it("should strip currency and separators test", () => {
        // Given / When / Then
        expect(parseNumber("$1,200,000")).toBe(1200000);
    });

    it("should return 0 for empty or non-numeric cells test", () => {
        expect(parseNumber(undefined)).toBe(0);
        expect(parseNumber("n/a")).toBe(0);
    });
});

describe("deriveAmount", () => {
    it("should bill CPM per thousand units test", () => {
        // Given: 9,142,858 impressions at $7 CPM
        // When / Then: cost is units/1000 × rate
        expect(deriveAmount(9142858, 7, "CPM")).toBeCloseTo(64000.006, 2);
    });

    it("should bill CPC per unit test", () => {
        // Given: 3,429 clicks at $3.50 CPC
        expect(deriveAmount(3429, 3.5, "CPC")).toBeCloseTo(12001.5, 2);
    });
});

describe("extractTacticBudgets", () => {
    it("should align duplicated tactic names to their own rows in order test", () => {
        // Given: two identical "Programmatic Display" tactics plus a SEM tactic
        const names = ["Programmatic Display", "Programmatic Display", "Google SEM"];

        // When
        const budgets = extractTacticBudgets(planRows, names);

        // Then: each duplicate keeps its own units/price, not the first row's
        expect(budgets[0]?.units).toBe(9142858);
        expect(budgets[0]?.amount).toBeCloseTo(64000.006, 2);
        expect(budgets[1]?.units).toBe(1200000);
        expect(budgets[1]?.amount).toBeCloseTo(12000, 2);
        expect(budgets[2]?.units).toBe(3429);
        expect(budgets[2]?.rateType).toBe("CPC");
    });

    it("should return nulls when no Media header exists test", () => {
        // Given: a grid with no "Media" column
        // When
        const budgets = extractTacticBudgets([["a", "b"], ["c", "d"]], ["Google SEM"]);

        // Then: fail-safe — no budgets attached
        expect(budgets).toEqual([null]);
    });

    it("should return an empty-aligned array for empty rows test", () => {
        expect(extractTacticBudgets(null, ["X", "Y"])).toEqual([null, null]);
    });
});

describe("namingTail", () => {
    it("should return the token after the id test", () => {
        // Given: a Level-1 naming ending in the audience token
        const naming = "GCVB_..._Display_Prospecting_CPM_-_616641_Contextual";

        // When / Then
        expect(namingTail(naming, "616641")).toBe("Contextual");
    });

    it("should collapse dash padding after the id test", () => {
        const naming = "GCVB_..._Display_Prospecting_CPM_-_616642_-_-_-_WhiteList_Clicks";
        expect(namingTail(naming, "616642")).toBe("WhiteList Clicks");
    });

    it("should fall back to the last meaningful segment when the id is absent test", () => {
        const naming = "GCVB_..._Google Search_Prospecting_CPC_-_616653_-_GVRR-Branded";
        expect(namingTail(naming, "")).toBe("GVRR Branded");
    });

    it("should return empty string for empty naming test", () => {
        expect(namingTail("", "616641")).toBe("");
    });
});
