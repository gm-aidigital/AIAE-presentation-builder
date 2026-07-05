import { useState } from "react";
import { IconArrowLeft, IconArrowRight, IconInfo, IconSpinner } from "./icons";

export const BREAKDOWNS = [
    { id: "tp", label: "Top Publishers" },
    { id: "ca", label: "Creative Analysis" },
    { id: "geo", label: "Geo Performance" },
    { id: "aud", label: "Audience Analysis" },
    { id: "dev", label: "Device Breakdown" },
] as const;

export type BreakdownId = (typeof BREAKDOWNS)[number]["id"];
export type BreakdownState = Record<BreakdownId, boolean>;

export interface TacticView {
    tacticNum: number;
    name: string;
    channel: string;
    meta: string;
    on: BreakdownState;
}

interface Props {
    tactics: TacticView[];
    building: boolean;
    /** True once a sheet has already been assembled — enables Continue and gates rebuilds. */
    sheetBuilt: boolean;
    onToggle(tacticNum: number, id: BreakdownId): void;
    onBuild(): void;
    onContinue(): void;
    onBack(): void;
}

/** Screen 3 — per-tactic analysis toggles. Cosmetic for now (no backend effect). */
export function StepBreakdowns({ tactics, building, sheetBuilt, onToggle, onBuild, onContinue, onBack }: Props) {
    // Rebuilding overwrites the assembled sheet, so gate it behind a data-loss confirm.
    const [confirmRebuild, setConfirmRebuild] = useState(false);

    return (
        <div className="rc-content">
            <div className="rc-section-head">
                <div className="rc-section-head__num">03</div>
                <div>
                    <h2 className="rc-section-head__title">Breakdowns per tactic</h2>
                    <p className="rc-section-head__sub">
                        Toggle which analysis sections to include for each tactic in the report.
                    </p>
                </div>
            </div>

            {tactics.length === 0 ? (
                <div className="rc-empty">No tactics found in the confirmed mapping.</div>
            ) : (
                <div className="rc-tactics">
                    {tactics.map((t) => {
                        const count = BREAKDOWNS.filter((b) => t.on[b.id]).length;
                        return (
                            <div className="rc-tactic" key={t.tacticNum}>
                                <div className="rc-tactic__head">
                                    <div>
                                        <div className="rc-tactic__title-row">
                                            <span className="rc-tactic__name">{t.name}</span>
                                            {t.channel && <span className="rc-tactic__channel">{t.channel}</span>}
                                        </div>
                                        {t.meta && <div className="rc-tactic__meta">{t.meta}</div>}
                                    </div>
                                    <span className="rc-tactic__count">{count} of 5 included</span>
                                </div>
                                <div className="rc-tactic__grid">
                                    {BREAKDOWNS.map((b) => {
                                        const on = t.on[b.id];
                                        return (
                                            <div className="rc-toggle-row" key={b.id}>
                                                <span
                                                    className={`rc-toggle-row__label${
                                                        on ? " rc-toggle-row__label--on" : ""
                                                    }`}
                                                >
                                                    {b.label}
                                                </span>
                                                <button
                                                    type="button"
                                                    role="switch"
                                                    aria-checked={on}
                                                    aria-label={b.label}
                                                    className={`rc-switch${on ? " rc-switch--on" : ""}`}
                                                    onClick={() => onToggle(t.tacticNum, b.id)}
                                                >
                                                    <span className="rc-switch__knob" />
                                                </button>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            <div className="rc-actions rc-actions--split">
                <button type="button" className="rc-btn rc-btn--outline" disabled={building} onClick={onBack}>
                    <IconArrowLeft size={16} />
                    Back
                </button>

                {sheetBuilt ? (
                    <div className="rc-actions__group">
                        <button
                            type="button"
                            className="rc-btn rc-btn--outline"
                            disabled={building || tactics.length === 0}
                            onClick={() => setConfirmRebuild(true)}
                        >
                            {building ? <IconSpinner size={14} /> : null}
                            {building ? "Rebuilding…" : "Rebuild sheet"}
                        </button>
                        <button
                            type="button"
                            className="rc-btn rc-btn--primary"
                            disabled={building}
                            onClick={onContinue}
                        >
                            Continue
                            <IconArrowRight size={16} />
                        </button>
                    </div>
                ) : (
                    <button
                        type="button"
                        className="rc-btn rc-btn--primary"
                        disabled={building || tactics.length === 0}
                        onClick={onBuild}
                    >
                        {building ? <IconSpinner size={14} /> : null}
                        {building ? "Building the sheet…" : "Build the sheet"}
                        {!building && <IconArrowRight size={16} />}
                    </button>
                )}
            </div>

            {confirmRebuild && (
                <div
                    className="rc-overlay"
                    onClick={(e) => {
                        if (e.target === e.currentTarget) setConfirmRebuild(false);
                    }}
                >
                    <div className="rc-overlay__card">
                        <div className="rc-overlay__warn">
                            <IconInfo size={20} />
                        </div>
                        <div className="rc-overlay__title">Rebuild the sheet?</div>
                        <div className="rc-overlay__sub">
                            This assembles a fresh Google Sheet from the current settings and{" "}
                            <b>discards any edits you made in the existing sheet</b>. This can't be undone.
                        </div>
                        <div className="rc-overlay__actions">
                            <button
                                type="button"
                                className="rc-btn rc-btn--outline rc-btn--sm"
                                onClick={() => setConfirmRebuild(false)}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="rc-btn rc-btn--primary rc-btn--sm"
                                onClick={() => {
                                    setConfirmRebuild(false);
                                    onBuild();
                                }}
                            >
                                Rebuild anyway
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
