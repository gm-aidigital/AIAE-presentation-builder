import { useEffect, useMemo, useRef, useState } from "react";
import { MEDIA_PLAN_FALLBACK_TAB, MEDIA_PLAN_PRIMARY_TAB, readSheetSummary, readSheetTab } from "@/shared/api/sheets";
import type { GenerateRequest, LineItemMatchResult, Rows2D, SheetReadResult, SheetSummaryRow } from "@/shared/api/types";
import { WizardProvider, useWizard } from "@/shared/wizard/WizardContext";
import { extractTacticBudgets, looksLikeMediaPlan, type TacticBudget } from "../lib/mediaPlanBudget";
import { keepActive, toPayloadMapping } from "../lib/tacticSelection";
import { eomWindow } from "../lib/reportingMonth";
import { useDetectDateRange } from "../api/useDetectDateRange";
import { useMatchLineItems } from "../api/useMatchLineItems";
import { fetchReportJob, startReportJob } from "../api/useReportJob";
import { MatchModal } from "./MatchModal";
import { PacingModal } from "./PacingModal";
import { Stepper } from "./Stepper";
import {
    allowedBreakdowns,
    BREAKDOWNS,
    StepBreakdowns,
    type BreakdownId,
    type BreakdownState,
    type TacticView,
} from "./StepBreakdowns";
import { StepDataInputs, type InputErrors } from "./StepDataInputs";
import { StepGenerate, type GenStatus } from "./StepGenerate";
import { StepReportType } from "./StepReportType";
import { StepReviewSheet, type ReviewRow } from "./StepReviewSheet";
import { ToastProvider, useToast } from "./ToastContext";
import "./report-constructor.css";

const DEFAULT_BREAKDOWNS: BreakdownState = { tp: false, ca: false, geo: false, aud: false, dev: false };
const NO_ERRORS: InputErrors = {
    brief: false,
    marketVolume: false,
    sheet: false,
    adj: false,
    dates: false,
};
const JOB_TOTAL = 7;
// Step numbers the SLIDES_FROM_SHEET job reports, in order (ReportGenerationServiceImpl
// #runSlidesFromSheet emits 1, 3, 6, 7 — not a dense 1..7 run). Each checkpoint maps to one
// displayed stage in StepGenerate, so the visual stages stay in sync with the real work.
const GEN_STEP_CHECKPOINTS = [1, 3, 6, 7];

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });
const grouped = new Intl.NumberFormat("en-US");

function compactUnits(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace(/\.0$/, "")}M`;
    if (n >= 10_000) return `${Math.round(n / 1000)}K`;
    return grouped.format(n);
}

/** Canonical Google Sheet URL from its id, so admins can reopen the source; undefined when no sheet. */
function sheetUrlFromId(sheetId: string | undefined): string | undefined {
    return sheetId ? `https://docs.google.com/spreadsheets/d/${sheetId}` : undefined;
}

/** One compact media-plan line, e.g. "$25,518 · 10M @ $2.5 CPM". */
function budgetLine(b: TacticBudget | null): string {
    if (!b) return "";
    const seg: string[] = [];
    if (b.amount > 0) seg.push(usd.format(Math.round(b.amount)));
    if (b.units > 0) {
        const rate = b.rateType ? ` @ $${b.unitPrice} ${b.rateType.toUpperCase()}` : "";
        seg.push(`${compactUnits(b.units)}${rate}`);
    }
    return seg.join(" · ");
}

/**
 * Flattens every loaded workbook tab into a single 2-D grid, prefixing each tab's
 * rows with a `### TAB: <name> ###` marker row so Claude can tell the tabs apart
 * when it scans the bundle for geo targeting.
 */
function buildWorkbookRows(loaded: { tab: string; rows: Rows2D }[]): Rows2D {
    const out: Rows2D = [];
    for (const { tab, rows } of loaded) {
        out.push([`### TAB: ${tab} ###`]);
        for (const row of rows) out.push(row);
        out.push([""]);
    }
    return out;
}

