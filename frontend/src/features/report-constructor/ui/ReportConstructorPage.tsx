import { useEffect, useState } from "react";
import { useClerk, useUser } from "@clerk/clerk-react";
import { readSheetTab } from "@/shared/api/sheets";
import type { LineItemMatchResult, PreviewResult, ReportType } from "@/shared/api/types";
import { WizardProvider, useWizard } from "@/shared/wizard/WizardContext";
import { useGoogleStatus } from "../api/useGoogleStatus";
import { useMatchLineItems } from "../api/useMatchLineItems";
import { usePreviewPlaceholders } from "../api/usePreviewPlaceholders";
import { useCreateReportJob, useReportJob } from "../api/useReportJob";
import { GeneratingOverlay } from "./GeneratingOverlay";
import { MatchModal } from "./MatchModal";
import { PreviewPanel } from "./PreviewPanel";
import { Sidebar } from "./Sidebar";
import { SheetCard } from "./SheetCard";
import { ToastProvider, useToast } from "./ToastContext";
import { IconCheck, IconChevron, IconLink2, IconLogo } from "./icons";
import "./report-constructor.css";

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
    const google = useGoogleStatus();

    const matchMutation = useMatchLineItems();
    const previewMutation = usePreviewPlaceholders();
    const createJob = useCreateReportJob();

    const [mediaPulling, setMediaPulling] = useState(false);
    const [elevatePulling, setElevatePulling] = useState(false);
    const [req, setReq] = useState({ brief: false, sheet: false, adj: false });
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [activeStep, setActiveStep] = useState(0);

    const [matchOpen, setMatchOpen] = useState(false);
    const [matchData, setMatchData] = useState<LineItemMatchResult | null>(null);

    const [previewData, setPreviewData] = useState<PreviewResult | null>(null);
    const [previewVisible, setPreviewVisible] = useState(false);

    const [jobId, setJobId] = useState<number | null>(null);
    const job = useReportJob(jobId);
    const done = job.data?.status === "done";
    const generating = jobId != null && !done && job.data?.status !== "error";
    const resultUrl = done ? job.data?.slideUrl ?? "" : null;

    // Job error → toast + reset.
    useEffect(() => {
        if (jobId == null) return;
        if (job.data?.status === "error") {
            showToast(job.data.error ?? "Generation failed", true);
            setJobId(null);
        } else if (job.isError) {
            showToast(job.error?.message ?? "Generation failed", true);
            setJobId(null);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [job.data?.status, job.isError, jobId]);

    // Scroll-spy for the sticky stepper.
    useEffect(() => {
        function onScroll() {
            ["s1", "s2", "s3"].forEach((id, i) => {
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
            void loadOptionalTabs(url);
        } catch (e) {
            showToast(e instanceof Error ? e.message : "Could not read sheet", true);
        } finally {
            setMediaPulling(false);
        }
    }

    function loadOptionalTabs(url: string) {
        readSheetTab(url, "Audience&Inventory")
            .then((r) => {
                if (r.ok && r.rawRows) {
                    w.updateMediaPlanTabs({ audienceRows: r.rawRows });
                    showToast(`Audience&Inventory tab loaded (${r.rows} rows)`);
                }
            })
            .catch((err) => console.warn("Audience&Inventory:", err));
        readSheetTab(url, "Estimates")
            .then(async (r) => {
                if (r.ok && r.rawRows) {
                    w.updateMediaPlanTabs({ estimatesRows: r.rawRows });
                    showToast(`Estimates tab loaded (${r.rows} rows)`);
                    return;
                }
                if (r.error === "tab_not_found") {
                    const fb = await readSheetTab(url, "Proposal").catch(() => null);
                    if (fb && fb.ok) {
                        w.updateMediaPlanTabs({ estimatesRows: fb.rawRows });
                        showToast(`Plan data loaded from Proposal tab (${fb.rows} rows)`);
                    }
                }
            })
            .catch((err) => console.warn("Estimates:", err));
        readSheetTab(url, "Geo")
            .then((r) => {
                if (r.ok && r.rawRows) {
                    w.updateMediaPlanTabs({ geoRows: r.rawRows });
                    showToast(`Geo tab loaded (${r.rows} rows)`);
                }
            })
            .catch((err) => console.warn("Geo:", err));
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
            showToast("Подключи оба файла перед маппингом", true);
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
            showToast("Сначала запусти маппинг", true);
            return;
        }
        w.confirmMatch();
        setMatchOpen(false);
        const matched = w.mapping.filter((m) => m.lineItemId).length;
        showToast(`Маппинг подтверждён — ${matched}/${w.mapping.length} тактик`);
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
        const errs = { brief: !w.brief.trim(), sheet: !w.mediaPlan, adj: !w.elevate };
        if (errs.brief || errs.sheet || errs.adj) {
            setReq(errs);
            showToast("Please complete all three sections", true);
            return;
        }
        createJob.mutate(
            {
                brief: w.brief,
                reportType: w.reportType,
                sheetRows: w.mediaPlan!.sheetRows,
                adjRows: w.elevate!.adjRows,
                audienceRows: w.mediaPlan!.audienceRows,
                estimatesRows: w.mediaPlan!.estimatesRows,
                geoRows: w.mediaPlan!.geoRows,
                lineItemMapping: w.mapping ?? undefined,
                bqSheetId: w.elevate!.sheetId,
            },
            {
                onSuccess: (id) => setJobId(id),
                onError: (e) => showToast(e.message, true),
            }
        );
    }

    function clearAll() {
        w.setBrief("");
        w.setReportType("EOC");
        w.disconnectMediaPlan();
        w.disconnectElevate();
        setMatchData(null);
        setPreviewData(null);
        setPreviewVisible(false);
        setJobId(null);
        setReq({ brief: false, sheet: false, adj: false });
    }

    // ── Derived UI ────────────────────────────────────────────────────────
    const briefLen = w.brief.length;
    const steps = [
        { label: "Campaign Brief", done: w.brief.trim().length > 0, href: "#s1" },
        { label: "Media Plan", done: !!w.mediaPlan, href: "#s2" },
        { label: "Elevate Dashboard", done: !!w.elevate, href: "#s3" },
    ];
    const bothConnected = !!w.mediaPlan && !!w.elevate;
    const matched = (w.mapping ?? []).filter((m) => m.lineItemId).length;
    const matchTotal = (w.mapping ?? []).length;
    const mockMode = google.data?.mockMode ?? false;

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
                        ? "Line Items — подключи оба файла"
                        : w.matchConfirmed
                          ? "✓ Маппинг подтверждён"
                          : "Line Items — требует проверки"}
                </div>
                <div className="match-status-banner-sub">
                    {!bothConnected
                        ? "Сначала загрузи Media Plan и BQ-выгрузку"
                        : w.matchConfirmed
                          ? `${matched} из ${matchTotal} тактик привязаны к Line Item ID`
                          : "Запусти маппинг и подтверди перед генерацией"}
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
                        {user?.primaryEmailAddress?.emailAddress ?? ""}
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
                <div className="form-area" style={{ display: generating ? "none" : "flex" }}>
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

                    {/* 02 Media Plan */}
                    <SheetCard
                        num="02"
                        id="s2"
                        title="Media Plan"
                        desc="Channel allocations, budgets, targeting — Google Sheets link"
                        hint={
                            <>
                                <strong>Tip:</strong> Убедись что таблица доступна service-account&apos;у (share или
                                «Anyone with the link»).
                            </>
                        }
                        authBanner={
                            <div className="auth-banner ok">
                                <IconCheck size={15} />
                                <span>
                                    {mockMode
                                        ? "Google в stub-режиме — данные будут замоканы (подключи service account для реальных данных)"
                                        : `Google подключён (service account)${
                                              google.data?.email ? ` · ${google.data.email}` : ""
                                          }`}
                                </span>
                            </div>
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

                    {/* 03 Elevate */}
                    <SheetCard
                        num="03"
                        id="s3"
                        title="Elevate Dashboard Datasheet"
                        desc='BigQuery export — actual performance data, tab "Basic"'
                        hint={
                            <>
                                <strong>Tip:</strong> Вставь ссылку на Google Sheets с выгрузкой из BigQuery. Будет
                                прочитана вкладка <strong>Basic</strong>.
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

                    {/* 04 Settings */}
                    <div className={`settings-card${settingsOpen ? " open" : ""}`} id="s4">
                        <button className="settings-toggle" onClick={() => setSettingsOpen((v) => !v)}>
                            <div className="settings-toggle-left">
                                <div className="settings-toggle-num">04</div>
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
                    onPreview={previewPlaceholders}
                    onGenerate={generate}
                    onClear={clearAll}
                />
            </div>

            {generating && (
                <GeneratingOverlay
                    step={job.data?.step ?? 1}
                    total={job.data?.total ?? 7}
                    label={job.data?.label ?? "Queued…"}
                />
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
