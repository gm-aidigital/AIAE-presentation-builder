import type { ReportType } from "@/shared/api/types";
import { IconCheck, IconExternalLink, IconInfo } from "./icons";

const STAGES = [
    "Reading Sheets data",
    "Matching line items",
    "Building breakdowns",
    "Filling Slides template",
    "Finalizing report",
];

export type GenStatus = "idle" | "running" | "done";

interface Props {
    reportType: ReportType;
    status: GenStatus;
    /** Number of stages completed (0..5). */
    completed: number;
    resultUrl: string | null;
    /** Non-fatal warnings (e.g. per-chart build failures); the report still completes. */
    warnings: string[];
    onGenerate(): void;
    onRunAgain(): void;
}

/** Screen 5 — trigger generation and watch per-stage status. */
export function StepGenerate({ reportType, status, completed, resultUrl, warnings, onGenerate, onRunAgain }: Props) {
    const isDone = status === "done";
    const btnLabel = status === "idle" ? "Generate report" : status === "running" ? "Generating…" : "Report ready";

    return (
        <div className="rc-content rc-content--center">
            <div className="rc-gen-head">
                <div className="rc-gen-head__title">Ready to generate</div>
                <p className="rc-gen-head__sub">
                    All inputs confirmed and breakdowns selected. This builds your{" "}
                    <b>{reportType}</b> report from the collected sheet.
                </p>
                <button
                    type="button"
                    className={`rc-btn rc-btn--gen${status === "running" ? " rc-btn--gen-running" : ""}`}
                    disabled={status !== "idle"}
                    onClick={onGenerate}
                >
                    {btnLabel}
                </button>
            </div>

            <div className="rc-stages">
                {STAGES.map((label, i) => {
                    const state = isDone || i < completed ? "done" : i === completed && status === "running" ? "running" : "pending";
                    return (
                        <div className={`rc-stage rc-stage--${state}`} key={label}>
                            <span className="rc-stage__circle">
                                {state === "done" ? (
                                    <IconCheck size={14} />
                                ) : state === "running" ? (
                                    <span className="rc-stage__spinner" />
                                ) : (
                                    i + 1
                                )}
                            </span>
                            <span className="rc-stage__label">{label}</span>
                            <span className="rc-stage__status">
                                {state === "done" ? "Done" : state === "running" ? "Processing…" : "Waiting"}
                            </span>
                        </div>
                    );
                })}
            </div>

            {isDone && (
                <div className="rc-success">
                    <span className="rc-success__check">
                        <IconCheck size={15} />
                    </span>
                    <span className="rc-success__label">Report ready — {reportType}_Report</span>
                    {resultUrl && (
                        <a className="rc-btn rc-btn--primary rc-btn--sm" href={resultUrl} target="_blank" rel="noreferrer">
                            Open report
                            <IconExternalLink size={13} />
                        </a>
                    )}
                    <button type="button" className="rc-btn rc-btn--outline rc-btn--sm" onClick={onRunAgain}>
                        Run again
                    </button>
                </div>
            )}

            {isDone && warnings.length > 0 && (
                <div className="rc-gen-warnings">
                    <div className="rc-gen-warnings__head">
                        <IconInfo size={14} />
                        <span>{warnings.length} warning{warnings.length > 1 ? "s" : ""} while building — the report was still created</span>
                    </div>
                    <ul className="rc-gen-warnings__list">
                        {warnings.map((w, i) => (
                            <li className="rc-gen-warnings__item" key={i}>
                                {w}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}
