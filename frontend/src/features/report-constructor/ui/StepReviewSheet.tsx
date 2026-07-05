import { Fragment } from "react";
import type { ReportType } from "@/shared/api/types";
import { IconArrowLeft, IconCheck, IconExternalLink, IconSheet } from "./icons";

export interface ReviewRow {
    tactic: string;
    /** null → render a "needs input" pill. */
    lineId: string | null;
    spend: string | null;
    impressions: string | null;
    clicks: string | null;
    ctr: string | null;
}

interface Props {
    reportType: ReportType;
    sheetUrl: string | null;
    rows: ReviewRow[];
    onConfirm(): void;
    onBack(): void;
}

function Cell({ value, addLabel }: { value: string | null; addLabel: string }) {
    if (value) return <div className="rc-sheet__cell rc-sheet__cell--num">{value}</div>;
    return (
        <div className="rc-sheet__cell">
            <span className="rc-sheet__add">{addLabel}</span>
        </div>
    );
}

/** Screen 4 — review the assembled Google Sheet, fill gaps, confirm. */
export function StepReviewSheet({ reportType, sheetUrl, rows, onConfirm, onBack }: Props) {
    return (
        <div className="rc-content">
            <div className="rc-section-head">
                <div className="rc-section-head__num">04</div>
                <div>
                    <h2 className="rc-section-head__title">Review the generated sheet</h2>
                    <p className="rc-section-head__sub">
                        We collected everything into one Google Sheet. Fill any gaps, then confirm it's correct.
                    </p>
                </div>
            </div>

            <div className="rc-banner">
                <span className="rc-banner__icon">
                    <IconSheet size={17} />
                </span>
                <div className="rc-banner__text">
                    <div className="rc-banner__title">{reportType}_Report · data collected</div>
                    <div className="rc-banner__sub">
                        {rows.length} tactic{rows.length === 1 ? "" : "s"} · orange cells still need your input
                    </div>
                </div>
                {sheetUrl && (
                    <a className="rc-banner__link" href={sheetUrl} target="_blank" rel="noreferrer">
                        Open in Sheets
                        <IconExternalLink size={14} />
                    </a>
                )}
            </div>

            <div className="rc-sheet">
                <div className="rc-sheet__grid">
                    <div className="rc-sheet__head">Tactic</div>
                    <div className="rc-sheet__head">Line ID</div>
                    <div className="rc-sheet__head">Spend</div>
                    <div className="rc-sheet__head">Impressions</div>
                    <div className="rc-sheet__head">Clicks</div>
                    <div className="rc-sheet__head">CTR</div>

                    {rows.map((r, i) => (
                        // Cells are direct grid children so columns line up; tactic labels repeat
                        // across rows, so the index is the stable key.
                        <Fragment key={i}>
                            <div className="rc-sheet__cell rc-sheet__cell--name">{r.tactic}</div>
                            <Cell value={r.lineId} addLabel="add ID" />
                            <Cell value={r.spend} addLabel="add" />
                            <Cell value={r.impressions} addLabel="add" />
                            <Cell value={r.clicks} addLabel="add" />
                            <Cell value={r.ctr} addLabel="add" />
                        </Fragment>
                    ))}
                </div>
            </div>

            <div className="rc-confirm-bar">
                <div className="rc-confirm-bar__left">
                    <span className="rc-confirm-bar__check">
                        <IconCheck size={15} />
                    </span>
                    <div>
                        <div className="rc-confirm-bar__title">Everything look right?</div>
                        <div className="rc-confirm-bar__sub">Confirm to lock the data and move to generation.</div>
                    </div>
                </div>
                <div className="rc-confirm-bar__actions">
                    <button type="button" className="rc-btn rc-btn--outline" onClick={onBack}>
                        <IconArrowLeft size={16} />
                        Back
                    </button>
                    <button type="button" className="rc-btn rc-btn--primary" onClick={onConfirm}>
                        Confirm — it's correct
                    </button>
                </div>
            </div>
        </div>
    );
}
