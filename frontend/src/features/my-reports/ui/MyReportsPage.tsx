import { useNavigate } from "react-router-dom";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import type { ReportSummary } from "@/shared/api/types";
import { useMyReports } from "../api/useMyReports";
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
    return [when, statusLabel(r.status)].filter(Boolean).join(" · ");
}

/** Screen 6 — the signed-in user's minimal report history. */
export function MyReportsPage() {
    const navigate = useNavigate();
    const { data, isLoading, isError, error } = useMyReports();

    const reports = data?.reports ?? [];

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
                                    <span className={`mr-badge ${typeModifier(r.type)}`}>
                                        {(r.type ?? "REP").toUpperCase()}
                                    </span>
                                    <div className="mr__meta">
                                        <div className="mr__name">{r.title}</div>
                                        <div className="mr__sub">{reportMeta(r)}</div>
                                    </div>
                                </div>
                                <div className="mr__actions">
                                    <button
                                        type="button"
                                        className="mr__open"
                                        disabled={!r.slideUrl}
                                        onClick={() => r.slideUrl && window.open(r.slideUrl, "_blank", "noopener")}
                                    >
                                        Open report →
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
