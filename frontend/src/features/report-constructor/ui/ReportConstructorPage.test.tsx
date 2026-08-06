import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReportResume } from "@/shared/api/types";
import { ReportConstructorPage } from "./ReportConstructorPage";

const resumeQuery = vi.fn();
const readSheetSummary = vi.fn();
const adopt = vi.fn();
const navigate = vi.fn();
let search = "";
let adopting = false;

vi.mock("react-router-dom", async () => {
    const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
    return {
        ...actual,
        useNavigate: () => navigate,
        useSearchParams: () => [new URLSearchParams(search), vi.fn()],
    };
});

vi.mock("../api/useReportResume", () => ({ useReportResume: () => resumeQuery() }));
vi.mock("../api/useAdoptSheet", () => ({
    useAdoptSheet: () => ({ mutate: adopt, isPending: adopting }),
}));
vi.mock("../api/useMatchLineItems", () => ({
    useMatchLineItems: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("../api/useDetectDateRange", () => ({
    useDetectDateRange: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock("../api/useReportJob", () => ({
    startReportJob: vi.fn(),
    fetchReportJob: vi.fn(),
}));
vi.mock("@/shared/api/sheets", async () => {
    const actual = await vi.importActual<typeof import("@/shared/api/sheets")>("@/shared/api/sheets");
    return { ...actual, readSheetSummary: (url: string) => readSheetSummary(url) };
});

/**
 * Builds a resumable draft, overriding whatever the test is about.
 *
 * @param over the fields under test
 * @returns the resume payload
 */
function draft(over: Partial<ReportResume> = {}): ReportResume {
    return {
        jobId: 42,
        sheetUrl: "https://docs.google.com/spreadsheets/d/abc",
        reportType: "EOC",
        brief: "the brief",
        tacticNames: ["Display Prospecting", "CTV Retargeting"],
        ...over,
    } as ReportResume;
}

/**
 * Renders the constructor with the providers it needs.
 */
function renderPage() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter>
                <ReportConstructorPage />
            </MemoryRouter>
        </QueryClientProvider>
    );
}

describe("ReportConstructorPage — resuming a draft", () => {
    afterEach(() => {
        vi.clearAllMocks();
        search = "";
        adopting = false;
    });

    it("should start at the report-type step when no draft is being resumed test", () => {
        // Given: a plain /reports/new visit
        resumeQuery.mockReturnValue({ data: undefined, isLoading: false, isError: false });

        // When
        renderPage();

        // Then: the wizard starts from the beginning
        expect(screen.getByRole("heading", { name: /choose report type/i })).toBeTruthy();
        expect(screen.queryByText(/review the generated sheet/i)).toBeNull();
    });

    it("should hold the wizard while the draft is loading test", () => {
        // Given: ?resume= is present and the draft has not arrived
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: undefined, isLoading: true, isError: false });

        // When
        renderPage();

        // Then: the wizard is not shown half-seeded
        expect(screen.getByText(/opening your draft/i)).toBeTruthy();
    });

    it("should land on the review step with the draft's tactics test", async () => {
        // Given: a draft whose sheet summary has not been read back yet
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: draft(), isLoading: false, isError: false });
        readSheetSummary.mockResolvedValue([]);

        // When
        renderPage();

        // Then: the review step is shown, labelled from the names the draft recorded
        expect(await screen.findByText(/review the generated sheet/i)).toBeTruthy();
        expect(screen.getByText("Display Prospecting")).toBeTruthy();
        expect(screen.getByText("CTV Retargeting")).toBeTruthy();
        await waitFor(() =>
            expect(readSheetSummary).toHaveBeenCalledWith("https://docs.google.com/spreadsheets/d/abc")
        );
    });

    it("should prefer the tactic names the sheet itself reports test", async () => {
        // Given: the workbook was edited since the draft was saved and renamed a tactic
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: draft(), isLoading: false, isError: false });
        readSheetSummary.mockResolvedValue([
            { tactic: "Display — renamed in the sheet", spendPlan: "$10,000", spendFact: "$9,800" },
        ]);

        // When
        renderPage();

        // Then: the sheet wins over the recorded name, and its figures are shown
        expect(await screen.findByText("Display — renamed in the sheet")).toBeTruthy();
        expect(screen.getByText("$9,800")).toBeTruthy();
    });

    it("should not offer navigation back to the steps a resumed session cannot re-run test", async () => {
        // Given: a resumed draft — no media plan or Elevate data was loaded in this session
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: draft(), isLoading: false, isError: false });
        readSheetSummary.mockResolvedValue([]);
        renderPage();
        await screen.findByText(/review the generated sheet/i);

        // When-Then: the earlier stepper entries are disabled and the Back button is gone
        expect(screen.getByRole("button", { name: /data inputs/i }).hasAttribute("disabled")).toBe(true);
        expect(screen.getByRole("button", { name: /breakdowns/i }).hasAttribute("disabled")).toBe(true);
        expect(screen.queryByRole("button", { name: /^back$/i })).toBeNull();
    });

    it("should warn about hand-filled breakdowns using the toggles the draft recorded test", async () => {
        // Given: a draft whose workbook was prepared with a breakdown section enabled
        search = "resume=42";
        resumeQuery.mockReturnValue({
            data: draft({ breakdownSelections: [{ tacticNum: 1, breakdowns: ["aud"] }] }),
            isLoading: false,
            isError: false,
        });
        readSheetSummary.mockResolvedValue([]);

        // When
        renderPage();

        // Then: the review step warns, even though this session never ran the breakdown step
        expect(await screen.findByText(/breakdown slides need data filled in by hand/i)).toBeTruthy();
    });

    it("should ask for a brief when the adopted workbook carried none test", async () => {
        // Given: a workbook whose {{RFP info}} cell was empty
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: draft({ brief: "" }), isLoading: false, isError: false });
        readSheetSummary.mockResolvedValue([]);

        // When
        renderPage();
        await screen.findByText(/review the generated sheet/i);

        // Then: the deck cannot be generated without it, so it is asked for here
        expect(screen.getByText(/this sheet has no campaign brief/i)).toBeTruthy();
        const confirm = screen.getByRole("button", { name: /confirm/i }) as HTMLButtonElement;
        expect(confirm.disabled).toBe(true);

        // When: the user types one
        fireEvent.change(screen.getByPlaceholderText(/describe the campaign/i), {
            target: { value: "Acme summer awareness push" },
        });

        // Then: the gate lifts and the field stays put rather than vanishing mid-typing
        expect((screen.getByRole("button", { name: /confirm/i }) as HTMLButtonElement).disabled).toBe(false);
        expect(screen.getByText(/this sheet has no campaign brief/i)).toBeTruthy();
    });

    it("should not ask for a brief when the workbook supplied one test", async () => {
        // Given: a workbook carrying its own campaign context
        search = "resume=42";
        resumeQuery.mockReturnValue({ data: draft(), isLoading: false, isError: false });
        readSheetSummary.mockResolvedValue([]);

        // When
        renderPage();
        await screen.findByText(/review the generated sheet/i);

        // Then
        expect(screen.queryByText(/this sheet has no campaign brief/i)).toBeNull();
        expect((screen.getByRole("button", { name: /confirm/i }) as HTMLButtonElement).disabled).toBe(false);
    });

    it("should fall back to a fresh report when the draft is gone test", async () => {
        // Given: the draft was dismissed or already generated elsewhere
        search = "resume=42";
        resumeQuery.mockReturnValue({
            data: undefined,
            isLoading: false,
            isError: true,
            error: new Error("This draft is no longer available."),
        });

        // When
        renderPage();

        // Then: the user is told, and left on a usable wizard rather than a blank review step
        expect(await screen.findByText(/no longer available/i)).toBeTruthy();
        expect(screen.queryByText(/review the generated sheet/i)).toBeNull();
    });
});

