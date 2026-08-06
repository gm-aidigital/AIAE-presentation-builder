import { useNavigate } from "react-router-dom";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import type { ReportSummary } from "@/shared/api/types";
import { useDismissReport, useMyReports } from "../api/useMyReports";
import "./my-reports.css";

const dateFmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });

/** Maps a report-type code to its BEM modifier class for badge coloring. */
function typeModifier(type: string | undefined): string {
    switch ((type ?? "").toUpperCase()) {
        case "EOM":
            return "mr-badge--eom";
        case "EOC":
            return "mr-badge--eoc";
        case "EXCEL":
            return "mr-badge--excel";
        case "AGENDA":
            return "mr-badge--agenda";
        default:
            return "mr-badge--eom";
    }
}

/** Human-readable status word for the row meta line. */
function statusLabel(status: string): string {
    switch (status) {
        case "done":
            return "Ready";
        case "running":
        case "queued":
            return "In progress";
        case "error":
            return "Failed";
        default:
            return status;
    }
}

function reportMeta(r: ReportSummary): string {
    const when = r.createdAt ? dateFmt.format(new Date(r.createdAt)) : "";
    // A draft's job status is "done" — the sheet build finished. What is unfinished is the report,
    // so the row says so instead of claiming the report is ready.
    return [when, r.draft ? "Sheet ready — not generated yet" : statusLabel(r.status)]
        .filter(Boolean)
        .join(" · ");
}

/** Screen 6 — the signed-in user's minimal report history. */
export function MyReportsPage() {
    const navigate = useNavigate();
    const { data, isLoading, isError, error } = useMyReports();
    const dismiss = useDismissReport();

    const reports = data?.reports ?? [];

    // The workbook stays in Drive and the job stays in the history — only the offer to continue
    // goes away — so the confirm says that rather than implying anything is deleted.
    function discardDraft(r: ReportSummary) {
        const ok = window.confirm(
            `Stop offering “${r.fileName ?? r.title}” as a draft?\n\n` +
                "The Google Sheet itself is not deleted — you can still open it from Drive."
        );
        if (ok) dismiss.mutate(r.jobId);
    }

    return (
        <div className="mr">
            <div className="mr__card">
                <div className="mr__head">
                    <div>
                        <h1 className="mr__title">My reports</h1>
                        <p className="mr__subtitle">
                            Your report history. Total: <b>{data?.total ?? 0}</b>
                        </p>
                    </div>
                    <button type="button" className="mr__new" onClick={() => navigate("/reports/new")}>
                        + New report
                    </button>
                </div>

                {isLoading && <LoadingBlock label="Loading your reports…" />}
                {isError && <ErrorAlert message={error instanceof Error ? error.message : "Could not load reports"} />}
                {!isLoading && !isError && reports.length === 0 && (
                    <EmptyState message="No reports yet — start one with “+ New report”." />
                )}

                {!isLoading && !isError && reports.length > 0 && (
                    <div className="mr__list">
                        {reports.map((r) => (
                            <div key={r.jobId} className="mr__row">
                                <div className="mr__left">
                                    <span
                                        className={`mr-badge ${
                                            r.draft ? "mr-badge--draft" : typeModifier(r.type)
                                        }`}
                                    >
                                        {r.draft ? "Draft" : (r.type ?? "REP").toUpperCase()}
                                    </span>
                                    <div className="mr__meta">
                                        <div className="mr__name">{r.fileName ?? r.title}</div>
                                        <div className="mr__sub">{reportMeta(r)}</div>
                                    </div>
                                </div>
                                <div className="mr__actions">
                                    {r.sheetUrl && (
                                        <a
                                            className="mr__sheet"
                                            href={r.sheetUrl}
                                            target="_blank"
                                            rel="noreferrer"
                                        >
                                            View sheet ↗
                                        </a>
                                    )}
                                    {r.draft ? (
                                        <>
                                            <button
                                                type="button"
                                                className="mr__sheet mr__discard"
                                                disabled={dismiss.isPending}
                                                onClick={() => discardDraft(r)}
                                            >
                                                Discard
                                            </button>
                                            <button
                                                type="button"
                                                className="mr__open"
                                                onClick={() => navigate(`/reports/new?resume=${r.jobId}`)}
                                            >
                                                Continue →
                                            </button>
                                        </>
                                    ) : (
                                        <button
                                            type="button"
                                            className="mr__open"
                                            disabled={!r.slideUrl}
                                            onClick={() => r.slideUrl && window.open(r.slideUrl, "_blank", "noopener")}
                                        >
                                            Open report →
                                        </button>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
