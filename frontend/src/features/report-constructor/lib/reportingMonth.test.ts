import { describe, expect, it } from "vitest";
import { eomWindow, previousMonthWindow, toIsoDate } from "./reportingMonth";

describe("toIsoDate", () => {
    it("should format a local date without a UTC shift test", () => {
        // Given: a local date late in the day, where toISOString() would roll to the next day
        // When / Then
        expect(toIsoDate(new Date(2026, 6, 31, 23, 30))).toBe("2026-07-31");
    });
});

describe("previousMonthWindow", () => {
    it("should return the whole previous month test", () => {
        // Given: the user opens the constructor on 1 August 2026
        // When
        const window = previousMonthWindow(new Date(2026, 7, 1));
        // Then: July, first to last day
        expect(window).toEqual({ start: "2026-07-01", end: "2026-07-31" });
    });

    it("should handle a short previous month test", () => {
        // Given / When: any day in March 2026 → February 2026 (28 days)
        const window = previousMonthWindow(new Date(2026, 2, 17));
        // Then
        expect(window).toEqual({ start: "2026-02-01", end: "2026-02-28" });
    });

    it("should cross the year boundary test", () => {
        // Given / When: January 2026 → December 2025
        const window = previousMonthWindow(new Date(2026, 0, 5));
        // Then
        expect(window).toEqual({ start: "2025-12-01", end: "2025-12-31" });
    });
});

describe("eomWindow", () => {
    const onAugustFirst = new Date(2026, 7, 1);

    it("should keep the whole previous month for a campaign running all month test", () => {
        // Given: a campaign live March–September, read on 1 August
        // When / Then: the full month of July
        expect(eomWindow({ start: "2026-03-01", end: "2026-09-30" }, onAugustFirst)).toEqual({
            start: "2026-07-01",
            end: "2026-07-31",
        });
    });

    it("should start on the campaign launch date when it launched mid-month test", () => {
        // Given: the campaign started 17 July
        // When / Then: the window opens on the launch date, not the 1st
        expect(eomWindow({ start: "2026-07-17", end: "2026-09-30" }, onAugustFirst)).toEqual({
            start: "2026-07-17",
            end: "2026-07-31",
        });
    });

    it("should end on the campaign end date when it ended mid-month test", () => {
        // Given: the campaign ended 22 July
        // When / Then
        expect(eomWindow({ start: "2026-06-01", end: "2026-07-22" }, onAugustFirst)).toEqual({
            start: "2026-07-01",
            end: "2026-07-22",
        });
    });

    it("should fall back to the detected range when the campaign missed the month test", () => {
        // Given: the campaign closed in May, so there is no July overlap
        // When / Then: the detected range is kept for the user to correct
        expect(eomWindow({ start: "2026-04-01", end: "2026-05-31" }, onAugustFirst)).toEqual({
            start: "2026-04-01",
            end: "2026-05-31",
        });
    });
});
