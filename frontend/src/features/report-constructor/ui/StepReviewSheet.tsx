import { Fragment, useState } from "react";
import type { ReportType } from "@/shared/api/types";
import {
    IconArrowLeft,
    IconCheck,
    IconExternalLink,
    IconInfo,
    IconRefresh,
    IconSheet,
    IconSpinner,
} from "./icons";

export interface ReviewRow {
    tactic: string;
    /** null → render a "needs input" pill. */
    lineId: string | null;
    spendPlan: string | null;
    spendFact: string | null;
    impressionsPlan: string | null;
    impressionsFact: string | null;
}

interface Props {
    reportType: ReportType;
    sheetUrl: string | null;
    rows: ReviewRow[];
    /** True while the summary figures are being re-read from the sheet. */
    refreshing: boolean;
    /** True when at least one tactic has a breakdown section enabled, so its slides need manual data. */
    breakdownsEnabled: boolean;
    onRefresh(): void;
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
export function StepReviewSheet({
    reportType,
    sheetUrl,
    rows,
    refreshing,
    breakdownsEnabled,
    onRefresh,
    onConfirm,
    onBack,
}: Props) {
    // When breakdown slides are enabled, their data can only come from the sheet's "Breakdowns" tab,
    // which the backend does not fill — the user must enter it by hand. Gate Confirm on an explicit ack.
    const [breakdownsAck, setBreakdownsAck] = useState(false);
    const confirmDisabled = breakdownsEnabled && !breakdownsAck;

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
                        {rows.length} tactic{rows.length === 1 ? "" : "s"} · edited values in Sheets? Refresh to pull
                        them in
                    </div>
                </div>
                {sheetUrl && (
                    <div className="rc-banner__actions">
                        <button
                            type="button"
                            className="rc-banner__btn"
                            onClick={onRefresh}
                            disabled={refreshing}
                        >
                            {refreshing ? <IconSpinner size={14} /> : <IconRefresh size={14} />}
                            {refreshing ? "Refreshing…" : "Refresh"}
                        </button>
                        <a className="rc-banner__link" href={sheetUrl} target="_blank" rel="noreferrer">
                            Open in Sheets
                            <IconExternalLink size={14} />
                        </a>
                    </div>
                )}
            </div>

            <div className="rc-sheet">
                <div className="rc-sheet__grid">
                    <div className="rc-sheet__head">Tactic</div>
                    <div className="rc-sheet__head">Line ID</div>
                    <div className="rc-sheet__head">Spend (Plan)</div>
                    <div className="rc-sheet__head">Spend (Fact)</div>
                    <div className="rc-sheet__head" title="Impressions for CPM, Clicks for CPC, Views for CPV">
                        Units (Plan)
                    </div>
                    <div className="rc-sheet__head" title="Impressions for CPM, Clicks for CPC, Views for CPV">
                        Units (Fact)
                    </div>

                    {rows.map((r, i) => (
                        // Cells are direct grid children so columns line up; tactic labels repeat
                        // across rows, so the index is the stable key.
                        <Fragment key={i}>
                            <div className="rc-sheet__cell rc-sheet__cell--name">{r.tactic}</div>
                            <Cell value={r.lineId} addLabel="add ID" />
                            <Cell value={r.spendPlan} addLabel="add" />
                            <Cell value={r.spendFact} addLabel="add" />
                            <Cell value={r.impressionsPlan} addLabel="add" />
                            <Cell value={r.impressionsFact} addLabel="add" />
                        </Fragment>
                    ))}
                </div>
            </div>

            {breakdownsEnabled && (
                <div className="rc-banner rc-banner--warn">
                    <span className="rc-banner__icon">
                        <IconInfo size={18} />
                    </span>
                    <div className="rc-banner__text">
                        <div className="rc-banner__title">Breakdown slides need data filled in by hand</div>
                        <div className="rc-banner__sub">
                            The breakdown sections you enabled won't populate automatically. Open the sheet's{" "}
                            <b>Breakdowns</b> tab and fill in every table highlighted in 🍋‍🟩 lime for each tactic before
                            generating — anything left blank will come out empty in the deck.
                        </div>
                        {sheetUrl && (
                            <a
                                className="rc-btn rc-btn--outline rc-btn--sm rc-banner__cta"
                                href={sheetUrl}
                                target="_blank"
                                rel="noreferrer"
                            >
                                <IconSheet size={14} />
                                Fill breakdowns
                                <IconExternalLink size={14} />
                            </a>
                        )}
                        <label className="rc-banner__ack">
                            <input
                                type="checkbox"
                                checked={breakdownsAck}
                                onChange={(e) => setBreakdownsAck(e.target.checked)}
                            />
                            <span>I've filled in the breakdown data on the Breakdowns tab.</span>
                        </label>
                    </div>
                </div>
            )}

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
                    <button
                        type="button"
                        className="rc-btn rc-btn--primary"
                        disabled={confirmDisabled}
                        onClick={onConfirm}
                    >
                        Confirm — it's correct
                    </button>
                </div>
            </div>
        </div>
    );
}
