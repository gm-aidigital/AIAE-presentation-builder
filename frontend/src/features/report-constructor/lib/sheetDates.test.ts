import { describe, expect, it } from "vitest";
import { daysInclusive, overlapDays, parseSheetDate } from "./sheetDates";

describe("parseSheetDate", () => {
    it("should parse the formats Sheets emits test", () => {
        // Given / When / Then
        expect(parseSheetDate("2026-06-03")).toBe("2026-06-03");
        expect(parseSheetDate("2026/6/3")).toBe("2026-06-03");
        expect(parseSheetDate("6/3/2026")).toBe("2026-06-03");
        expect(parseSheetDate("6-3-26")).toBe("2026-06-03");
        expect(parseSheetDate("Jun 3, 2026")).toBe("2026-06-03");
        expect(parseSheetDate("3 June 2026")).toBe("2026-06-03");
    });

    it("should drop a trailing time from a BigQuery timestamp test", () => {
        // Given: an export that kept midnight on every date
        // When / Then
        expect(parseSheetDate("2026-06-03 00:00:00")).toBe("2026-06-03");
        expect(parseSheetDate("2026-06-03T00:00:00Z")).toBe("2026-06-03");
    });

    it("should return null for blank and non-date cells test", () => {
        // Given / When / Then
        expect(parseSheetDate("")).toBeNull();
        expect(parseSheetDate(undefined)).toBeNull();
        expect(parseSheetDate("n/a")).toBeNull();
        expect(parseSheetDate("2026-02-31")).toBeNull();
    });
});

describe("daysInclusive", () => {
    it("should count both ends test", () => {
        // Given: 3 April through 31 July
        // When / Then: 28 + 31 + 30 + 31
        expect(daysInclusive("2026-04-03", "2026-07-31")).toBe(120);
        expect(daysInclusive("2026-06-01", "2026-06-30")).toBe(30);
        expect(daysInclusive("2026-06-01", "2026-06-01")).toBe(1);
    });

    it("should return 0 when the end precedes the start test", () => {
        // Given / When / Then
        expect(daysInclusive("2026-06-30", "2026-06-01")).toBe(0);
    });
});

describe("overlapDays", () => {
    it("should count the days two ranges share test", () => {
        // Given: a flight 3 Apr–31 Jul reported on June
        // When / Then
        expect(overlapDays("2026-04-03", "2026-07-31", "2026-06-01", "2026-06-30")).toBe(30);
    });

    it("should clip a flight that ends mid-window test", () => {
        // Given: the flight stops on 10 June
        // When / Then
        expect(overlapDays("2026-04-03", "2026-06-10", "2026-06-01", "2026-06-30")).toBe(10);
    });

    it("should return 0 for disjoint ranges test", () => {
        // Given / When / Then
        expect(overlapDays("2026-04-03", "2026-05-31", "2026-06-01", "2026-06-30")).toBe(0);
    });
});
