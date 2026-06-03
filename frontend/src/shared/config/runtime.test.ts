import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl, runtimeConfig } from "./runtime";

describe("resolveApiBaseUrl", () => {
    it("should keep API calls same-origin in Vite dev mode test", () => {
        expect(resolveApiBaseUrl()).toBe("");
    });

    it("should use the Spring servlet context path when configured test", () => {
        expect(resolveApiBaseUrl("", "/employee-directory/")).toBe("/employee-directory");
    });

    it("should reject duplicated OpenAPI version prefixes test", () => {
        expect(() => resolveApiBaseUrl("/api/v1")).toThrow(/must not include/);
    });
});

describe("auth token", () => {
    it("should return null when no Clerk token getter is registered yet test", async () => {
        // Given: AuthProvider has not mounted (no ClerkTokenBridge registered)

        // When / Then: no token is available, never throws
        expect(await runtimeConfig.getAuthToken()).toBeNull();
    });
});
