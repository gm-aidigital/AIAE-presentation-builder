import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReportSummary } from "@/shared/api/types";
import { MyReportsPage } from "./MyReportsPage";

const navigate = vi.fn();
const dismiss = vi.fn();
const reportsQuery = vi.fn();

vi.mock("react-router-dom", async () => {
    const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
    return { ...actual, useNavigate: () => navigate };
});

vi.mock("../api/useMyReports", () => ({
    useMyReports: () => reportsQuery(),
    useDismissReport: () => ({ mutate: dismiss, isPending: false }),
}));

/**
 * Builds one history row, overriding whatever the test is about.
 *
 * @param over the fields under test
 * @returns the report summary
 */
function report(over: Partial<ReportSummary>): ReportSummary {
    return {
        jobId: 1,
        type: "EOC",
        status: "done",
        title: "EOC report",
        createdAt: "2026-08-01T10:00:00",
        inputTokens: 0,
        outputTokens: 0,
        totalTokens: 0,
        costUsd: 0,
        ...over,
    } as ReportSummary;
}

/**
 * Renders the page with the providers it needs.
 */
function renderPage() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter>
                <MyReportsPage />
            </MemoryRouter>
        </QueryClientProvider>
    );
}

describe("MyReportsPage", () => {
    afterEach(() => {
        vi.clearAllMocks();
        vi.unstubAllGlobals();
    });

    it("should render the loading state while the history is being read test", () => {
        // Given: the history query has not resolved
        reportsQuery.mockReturnValue({ data: undefined, isLoading: true, isError: false });

        // When
        renderPage();

        // Then
        expect(screen.getByText(/loading your reports/i)).toBeTruthy();
    });

    it("should render the error state when the history cannot be read test", () => {
        // Given: the history query failed
        reportsQuery.mockReturnValue({
            data: undefined,
            isLoading: false,
            isError: true,
            error: new Error("nope"),
        });

        // When
        renderPage();

        // Then
        expect(screen.getByText("nope")).toBeTruthy();
    });

    it("should render the empty state when the user has no reports test", () => {
        // Given: an empty history
        reportsQuery.mockReturnValue({
            data: { total: 0, reports: [] },
            isLoading: false,
            isError: false,
        });

        // When
        renderPage();

        // Then
        expect(screen.getByText(/no reports yet/i)).toBeTruthy();
    });

    it("should offer Open report for a finished deck test", () => {
        // Given: a finished report with a deck
        reportsQuery.mockReturnValue({
            data: {
                total: 1,
                reports: [report({ draft: false, slideUrl: "https://deck" })],
            },
            isLoading: false,
            isError: false,
        });

        // When
        renderPage();

        // Then: the finished-report actions, not the draft ones
        expect(screen.getByRole("button", { name: /open report/i })).toBeTruthy();
        expect(screen.queryByRole("button", { name: /continue/i })).toBeNull();
        expect(screen.queryByRole("button", { name: /discard/i })).toBeNull();
    });

    it("should offer Continue for a draft and route to the constructor with its job id test", () => {
        // Given: a draft — a finished sheet build with no deck
        reportsQuery.mockReturnValue({
            data: {
                total: 1,
                reports: [report({ jobId: 42, draft: true, slideUrl: undefined, sheetUrl: "https://sheet" })],
            },
            isLoading: false,
            isError: false,
        });
        renderPage();

        // When: the user continues the draft
        fireEvent.click(screen.getByRole("button", { name: /continue/i }));

        // Then: the constructor is opened on that draft, and the row never claimed the report was ready
        expect(navigate).toHaveBeenCalledWith("/reports/new?resume=42");
        expect(screen.getByText(/sheet ready/i)).toBeTruthy();
        expect(screen.queryByRole("button", { name: /open report/i })).toBeNull();
    });

    it("should dismiss a draft only after the user confirms test", () => {
        // Given: a draft, and a user who declines the confirm the first time
        reportsQuery.mockReturnValue({
            data: { total: 1, reports: [report({ jobId: 42, draft: true })] },
            isLoading: false,
            isError: false,
        });
        const confirm = vi.fn().mockReturnValueOnce(false).mockReturnValueOnce(true);
        vi.stubGlobal("confirm", confirm);
        renderPage();

        // When: Discard is clicked twice
        fireEvent.click(screen.getByRole("button", { name: /discard/i }));
        fireEvent.click(screen.getByRole("button", { name: /discard/i }));

        // Then: only the confirmed click dismissed the draft
        expect(dismiss).toHaveBeenCalledTimes(1);
        expect(dismiss).toHaveBeenCalledWith(42);
    });
});
