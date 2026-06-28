import { useEffect, useRef, useState } from "react";
import { useClerk, useUser } from "@clerk/clerk-react";
import { readSheetTab } from "@/shared/api/sheets";
import type { LineItemMatchResult, PreviewResult, ReportType, Rows2D } from "@/shared/api/types";
import { WizardProvider, useWizard } from "@/shared/wizard/WizardContext";
import { useMatchLineItems } from "../api/useMatchLineItems";
import { usePreviewPlaceholders } from "../api/usePreviewPlaceholders";
import { fetchReportJob, startReportJob } from "../api/useReportJob";
import { GeneratingOverlay } from "./GeneratingOverlay";
import { MatchModal } from "./MatchModal";
import { PreviewPanel } from "./PreviewPanel";
import { Sidebar } from "./Sidebar";
import { SheetCard } from "./SheetCard";
import { ToastProvider, useToast } from "./ToastContext";
import { IconCheck, IconChevron, IconLink2, IconLogo } from "./icons";
import "./report-constructor.css";

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
    const { user } = useUser();
    const { signOut } = useClerk();

    const matchMutation = useMatchLineItems();
    const previewMutation = usePreviewPlaceholders();

    const [mediaPulling, setMediaPulling] = useState(false);
    const [elevatePulling, setElevatePulling] = useState(false);
    const [req, setReq] = useState({ brief: false, sheet: false, adj: false, marketVolume: false });
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [activeStep, setActiveStep] = useState(0);

    const [matchOpen, setMatchOpen] = useState(false);
    const [matchData, setMatchData] = useState<LineItemMatchResult | null>(null);

    const [previewData, setPreviewData] = useState<PreviewResult | null>(null);
    const [previewVisible, setPreviewVisible] = useState(false);

    // Imperative generation state (stable — never derived from a live query, so
    // polling/retries can't make the overlay flicker). Mirrors the POC.
    const [generating, setGenerating] = useState(false);
    const [progress, setProgress] = useState({ step: 0, total: 7, label: "" });
    const [resultUrl, setResultUrl] = useState<string | null>(null);
    const pollRef = useRef<number | null>(null);

    function stopPolling() {
        if (pollRef.current) {
            window.clearInterval(pollRef.current);
            pollRef.current = null;
        }
    }
    useEffect(() => () => stopPolling(), []);

    // Scroll-spy for the sticky stepper.
    useEffect(() => {
        function onScroll() {
            ["s1", "s2", "s3", "s4"].forEach((id, i) => {
                const el = document.getElementById(id);
                if (!el) return;
                const r = el.getBoundingClientRect();
                if (r.top < 180 && r.bottom > 0) setActiveStep(i);
            });
        }
        window.addEventListener("scroll", onScroll);
        return () => window.removeEventListener("scroll", onScroll);
    }, []);

    // ── Sheet connect handlers ────────────────────────────────────────────
    async function pullMediaPlan(url: string) {
        setMediaPulling(true);
        setMatchData(null);
        try {
            const p = await readSheetTab(url, "Proposal");
            if (!p.ok) {
                showToast(
                    p.error === "tab_not_found" ? 'Tab "Proposal" not found' : p.error || "Could not read sheet",
                    true
                );
                return;
            }
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
            setReq((r) => ({ ...r, sheet: false }));
            showToast(`${p.title} — ${p.rows} rows loaded`);
            void loadOptionalTabs(url, p.tabs);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setMediaPulling(false);
        }
    }

    async function loadOptionalTabs(url: string, tabs: string[]) {
        // Read every tab in the workbook once. The Audience and Estimates tabs feed
        // their own resolvers; the full set is bundled into geoRows so Claude can
        // find the geo targeting wherever it lives (its tab name varies per file).
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
            estimatesRows: byName("Estimates") ?? byName("Proposal") ?? [],
            geoRows: buildWorkbookRows(loaded),
        });
        showToast(`Loaded ${loaded.length} tab${loaded.length === 1 ? "" : "s"} for analysis`);
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
            setReq((r) => ({ ...r, adj: false }));
            showToast(`${b.title} — ${b.rows} rows loaded`);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setElevatePulling(false);
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
        w.confirmMatch();
        setMatchOpen(false);
        const matched = w.mapping.filter((m) => m.lineItemId).length;
        showToast(`Mapping confirmed — ${matched}/${w.mapping.length} tactics`);
    }

    // ── Preview ───────────────────────────────────────────────────────────
    function previewPlaceholders() {
        if (!w.mediaPlan && !w.elevate) {
            showToast("Connect at least one sheet first", true);
            return;
        }
        previewMutation.mutate(
            {
                brief: w.brief,
                reportType: w.reportType,
                marketVolume: w.marketVolume,
                sheetRows: w.mediaPlan?.sheetRows ?? [],
                adjRows: w.elevate?.adjRows ?? [],
                audienceRows: w.mediaPlan?.audienceRows ?? [],
                estimatesRows: w.mediaPlan?.estimatesRows ?? [],
                geoRows: w.mediaPlan?.geoRows ?? [],
                lineItemMapping: w.mapping ?? undefined,
            },
            {
                onSuccess: (d) => {
                    setPreviewData(d);
                    setPreviewVisible(true);
                    requestAnimationFrame(() =>
                        document.getElementById("preview-panel")?.scrollIntoView({ behavior: "smooth", block: "start" })
                    );
                },
                onError: (e) => showToast(e.message, true),
            }
        );
    }

    // ── Generate ──────────────────────────────────────────────────────────
    function generate() {
        const errs = {
            brief: !w.brief.trim(),
            sheet: !w.mediaPlan,
            adj: !w.elevate,
            marketVolume: !w.marketVolume.trim(),
        };
        if (errs.brief || errs.sheet || errs.adj || errs.marketVolume) {
            setReq(errs);
            showToast("Please complete all required sections", true);
            return;
        }
        if (!w.mediaPlan || !w.elevate) return;
        setReq({ brief: false, sheet: false, adj: false, marketVolume: false });
        setResultUrl(null);
        setGenerating(true);
        setProgress({ step: 0, total: 7, label: "Starting…" });
        startReportJob({
            brief: w.brief,
            reportType: w.reportType,
            marketVolume: w.marketVolume,
            sheetRows: w.mediaPlan.sheetRows,
            adjRows: w.elevate.adjRows,
            audienceRows: w.mediaPlan.audienceRows,
            estimatesRows: w.mediaPlan.estimatesRows,
            geoRows: w.mediaPlan.geoRows,
            lineItemMapping: w.mapping ?? undefined,
            bqSheetId: w.elevate.sheetId,
        })
            .then((jobId) => {
                setProgress({ step: 1, total: 7, label: "Queued…" });
                let polls = 0;
                pollRef.current = window.setInterval(async () => {
                    polls += 1;
                    if (polls > 240) {
                        stopPolling();
                        setGenerating(false);
                        showToast("Generation timeout — please try again", true);
                        return;
                    }
                    try {
                        const p = await fetchReportJob(jobId);
                        if (!p) return; // transient miss — keep polling
                        if (p.step > 0) {
                            setProgress({ step: p.step, total: p.total || 7, label: p.label ?? "Working…" });
                        }
                        if (p.status === "done") {
                            stopPolling();
                            setProgress({ step: 7, total: 7, label: "Done!" });
                            setResultUrl(p.slideUrl ?? "");
                            setGenerating(false);
                            showToast("Presentation ready!");
                        } else if (p.status === "error") {
                            stopPolling();
                            setGenerating(false);
                            showToast(p.error ?? "Generation failed", true);
                        }
                    } catch {
                        /* transient poll error — keep polling */
                    }
                }, 1500);
            })
            .catch((e) => {
                setGenerating(false);
                showToast(e instanceof Error ? e.message : "Launch failed", true);
            });
    }

    function clearAll() {
        w.setBrief("");
        w.setReportType("EOC");
        w.setMarketVolume("");
        w.disconnectMediaPlan();
        w.disconnectElevate();
        setMatchData(null);
        setPreviewData(null);
        setPreviewVisible(false);
        stopPolling();
        setGenerating(false);
        setResultUrl(null);
        setProgress({ step: 0, total: 7, label: "" });
        setReq({ brief: false, sheet: false, adj: false, marketVolume: false });
    }

    // ── Derived UI ────────────────────────────────────────────────────────
    const briefLen = w.brief.length;
    const hasMarketVolume = w.marketVolume.trim().length > 0;
    const steps = [
        { label: "Campaign Brief", done: w.brief.trim().length > 0, href: "#s1" },
        { label: "Market Volume", done: hasMarketVolume, href: "#s2" },
        { label: "Media Plan", done: !!w.mediaPlan, href: "#s3" },
        { label: "Elevate Dashboard", done: !!w.elevate, href: "#s4" },
    ];
    const bothConnected = !!w.mediaPlan && !!w.elevate;
    const matched = (w.mapping ?? []).filter((m) => m.lineItemId).length;
    const matchTotal = (w.mapping ?? []).length;

    const matchBanner = (
        <div
            className={
                "match-status-banner " +
                (bothConnected ? (w.matchConfirmed ? "visible" : "pending visible") : "pending")
            }
        >
            <div className="match-status-banner-icon">
                <IconLink2 size={13} />
            </div>
            <div className="match-status-banner-text">
                <div className="match-status-banner-label">
                    {!bothConnected
                        ? "Line Items — connect both files"
                        : w.matchConfirmed
                          ? "✓ Mapping confirmed"
                          : "Line Items — needs review"}
                </div>
                <div className="match-status-banner-sub">
                    {!bothConnected
                        ? "First load the Media Plan and BQ export"
                        : w.matchConfirmed
                          ? `${matched} of ${matchTotal} tactics linked to a Line Item ID`
                          : "Run matching and confirm before generating"}
                </div>
            </div>
            <button className="btn-match-open" disabled={!bothConnected} onClick={openMatch}>
                {w.matchConfirmed ? "Edit →" : "Match →"}
            </button>
        </div>
    );

    return (
        <>
            <nav>
                <a className="nav-logo" href="#">
                    <div className="nav-logo-mark">
                        <IconLogo />
                    </div>
                    <span className="nav-logo-text">
                        AI Digital <span>Studio</span>
                    </span>
                </a>
                <div className="nav-right">
                    <div className="nav-badge">Report Constructor</div>
                    <div className="nav-user">
                        <span className="nav-user-dot" />
                        {user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? ""}
                    </div>
                    <button className="btn-signout" onClick={() => signOut()}>
                        Sign out
                    </button>
                </div>
            </nav>

            <div className="hero">
                <div className="hero-dots" />
                <div className="hero-inner">
                    <div className="hero-tag">
                        <span className="hero-tag-dot" />
                        Campaign Report Generator
                    </div>
                    <h1>
                        One button.
                        <br />
                        <em>Ready presentation.</em>
                    </h1>
                    <p className="hero-sub">
                        Connect your Google Sheets — Claude reads the data, applies your brief, and builds a
                        structured presentation.
                    </p>
                </div>
            </div>

            <div className="progress-wrap">
                <div className="progress-inner">
                    {steps.map((s, i) => (
                        <a
                            key={s.label}
                            className={`progress-step${activeStep === i ? " active" : ""}${s.done ? " done" : ""}`}
                            href={s.href}
                        >
                            <div className="step-circle">
                                <span className="step-num">{i + 1}</span>
                                <span className="step-check">
                                    <IconCheck size={11} />
                                </span>
                            </div>
                            <span className="step-label">{s.label}</span>
                        </a>
                    ))}
                </div>
            </div>

            <div className="page-body">
                <div className="form-area">
                    {/* 01 Brief */}
                    <div className="section-card" id="s1">
                        <div className="card-header">
                            <div className="card-num">01</div>
                            <div>
                                <div className="card-title">Campaign Brief</div>
                                <div className="card-desc">
                                    Core context — client, goals, audience, KPIs, flight dates
                                </div>
                            </div>
                        </div>
                        <div className="card-body">
                            <div className="textarea-wrap">
                                <textarea
                                    placeholder="Describe the campaign…&#10;&#10;Include: client name, campaign goals, target audience, budget, flight dates, KPIs, channels used."
                                    value={w.brief}
                                    onChange={(e) => {
                                        w.setBrief(e.target.value);
                                        setReq((r) => ({ ...r, brief: false }));
                                    }}
                                />
                                <span className={`char-count${briefLen > 0 ? " active" : ""}`}>
                                    {briefLen.toLocaleString()} chars
                                </span>
                            </div>
                            <div className={`field-error${req.brief ? " visible" : ""}`}>
                                Campaign brief is required.
                            </div>
                        </div>
                    </div>

                    {/* 02 Market Volume */}
                    <div className="section-card" id="s2">
                        <div className="card-header">
                            <div className="card-num">02</div>
                            <div>
                                <div className="card-title">Market Volume</div>
                                <div className="card-desc">Maximum addressable audience size — required</div>
                            </div>
                        </div>
                        <div className="card-body">
                            <div className="sheets-hint">
                                Open <strong>DV360</strong>, select your advertiser, and enter your socio-demographic
                                and audience targeting. Read off the <strong>maximum audience volume</strong> the
                                estimate reports, and paste that number here. It fills the{" "}
                                <strong>{"{{market volume}}"}</strong> placeholder, shortened automatically (e.g.
                                74,542 → 74k, 1,234,567 → 1.2M).
                            </div>
                            <input
                                className="input-field"
                                type="text"
                                inputMode="numeric"
                                placeholder="e.g. 1 234 567"
                                value={w.marketVolume}
                                onChange={(e) => {
                                    w.setMarketVolume(e.target.value);
                                    setReq((r) => ({ ...r, marketVolume: false }));
                                }}
                            />
                            <div className={`field-error${req.marketVolume ? " visible" : ""}`}>
                                Market volume is required.
                            </div>
                        </div>
                    </div>

                    {/* 03 Media Plan */}
                    <SheetCard
                        num="03"
                        id="s3"
                        title="Media Plan"
                        desc="Channel allocations, budgets, targeting — Google Sheets link"
                        hint={
                            <>
                                <strong>Tip:</strong> Make sure the sheet is accessible to the service account (share
                                it or set it to "Anyone with the link").
                            </>
                        }
                        sheet={w.mediaPlan}
                        pulling={mediaPulling}
                        requiredError={req.sheet}
                        requiredMsg="Media plan is required."
                        onPull={pullMediaPlan}
                        onDisconnect={() => {
                            w.disconnectMediaPlan();
                            setMatchData(null);
                        }}
                    />

                    {/* 04 Elevate */}
                    <SheetCard
                        num="04"
                        id="s4"
                        title="Elevate Dashboard Datasheet"
                        desc='BigQuery export — actual performance data, tab "Basic"'
                        hint={
                            <>
                                <strong>Tip:</strong> Paste the Google Sheets link with the BigQuery export. The{" "}
                                <strong>Basic</strong> tab will be read.
                            </>
                        }
                        sheet={w.elevate}
                        pulling={elevatePulling}
                        requiredError={req.adj}
                        requiredMsg="Elevate Dashboard Datasheet is required."
                        footer={matchBanner}
                        onPull={pullElevate}
                        onDisconnect={() => {
                            w.disconnectElevate();
                            setMatchData(null);
                        }}
                    />

                    {/* 05 Settings */}
                    <div className={`settings-card${settingsOpen ? " open" : ""}`} id="s5">
                        <button className="settings-toggle" onClick={() => setSettingsOpen((v) => !v)}>
                            <div className="settings-toggle-left">
                                <div className="settings-toggle-num">05</div>
                                <div>
                                    <div className="settings-toggle-title">Additional Settings</div>
                                    <div className="settings-toggle-desc">Report type and output options</div>
                                </div>
                            </div>
                            <div className="settings-toggle-right">
                                <span className="settings-active-badge">{w.reportType}</span>
                                <IconChevron className="settings-chevron" size={15} />
                            </div>
                        </button>
                        <div className="settings-body">
                            <div className="settings-body-inner">
                                <div className="setting-row">
                                    <div className="setting-label">
                                        Report Type
                                        <span className="setting-label-sub">— select the reporting period</span>
                                    </div>
                                    <div className="report-type-group">
                                        {(["EOC", "EOM"] as ReportType[]).map((t) => (
                                            <button
                                                key={t}
                                                className={`report-type-btn${w.reportType === t ? " active" : ""}`}
                                                onClick={() => w.setReportType(t)}
                                            >
                                                <span className="report-type-badge">{t}</span>
                                                <span className="report-type-sub">
                                                    {t === "EOC" ? "End of Campaign" : "End of Month"}
                                                </span>
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <Sidebar
                    resultUrl={resultUrl}
                    previewLoading={previewMutation.isPending}
                    generating={generating}
                    onPreview={previewPlaceholders}
                    onGenerate={generate}
                    onClear={clearAll}
                />
            </div>

            {generating && (
                <GeneratingOverlay step={progress.step} total={progress.total} label={progress.label} />
            )}

            {previewVisible && previewData && (
                <PreviewPanel data={previewData} onClose={() => setPreviewVisible(false)} />
            )}

            <MatchModal
                open={matchOpen}
                matchData={matchData}
                running={matchMutation.isPending}
                onClose={() => setMatchOpen(false)}
                onRun={runMatching}
                onConfirm={confirmMatching}
            />
        </>
    );
}
