interface Props {
    step: number;
    total: number;
    label: string;
}

/** The full-block generating state shown while the form-area is hidden. */
export function GeneratingOverlay({ step, total, label }: Props) {
    const pct = total > 0 ? Math.round((step / total) * 100) : 0;
    return (
        <div className="generating-state visible">
            <div className="gen-spinner" />
            <div className="gen-label">Building your presentation…</div>
            <div className="gen-sublabel">Claude reads your data and structures the slides.</div>
            <div className="gen-progress-bar-wrap">
                <div className="gen-progress-bar" style={{ width: `${pct}%` }} />
            </div>
            <div className="gen-progress-text">{label || "Working…"}</div>
        </div>
    );
}
