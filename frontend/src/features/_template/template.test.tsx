import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TemplateProfilePanel } from "./ui/TemplateProfilePanel";

describe("TemplateProfilePanel", () => {
    it("should render loading state test", () => {
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false } },
        });
        render(
            <QueryClientProvider client={queryClient}>
                <TemplateProfilePanel />
            </QueryClientProvider>,
        );
        expect(screen.getByText(/loading profile/i)).toBeTruthy();
    });
});
