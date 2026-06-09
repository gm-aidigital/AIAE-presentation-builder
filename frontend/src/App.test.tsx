import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";

vi.mock("@clerk/clerk-react", () => ({
    UserButton: () => <div data-testid="user-button-stub" />,
}));

/**
 * Frontend smoke test — renders App with QueryClient (AppShell uses Clerk
 * UserButton; mocked here because this test mounts App without AppRoot).
 * The /auth/me network call is left to fail (retry: false on the query),
 * which exercises the loading → error transition without needing a fetch
 * mock for a "does the app even render" gate.
 */
describe("App", () => {
    it("should render the scaffold heading test", () => {
        // Given:
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false } },
        });

        // When:
        render(
            <QueryClientProvider client={queryClient}>
                <App />
            </QueryClientProvider>
        );

        // Then:
        expect(screen.getByRole("heading", { level: 1 })).toBeTruthy();
    });
});
