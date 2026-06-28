import { describe, expect, it } from "vitest";
import { GEO_TAB_CANDIDATES, resolveTabName } from "./sheets";

describe("resolveTabName", () => {
    it("should match a candidate case-insensitively test", () => {
        // Given: the workbook spells the tab "geo" in lower case
        const tabs = ["Proposal", "geo", "Basic"];

        // When:
        const resolved = resolveTabName(tabs, GEO_TAB_CANDIDATES, "geo");

        // Then: the actual title from the workbook is returned, preserving its casing
        expect(resolved).toBe("geo");
    });

    it("should resolve a non-standard geo tab title via the needle fallback test", () => {
        // Given: the geo tab is named "GeoTab" (the real-world Gulf South file)
        const tabs = ["Proposal", "Audience&Inventory", "GeoTab", "Flight Timing"];

        // When:
        const resolved = resolveTabName(tabs, GEO_TAB_CANDIDATES, "geo");

        // Then: the substring match picks up "GeoTab"
        expect(resolved).toBe("GeoTab");
    });

    it("should return undefined when no tab matches test", () => {
        // Given: a workbook with no geo tab at all
        const tabs = ["Proposal", "Basic"];

        // When:
        const resolved = resolveTabName(tabs, GEO_TAB_CANDIDATES, "geo");

        // Then:
        expect(resolved).toBeUndefined();
    });

    it("should return undefined for an empty or missing tab list test", () => {
        // When-Then:
        expect(resolveTabName([], GEO_TAB_CANDIDATES, "geo")).toBeUndefined();
        expect(resolveTabName(undefined, GEO_TAB_CANDIDATES, "geo")).toBeUndefined();
    });
});
