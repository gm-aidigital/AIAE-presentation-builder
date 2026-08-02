import { describe, expect, it } from "vitest";
import { evenPacedBudget, firstDeliveryDateByLineItem, namingLineItemId } from "./evenPacing";

describe("evenPacedBudget", () => {
    it("should spread the plan spend over the real live days test", () => {
        // Given: $10,000 planned, plan ends 31 July, first delivery 3 April (two days late),
        // reporting on June
        // When
        const even = evenPacedBudget({
            spendPlan: 10000,
            firstDate: "2026-04-03",
            flightEnd: "2026-07-31",
            windowStart: "2026-06-01",
            windowEnd: "2026-06-30",
        });

        // Then: 120 live days → $83.33/day → 30 June days
        expect(even?.flightDays).toBe(120);
        expect(even?.windowDays).toBe(30);
        expect(even?.daily).toBeCloseTo(83.3333, 4);
        expect(even?.budget).toBeCloseTo(2500, 2);
    });

    it("should bill only the days the flight covers inside the window test", () => {
        // Given: the same plan ending 10 June
        // When
        const even = evenPacedBudget({
            spendPlan: 10000,
            firstDate: "2026-04-03",
            flightEnd: "2026-06-10",
            windowStart: "2026-06-01",
            windowEnd: "2026-06-30",
        });

        // Then: 69 live days, 10 of them in the window
        expect(even?.flightDays).toBe(69);
        expect(even?.windowDays).toBe(10);
        expect(even?.budget).toBeCloseTo(1449.28, 2);
    });

    it("should return null when an input is missing or the flight missed the window test", () => {
        // Given: no plan spend / no first delivery / no flight end / a flight that ended earlier
        const base = { windowStart: "2026-06-01", windowEnd: "2026-06-30" };

        // When / Then
        expect(evenPacedBudget({ ...base, spendPlan: 0, firstDate: "2026-04-03", flightEnd: "2026-07-31" })).toBeNull();
        expect(evenPacedBudget({ ...base, spendPlan: 10000, firstDate: null, flightEnd: "2026-07-31" })).toBeNull();
        expect(evenPacedBudget({ ...base, spendPlan: 10000, firstDate: "2026-04-03", flightEnd: null })).toBeNull();
        expect(
            evenPacedBudget({ ...base, spendPlan: 10000, firstDate: "2026-04-03", flightEnd: "2026-05-31" })
        ).toBeNull();
    });

    it("should return null when the window itself is not set yet test", () => {
        // Given: the user has not confirmed flight dates
        // When / Then
        expect(
            evenPacedBudget({
                spendPlan: 10000,
                firstDate: "2026-04-03",
                flightEnd: "2026-07-31",
                windowStart: "",
                windowEnd: "",
            })
        ).toBeNull();
    });
});

describe("firstDeliveryDateByLineItem", () => {
    it("should take the earliest delivering date per line item test", () => {
        // Given: a Basic grid where line item 616641 has a zero row on 1 April and real
        // delivery from 3 April, and 700002 starts on 2 April
        const rows = [
            ["Report", "Lennox"],
            ["Date", "Channel", "Line Item ID", "Cost", "Impressions"],
            ["2026-04-01", "Display", "616641", "0", "0"],
            ["4/3/2026", "Display", "616641", "$120.50", "18,000"],
            ["2026-04-05", "Display", "616641", "90", "12000"],
            ["2026-04-02", "CTV", "700002", "300", "20000"],
        ];

        // When
        const first = firstDeliveryDateByLineItem(rows);

        // Then: the empty 1 April row does not count as a launch
        expect(first).toEqual({ "616641": "2026-04-03", "700002": "2026-04-02" });
    });

    it("should fall back to the Level 1 naming id when there is no id column test", () => {
        // Given: only a naming string carrying the id in its ninth part
        const naming = "AIDG_Lennox_2026_Q2_Display_Prospecting_CPM_-_616641_Contextual";
        const rows = [
            ["Date", "Channel", "Level 1 Naming", "Cost", "Impressions"],
            ["2026-04-03", "Display", naming, "120", "18000"],
        ];

        // When
        const first = firstDeliveryDateByLineItem(rows);

        // Then
        expect(first).toEqual({ "616641": "2026-04-03" });
    });

    it("should return an empty map when the grid has no usable header test", () => {
        // Given: a grid missing the Cost/Impressions header pair
        // When / Then
        expect(firstDeliveryDateByLineItem([["Date", "Channel"], ["2026-04-03", "Display"]])).toEqual({});
        expect(firstDeliveryDateByLineItem(null)).toEqual({});
    });
});

describe("namingLineItemId", () => {
    it("should read the numeric id part and reject anything else test", () => {
        // Given / When / Then
        expect(namingLineItemId("AIDG_Lennox_2026_Q2_Display_Prospecting_CPM_-_616641_Contextual")).toBe("616641");
        expect(namingLineItemId("AIDG_Lennox_2026_Q2_Display_Prospecting_CPM_-_-_Contextual")).toBe("");
        expect(namingLineItemId(undefined)).toBe("");
    });
});
