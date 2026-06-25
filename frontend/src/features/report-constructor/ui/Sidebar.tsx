import { useWizard } from "@/shared/wizard/WizardContext";
import { IconBolt, IconCheck, IconChecklist, IconClock, IconEye, IconMonitor, IconSheet, IconSpinner } from "./icons";

interface Props {
    resultUrl: string | null;
    previewLoading: boolean;
    generating: boolean;
    onPreview(): void;
    onGenerate(): void;
    onClear(): void;
}

export function Sidebar({ resultUrl, previewLoading, generating, onPreview, onGenerate, onClear }: Props) {
    const { brief, marketVolume, mediaPlan, elevate, matchConfirmed } = useWizard();
    const briefLen = brief.trim().length;
    const hasSheet = !!mediaPlan;
    const hasAdj = !!elevate;
    const hasMarketVolume = marketVolume.trim().length > 0;
    const genReady = briefLen > 0 && hasSheet && hasAdj && hasMarketVolume && matchConfirmed;
    const previewReady = hasSheet || hasAdj;
    const flow1 = hasSheet || hasAdj;
    const flow2 = (hasSheet || hasAdj) && briefLen > 0;
    const flow3 = briefLen > 0 && hasSheet && hasAdj;

    return (
        <div className="sidebar">
            <div className="status-card">
                <div className="status-head">
                    <IconChecklist />
                    <span>Input Status</span>
                </div>
                <div className="status-body">
                    <div className="status-row">
                        <span className="status-key">
                            <span className={`status-dot${briefLen > 0 ? " on" : ""}`} />
                            Campaign Brief
                        </span>
                        <span className={`status-val${briefLen > 0 ? " on" : ""}`}>
                            {briefLen > 0 ? `${briefLen.toLocaleString()} chars` : "Empty"}
                        </span>
                    </div>
                    <div className="status-row">
                        <span className="status-key">
                            <span className={`status-dot${hasMarketVolume ? " on" : ""}`} />
                            Market Volume
                        </span>
                        <span className={`status-val${hasMarketVolume ? " on" : ""}`}>
                            {hasMarketVolume ? marketVolume.trim() : "Empty"}
                        </span>
                    </div>
                    <div className="status-row">
                        <span className="status-key">
                            <span className={`status-dot${hasSheet ? " on" : ""}`} />
                            Media Plan
                        </span>
                        <span className={`status-val${hasSheet ? " on" : ""}`}>
                            {hasSheet ? mediaPlan!.title : "Not connected"}
                        </span>
                    </div>
                    <div className="status-row">
                        <span className="status-key">
                            <span className={`status-dot${hasAdj ? " on" : ""}`} />
                            Elevate Dashboard
                        </span>
                        <span className={`status-val${hasAdj ? " on" : ""}`}>
                            {hasAdj ? elevate!.title : "Not connected"}
                        </span>
                    </div>
                </div>
            </div>

            <div className="flow-card">
                <div className="flow-head">How it works</div>
                <div className="flow-body">
                    <div className={`flow-item${flow1 ? " active" : ""}`}>
                        <div className="flow-icon">
                            <IconSheet size={11} />
                        </div>
                        Reads Sheets data
                    </div>
                    <div className="flow-arrow">↓</div>
                    <div className={`flow-item${flow2 ? " active" : ""}`}>
                        <div className="flow-icon">
                            <IconClock />
                        </div>
                        Claude processes
                    </div>
                    <div className="flow-arrow">↓</div>
                    <div className={`flow-item${flow3 ? " active" : ""}`}>
                        <div className="flow-icon">
                            <IconMonitor size={11} />
                        </div>
                        Fills Slides template
                    </div>
                </div>
            </div>

            <div className="gen-card">
                <div className="gen-top">
                    <p>Connect both sheets, then preview placeholders before generating.</p>
                    <button className="btn-preview" disabled={!previewReady || previewLoading} onClick={onPreview}>
                        {previewLoading ? <IconSpinner size={14} /> : <IconEye />}
                        {previewLoading ? "Loading…" : "Preview Placeholders"}
                    </button>
                    <button className="btn-generate" disabled={!genReady || generating} onClick={onGenerate}>
                        <IconBolt />
                        {generating ? "Generating…" : "Generate Slides"}
                    </button>
                    <div
                        className={`gen-lock-notice${
                            briefLen > 0 && hasSheet && hasAdj && !matchConfirmed ? " visible" : ""
                        }`}
                    >
                        ⚠ Confirm the line item mapping first
                    </div>
                </div>
                <div className="gen-bottom">
                    <button className="btn-clear" onClick={onClear}>
                        Clear all fields
                    </button>
                </div>
            </div>

            <div className={`result-card${resultUrl ? " visible" : ""}`}>
                <div className="result-head">
                    <IconCheck size={16} />
                    <span className="result-head-title">Slides ready!</span>
                </div>
                <div className="result-body">
                    <div style={{ fontSize: "12px", color: "var(--text-muted)", lineHeight: 1.6 }}>
                        The presentation is ready and saved to Google Slides.
                    </div>
                    <a className="result-link" href={resultUrl || "#"} target="_blank" rel="noreferrer">
                        <IconMonitor />
                        Open in Google Slides →
                    </a>
                </div>
            </div>
        </div>
    );
}
