import { IconArrowRight, IconSpinner } from "./icons";

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
    onToggle(tacticNum: number, id: BreakdownId): void;
    onBuild(): void;
}

/** Screen 3 — per-tactic analysis toggles. Cosmetic for now (no backend effect). */
export function StepBreakdowns({ tactics, building, onToggle, onBuild }: Props) {
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

            <div className="rc-actions rc-actions--end">
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
            </div>
        </div>
    );
}