export function ReportConstructorPage() {
    return (
        <WizardProvider>
            <ToastProvider>
                <PageInner />
            </ToastProvider>
        </WizardProvider>
    );
}

function PageInner() {
    const w = useWizard();
    const { showToast } = useToast();

    const matchMutation = useMatchLineItems();
    const detectDateRangeMutation = useDetectDateRange();

    const [step, setStep] = useState(0);
    // Highest step reached — the stepper lets the user jump back to any visited step.
    const [maxStep, setMaxStep] = useState(0);
    useEffect(() => setMaxStep((m) => Math.max(m, step)), [step]);
    const [errors, setErrors] = useState<InputErrors>(NO_ERRORS);
    const [mediaPulling, setMediaPulling] = useState(false);
    const [elevatePulling, setElevatePulling] = useState(false);
    // When neither "Proposal" nor "Estimates" is found, we don't fail — we offer the
    // workbook's visible tabs so the user can point us at the tab that holds the plan.
    const [mediaTabPicker, setMediaTabPicker] = useState<{ url: string; tabs: string[] } | null>(null);

    const [matchOpen, setMatchOpen] = useState(false);
    const [matchData, setMatchData] = useState<LineItemMatchResult | null>(null);
    // EOM pacing input (monthly budget, buy type, rate per tactic) — its own dialog, gated on step 2.
    const [pacingOpen, setPacingOpen] = useState(false);

    // Per-tactic breakdown toggles, keyed by tacticNum. Sent as breakdownSelections in the SHEET
    // build payload: the backend clears every breakdown section a tactic did not enable on the
    // generated sheet's "Breakdowns" tab.
    const [breakdowns, setBreakdowns] = useState<Record<number, BreakdownState>>({});

    // Sheet-assembly (target=SHEET) job → produces the review sheet.
    const [building, setBuilding] = useState(false);
    const [sheetUrl, setSheetUrl] = useState<string | null>(null);
    // Per-tactic plan/fact figures read back from the assembled sheet's summary table.
    const [summaryRows, setSummaryRows] = useState<SheetSummaryRow[] | null>(null);
    const [summaryLoading, setSummaryLoading] = useState(false);

    // Final report (target=SLIDES_FROM_SHEET) job → produces the deck from the sheet.
    const [genStatus, setGenStatus] = useState<GenStatus>("idle");
    const [genStep, setGenStep] = useState(0);
    const [resultUrl, setResultUrl] = useState<string | null>(null);
    const [resultWarnings, setResultWarnings] = useState<string[]>([]);

    const pollRef = useRef<number | null>(null);
    function stopPolling() {
        if (pollRef.current) {
            window.clearInterval(pollRef.current);
            pollRef.current = null;
        }
    }
    useEffect(() => () => stopPolling(), []);

    const clearError = (key: keyof InputErrors) => setErrors((e) => ({ ...e, [key]: false }));

    // ── Sheet connect handlers ────────────────────────────────────────────
    async function pullMediaPlan(url: string) {
        setMediaPulling(true);
        setMatchData(null);
        setMediaTabPicker(null);
        try {
            let p = await readSheetTab(url, MEDIA_PLAN_PRIMARY_TAB);
            if (!p.ok && p.error === "tab_not_found") p = await readSheetTab(url, MEDIA_PLAN_FALLBACK_TAB);
            if (!p.ok) {
                if (p.error === "tab_not_found" && p.tabs.length > 0) {
                    // No standard media-plan tab — let the user pick the right one instead of failing.
                    setMediaTabPicker({ url, tabs: p.tabs });
                    showToast(
                        `No "${MEDIA_PLAN_PRIMARY_TAB}" or "${MEDIA_PLAN_FALLBACK_TAB}" tab — pick the tab with the media plan`,
                        true
                    );
                    return;
                }
                showToast(p.error || "Could not read sheet", true);
                return;
            }
            connectMediaPlanFromRead(url, p);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setMediaPulling(false);
        }
    }

    // Loads a user-chosen tab as the media plan when auto-detection failed. Validates
    // that the tab actually looks like a media plan (Media + a budget/volume column);
    // on failure the picker stays open so the user can try another tab.
    async function pullMediaPlanFromTab(url: string, tab: string) {
        setMediaPulling(true);
        try {
            const p = await readSheetTab(url, tab);
            if (!p.ok) {
                showToast(p.error === "tab_not_found" ? `Tab "${tab}" not found` : p.error || "Could not read sheet", true);
                return;
            }
            if (!looksLikeMediaPlan(p.rawRows)) {
                showToast(`"${tab}" doesn't look like a media plan — pick the tab with the "Media" and budget columns`, true);
                setMediaTabPicker({ url, tabs: p.tabs });
                return;
            }
            connectMediaPlanFromRead(url, p);
            setMediaTabPicker(null);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setMediaPulling(false);
        }
    }

    // Shared connect path for a successful media-plan read (auto-detected or user-picked).
    // A user-picked tab often has no separate "Estimates"/"Proposal" tab, so its own rows
    // seed the estimates source as a fallback for the backend's estimates-table pass.
    function connectMediaPlanFromRead(url: string, p: SheetReadResult) {
        w.connectMediaPlan({
            title: p.title ?? "",
            tab: p.tab,
            sheetId: p.sheetId ?? "",
            rows: p.rows,
            cols: p.cols,
            tabsCount: p.tabs.length,
            headers: p.headers,
            preview: p.preview,
            sheetRows: p.rawRows,
            audienceRows: [],
            estimatesRows: [],
            geoRows: [],
        });
        setErrors((e) => ({ ...e, sheet: false }));
        showToast(`${p.title} — ${p.rows} rows loaded`);
        void loadOptionalTabs(url, p.tabs, p.rawRows);
    }

    async function loadOptionalTabs(url: string, tabs: string[], estimatesFallback: Rows2D = []) {
        const loaded = (
            await Promise.all(
                tabs.map((tab) =>
                    readSheetTab(url, tab)
                        .then((r) => (r.ok && r.rawRows ? { tab, rows: r.rawRows } : null))
                        .catch((err) => {
                            console.warn(`${tab}:`, err);
                            return null;
                        })
                )
            )
        ).filter((t): t is { tab: string; rows: Rows2D } => t !== null);

        const byName = (name: string) =>
            loaded.find((t) => t.tab.trim().toLowerCase() === name.toLowerCase())?.rows;

        w.updateMediaPlanTabs({
            audienceRows: byName("Audience&Inventory") ?? [],
            estimatesRows: byName("Estimates") ?? byName("Proposal") ?? estimatesFallback,
            geoRows: buildWorkbookRows(loaded),
        });
    }

    async function pullElevate(url: string) {
        setElevatePulling(true);
        setMatchData(null);
        try {
            const b = await readSheetTab(url, "Basic");
            if (!b.ok) {
                showToast(
                    b.error === "tab_not_found" ? 'Tab "Basic" not found' : b.error || "Could not read sheet",
                    true
                );
                return;
            }
            w.connectElevate({
                title: b.title ?? "",
                tab: b.tab,
                sheetId: b.sheetId ?? "",
                rows: b.rows,
                cols: b.cols,
                tabsCount: b.tabs.length,
                headers: b.headers,
                preview: b.preview,
                adjRows: b.rawRows,
            });
            setErrors((e) => ({ ...e, adj: false }));
            showToast(`${b.title} — ${b.rows} rows loaded`);
            void detectDates(b.rawRows);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setElevatePulling(false);
        }
    }

    // Prefill the flight-date field from the raw-data ("Basic" tab) so the user can
    // confirm or correct it. Best-effort — on failure the user enters dates by hand.
    // An EOM deck reports one month, while the Elevate rows span the whole campaign, so
    // the detected range is clipped to the month that just closed (17 Jul–30 Sep read on
    // 1 Aug → 17–31 Jul).
    async function detectDates(adjRows: Rows2D) {
        try {
            const r = await detectDateRangeMutation.mutateAsync(adjRows);
            if (!r.start || !r.end) return;
            const detected = { start: r.start, end: r.end };
            const range = w.reportType === "EOM" ? eomWindow(detected) : detected;
            w.suggestDateWindow(range.start, range.end);
        } catch {
            /* detection is optional */
        }
    }

    // ── Matching ──────────────────────────────────────────────────────────
    function openMatch() {
        setMatchOpen(true);
        if (w.mediaPlan && w.elevate && !matchData && !matchMutation.isPending) runMatching();
    }
    function runMatching() {
        if (!w.mediaPlan || !w.elevate) {
            showToast("Connect both files before matching", true);
            return;
        }
        matchMutation.mutate(
            { bqRows: w.elevate.adjRows, planRows: w.mediaPlan.sheetRows },
            {
                onSuccess: (r) => {
                    setMatchData(r);
                    w.setMapping(r.mapping);
                },
                onError: (e) => showToast(e.message, true),
            }
        );
    }
    function confirmMatching() {
        if (!w.mapping || w.mapping.length === 0) {
            showToast("Run matching first", true);
            return;
        }
        if (w.activeMapping.length === 0) {
            showToast("Every plan line is excluded — restore at least one", true);
            return;
        }
        w.confirmMatch();
        setMatchOpen(false);
        const matched = w.activeMapping.filter((m) => m.lineItemId).length;
        const dropped = w.mapping.length - w.activeMapping.length;
        showToast(
            `Mapping confirmed — ${matched}/${w.activeMapping.length} tactics` +
                (dropped > 0 ? ` · ${dropped} line(s) excluded` : "")
        );
    }

    // ── Pacing ────────────────────────────────────────────────────────────
    function openPacing() {
        if (!w.mapping || w.mapping.length === 0) {
            showToast("Match the line items first", true);
            openMatch();
            return;
        }
        setPacingOpen(true);
    }
    function confirmPacing() {
        w.confirmPacing();
        setPacingOpen(false);
        showToast(`Pacing confirmed — ${w.activeMapping.length} tactics`);
    }

    // ── Step 2 → 3 gate ───────────────────────────────────────────────────
    function confirmInputs() {
        const errs: InputErrors = {
            brief: !w.brief.trim(),
            // EOM decks have no market-volume slide, so the field is hidden on step 2 and not required.
            marketVolume: w.reportType !== "EOM" && !w.marketVolume.trim(),
            sheet: !w.mediaPlan,
            adj: !w.elevate,
            dates: !(w.dateStart && w.dateEnd),
        };
        setErrors(errs);
        if (errs.brief || errs.marketVolume || errs.sheet || errs.adj || errs.dates) {
            showToast("Please complete all required fields", true);
            return;
        }
        if (!w.matchConfirmed) {
            showToast("Confirm the line-item mapping first", true);
            openMatch();
            return;
        }
        // EOM pacing figures come from the user, not the media plan — the deck can't be built without them.
        if (w.reportType === "EOM" && !w.pacingConfirmed) {
            showToast("Set the pacing & rates for every tactic first", true);
            openPacing();
            return;
        }
        setStep(2);
    }

    // ── Derived tactics + review rows ─────────────────────────────────────
    // Parsed over the whole media plan — excluded lines included — so repeated tactic names keep
    // lining up with their own plan row; the dropped entries are filtered out right after.
    const budgets = useMemo(
        () => extractTacticBudgets(w.mediaPlan?.sheetRows ?? null, (w.mapping ?? []).map((m) => m.tacticName)),
        [w.mediaPlan, w.mapping]
    );
    const activeBudgets = useMemo(
        () => keepActive(budgets, w.mapping ?? [], w.excludedTactics),
        [budgets, w.mapping, w.excludedTactics]
    );

    const tactics: TacticView[] = useMemo(
        () =>
            w.activeMapping.map((m, i) => ({
                tacticNum: m.tacticNum,
                name: m.tacticName,
                channel: m.expectedChannel ?? "",
                meta: budgetLine(activeBudgets[i] ?? null),
                on: breakdowns[m.tacticNum] ?? DEFAULT_BREAKDOWNS,
            })),
        [w.activeMapping, activeBudgets, breakdowns]
    );

    const reviewRows: ReviewRow[] = useMemo(
        () =>
            w.activeMapping.map((m, i) => {
                const s = summaryRows?.[i] ?? null;
                return {
                    tactic: m.tacticName,
                    lineId: m.lineItemId ?? null,
                    // The full-flight plan (spend and its matching unit) only exists once the backend
                    // has resolved it from the budget/rate the user entered while matching — the media
                    // plan's own Units/Unit Price columns are pre-fill defaults, not that entry, and lack
                    // the flight-length multiplier, so they are not a safe stand-in while this loads.
                    spendPlan: s?.spendPlan ?? null,
                    spendFact: s?.spendFact ?? null,
                    unitPlan: s?.unitPlan ?? null,
                    unitFact: s?.unitFact ?? null,
                };
            }),
        [w.activeMapping, summaryRows]
    );

    // Enabled sections per tactic, dropped to the ones the tactic's channel actually supports (Meta,
    // TikTok, Google Search and Performance Max have no publisher or device split).
    // Numbered like the payload mapping (1..N over the reported tactics), while the toggle state
    // stays keyed by the row's own plan number so restoring a line brings its toggles back with it.
    const breakdownSelections = useMemo(
        () =>
            w.activeMapping.map((m, i) => {
                const state = breakdowns[m.tacticNum] ?? DEFAULT_BREAKDOWNS;
                const allowed = allowedBreakdowns(m.tacticName, m.expectedChannel ?? "");
                return {
                    tacticNum: i + 1,
                    breakdowns: BREAKDOWNS.filter((b) => allowed.includes(b.id) && state[b.id]).map((b) => b.id),
                };
            }),
        [w.activeMapping, breakdowns]
    );

    // True when any tactic has at least one breakdown section enabled — those slides need the user to
    // fill the sheet's "Breakdowns" tab by hand, so Review Sheet warns and gates Confirm on it.
    const breakdownsEnabled = useMemo(
        () => breakdownSelections.some((s) => s.breakdowns.length > 0),
        [breakdownSelections]
    );

    function toggleBreakdown(tacticNum: number, id: BreakdownId) {
        setBreakdowns((prev) => {
            const cur = prev[tacticNum] ?? DEFAULT_BREAKDOWNS;
            return { ...prev, [tacticNum]: { ...cur, [id]: !cur[id] } };
        });
    }

    // ── Generation jobs (shared payload) ──────────────────────────────────
    function basePayload(): GenerateRequest {
        return {
            brief: w.brief,
            changeLog: w.changeLog,
            reportType: w.reportType,
            marketVolume: w.marketVolume,
            sheetRows: w.mediaPlan?.sheetRows ?? [],
            adjRows: w.elevate?.adjRows ?? [],
            audienceRows: w.mediaPlan?.audienceRows ?? [],
            estimatesRows: w.mediaPlan?.estimatesRows ?? [],
            geoRows: w.mediaPlan?.geoRows ?? [],
            lineItemMapping: w.mapping ? toPayloadMapping(w.activeMapping) : undefined,
            // Step-3 breakdown toggles → the backend clears the sections a tactic didn't enable on the
            // generated sheet's "Breakdowns" tab. One entry per mapped tactic (empty list = none enabled).
            breakdownSelections,
            // When off, the per-tactic dayparting/gender tokens are dashed instead of AI-estimated.
            estimateDaypartGender: w.estimateDaypartGender,
            bqSheetId: w.elevate?.sheetId,
            // Persisted for the admin history so reviewers can open the user's source sheets.
            mediaPlanUrl: sheetUrlFromId(w.mediaPlan?.sheetId),
            elevateUrl: sheetUrlFromId(w.elevate?.sheetId),
            dateFilter:
                w.dateStart && w.dateEnd
                    ? { mode: "RANGE", start: w.dateStart, end: w.dateEnd }
                    : { mode: "ALL" },
        };
    }

    // Reads the assembled sheet's summary table so the review step shows the plan/fact
    // figures the sheet actually carries — re-run to pick up manual edits made in Google
    // Sheets. Non-fatal on the initial (silent) load: the table falls back to the plan
    // figures parsed from the media plan. `announce` surfaces a toast for manual refreshes.
    async function loadSummary(url: string, announce = false) {
        setSummaryLoading(true);
        try {
            setSummaryRows(await readSheetSummary(url));
            if (announce) showToast("Values refreshed from the sheet");
        } catch (e) {
            console.warn("sheet summary:", e);
            if (announce) showToast(e instanceof Error ? e.message : "Refresh failed", true);
        } finally {
            setSummaryLoading(false);
        }
    }

    function refreshSummary() {
        if (sheetUrl && !summaryLoading) void loadSummary(sheetUrl, true);
    }

    // Build the collected Google Sheet (step 3 → 4).
    function buildSheet() {
        if (building) return;
        setBuilding(true);
        setSummaryRows(null);
        startReportJob({ ...basePayload(), target: "SHEET" })
            .then((jobId) => {
                pollRef.current = window.setInterval(async () => {
                    try {
                        const p = await fetchReportJob(jobId);
                        if (!p) return;
                        if (p.status === "done") {
                            stopPolling();
                            setBuilding(false);
                            setSheetUrl(p.slideUrl ?? null);
                            setStep(3);
                            showToast("Sheet assembled — review it");
                            if (p.slideUrl) void loadSummary(p.slideUrl);
                        } else if (p.status === "error") {
                            stopPolling();
                            setBuilding(false);
                            showToast(p.error ?? "Sheet build failed", true);
                        }
                    } catch {
                        /* transient poll error — keep polling */
                    }
                }, 1500);
            })
            .catch((e) => {
                setBuilding(false);
                showToast(e instanceof Error ? e.message : "Launch failed", true);
            });
    }

    // Generate the final report from the reviewed sheet (step 5).
    function generateReport() {
        if (genStatus !== "idle") return;
        setResultUrl(null);
        setResultWarnings([]);
        setGenStatus("running");
        setGenStep(0);
        // Step 2 builds strictly from the reviewed sheet, so the raw media-plan grids are not sent — the
        // backend reads every number back from the sheet. Only the brief, report type and sheet URL matter.
        startReportJob({
            ...basePayload(),
            sheetRows: [],
            adjRows: [],
            audienceRows: [],
            estimatesRows: [],
            geoRows: [],
            lineItemMapping: undefined,
            bqSheetId: undefined,
            target: "SLIDES_FROM_SHEET",
            sheetUrl: sheetUrl ?? undefined,
        })
            .then((jobId) => {
                pollRef.current = window.setInterval(async () => {
                    try {
                        const p = await fetchReportJob(jobId);
                        if (!p) return;
                        if (p.step > 0) setGenStep(p.step);
                        if (p.status === "done") {
                            stopPolling();
                            setGenStep(JOB_TOTAL);
                            setGenStatus("done");
                            setResultUrl(p.slideUrl ?? null);
                            setResultWarnings(p.warnings ?? []);
                            showToast("Report ready!");
                        } else if (p.status === "error") {
                            stopPolling();
                            setGenStatus("idle");
                            setGenStep(0);
                            showToast(p.error ?? "Generation failed", true);
                        }
                    } catch {
                        /* transient poll error — keep polling */
                    }
                }, 1500);
            })
            .catch((e) => {
                setGenStatus("idle");
                showToast(e instanceof Error ? e.message : "Launch failed", true);
            });
    }

    function runAgain() {
        stopPolling();
        setGenStatus("idle");
        setGenStep(0);
        setResultUrl(null);
    }

    // Count how many step checkpoints the job has passed — that's the number of finished
    // display stages. The stage whose checkpoint equals the current step is the running one.
    const stagesCompleted =
        genStatus === "done"
            ? GEN_STEP_CHECKPOINTS.length
            : GEN_STEP_CHECKPOINTS.filter((s) => s < genStep).length;

    return (
        <div className="rc-app">
            <Stepper
                active={step}
                maxReached={maxStep}
                locked={building || genStatus === "running"}
                onNavigate={setStep}
            />

            {step === 0 && <StepReportType onContinue={() => setStep(1)} />}

            {step === 1 && (
                <StepDataInputs
                    errors={errors}
                    mediaPulling={mediaPulling}
                    elevatePulling={elevatePulling}
                    datesDetecting={detectDateRangeMutation.isPending}
                    matchRunning={matchMutation.isPending}
                    onConnectMediaPlan={pullMediaPlan}
                    onConnectElevate={pullElevate}
                    mediaTabPicker={mediaTabPicker}
                    onPickMediaTab={pullMediaPlanFromTab}
                    onDismissMediaTabPicker={() => setMediaTabPicker(null)}
                    onDisconnectMediaPlan={() => {
                        w.disconnectMediaPlan();
                        setMatchData(null);
                        setMediaTabPicker(null);
                    }}
                    onDisconnectElevate={() => {
                        w.disconnectElevate();
                        setMatchData(null);
                    }}
                    onOpenMatch={openMatch}
                    onOpenPacing={openPacing}
                    onConfirm={confirmInputs}
                    onBack={() => setStep(0)}
                    clearError={clearError}
                />
            )}

            {step === 2 && (
                <StepBreakdowns
                    tactics={tactics}
                    building={building}
                    sheetBuilt={sheetUrl !== null}
                    onToggle={toggleBreakdown}
                    onBuild={buildSheet}
                    onContinue={() => setStep(3)}
                    onBack={() => setStep(1)}
                />
            )}

            {step === 3 && (
                <StepReviewSheet
                    reportType={w.reportType}
                    sheetUrl={sheetUrl}
                    rows={reviewRows}
                    refreshing={summaryLoading}
                    breakdownsEnabled={breakdownsEnabled}
                    onRefresh={refreshSummary}
                    onConfirm={() => setStep(4)}
                    onBack={() => setStep(2)}
                />
            )}

            {step === 4 && (
                <StepGenerate
                    reportType={w.reportType}
                    status={genStatus}
                    completed={stagesCompleted}
                    resultUrl={resultUrl}
                    warnings={resultWarnings}
                    onGenerate={generateReport}
                    onRunAgain={runAgain}
                    onBack={() => setStep(3)}
                />
            )}

            <MatchModal
                open={matchOpen}
                matchData={matchData}
                running={matchMutation.isPending}
                onClose={() => setMatchOpen(false)}
                onRun={runMatching}
                onConfirm={confirmMatching}
            />

            <PacingModal open={pacingOpen} onClose={() => setPacingOpen(false)} onConfirm={confirmPacing} />

            {building && (
                <div className="rc-overlay">
                    <div className="rc-overlay__card">
                        <div className="rc-overlay__spinner" />
                        <div className="rc-overlay__title">Assembling your sheet…</div>
                        <div className="rc-overlay__sub">
                            Reading the sources, matching line items and collecting every tactic into one Google
                            Sheet.
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