describe("ReportConstructorPage — adopting a filled sheet", () => {
    afterEach(() => {
        vi.clearAllMocks();
        search = "";
        adopting = false;
    });

    /**
     * Walks from the report-type step to the data-inputs step, where the adopt card lives.
     */
    function goToDataInputs() {
        resumeQuery.mockReturnValue({ data: undefined, isLoading: false, isError: false });
        renderPage();
        fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    }

    it("should offer the adopt card before any of the normal inputs test", () => {
        // Given-When: the user reaches the data-inputs step
        goToDataInputs();

        // Then: the shortcut is visible without having to hunt for it
        expect(screen.getByText(/already have a filled report sheet/i)).toBeTruthy();
    });

    it("should adopt a pasted sheet with the chosen report type test", () => {
        // Given: the user opens the card and pastes a workbook link
        goToDataInputs();
        fireEvent.click(screen.getByRole("button", { name: /use my sheet/i }));
        fireEvent.change(screen.getByPlaceholderText("https://docs.google.com/spreadsheets/…"), {
            target: { value: "https://docs.google.com/spreadsheets/d/abc/edit" },
        });

        // When: they use it
        fireEvent.click(screen.getByRole("button", { name: /use this sheet/i }));

        // Then: the sheet is adopted for the report type picked on step 1
        expect(adopt).toHaveBeenCalledTimes(1);
        expect(adopt.mock.calls[0][0]).toEqual({
            sheetUrl: "https://docs.google.com/spreadsheets/d/abc/edit",
            reportType: "EOC",
        });
    });

    it("should route to the new draft once the sheet is adopted test", () => {
        // Given: an adoption that succeeds
        goToDataInputs();
        fireEvent.click(screen.getByRole("button", { name: /use my sheet/i }));
        fireEvent.change(screen.getByPlaceholderText("https://docs.google.com/spreadsheets/…"), {
            target: { value: "https://docs.google.com/spreadsheets/d/abc/edit" },
        });
        fireEvent.click(screen.getByRole("button", { name: /use this sheet/i }));

        // When: the server returns the new draft
        adopt.mock.calls[0][1].onSuccess({ jobId: 77, sheetUrl: "https://docs.google.com/spreadsheets/d/abc" });

        // Then: it is entered through the same reload-safe door as any other draft
        expect(navigate).toHaveBeenCalledWith("/reports/new?resume=77");
    });

    it("should surface the server's reason when the sheet is rejected test", () => {
        // Given: a link the server will not accept
        goToDataInputs();
        fireEvent.click(screen.getByRole("button", { name: /use my sheet/i }));
        fireEvent.change(screen.getByPlaceholderText("https://docs.google.com/spreadsheets/…"), {
            target: { value: "https://docs.google.com/spreadsheets/d/abc/edit" },
        });
        fireEvent.click(screen.getByRole("button", { name: /use this sheet/i }));

        // When: the server explains what is wrong with it
        adopt.mock.calls[0][1].onError(
            new Error("That sheet doesn't look like a report workbook — no tactics found on its first tab")
        );

        // Then: the user reads that, not a generic failure, and stays on the form
        expect(screen.getByText(/no tactics found/i)).toBeTruthy();
        expect(navigate).not.toHaveBeenCalled();
    });

    it("should not offer to adopt a link that is not a Google Sheet test", () => {
        // Given: a pasted link to something else
        goToDataInputs();
        fireEvent.click(screen.getByRole("button", { name: /use my sheet/i }));
        fireEvent.change(screen.getByPlaceholderText("https://docs.google.com/spreadsheets/…"), {
            target: { value: "https://example.com/my-report" },
        });

        // When-Then: the action is disabled rather than failing on the server
        expect((screen.getByRole("button", { name: /use this sheet/i }) as HTMLButtonElement).disabled).toBe(true);
        expect(adopt).not.toHaveBeenCalled();
    });
});
