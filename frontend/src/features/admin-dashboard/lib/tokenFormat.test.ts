import { describe, expect, it } from "vitest";
import { formatTokens, formatUsd } from "./tokenFormat";

describe("formatTokens", () => {
    it("should keep small counts exact test", () => {
        // Given / When / Then
        expect(formatTokens(812)).toBe("812");
    });

    it("should abbreviate thousands and millions test", () => {
        // Given / When / Then
        expect(formatTokens(1_240)).toBe("1.2k");
        expect(formatTokens(340_500)).toBe("341k");
        expect(formatTokens(1_200_000)).toBe("1.2M");
        expect(formatTokens(24_000_000)).toBe("24M");
    });

    it("should render zero and non-finite input as zero test", () => {
        // Given: a report that recorded no usage, and a bad number from a partial payload
        // When / Then
        expect(formatTokens(0)).toBe("0");
        expect(formatTokens(Number.NaN)).toBe("0");
    });
});

describe("formatUsd", () => {
    it("should format an ordinary amount in dollars and cents test", () => {
        // Given / When / Then
        expect(formatUsd(12.5)).toBe("$12.50");
    });

    it("should keep extra decimals for a sub-cent amount test", () => {
        // Given: a cheap report — rounding it to $0.00 would read as "not measured"
        // When / Then
        expect(formatUsd(0.0032)).toBe("$0.0032");
    });

    it("should render zero as $0.00 test", () => {
        // Given / When / Then
        expect(formatUsd(0)).toBe("$0.00");
    });
});
