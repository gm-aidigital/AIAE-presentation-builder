import { useEffect, useState } from "react";
import type { LineItemMatchResult, MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { IconCheck, IconInfo, IconLink2, IconRefresh, IconSpinner } from "./icons";

interface Props {
    open: boolean;
    matchData: LineItemMatchResult | null;
    running: boolean;
    onClose(): void;
    onRun(): void;
    onConfirm(): void;
}

function abbreviateNaming(naming: string): string {
    if (!naming) return "";
    const parts = naming.split("_");
    if (parts.length >= 5) return parts.slice(2, 5).join(" · ");
    return naming.substring(0, 55) + (naming.length > 55 ? "…" : "");
}

export function MatchModal({ open, matchData, running, onClose, onRun, onConfirm }: Props) {
    const { mapping, setMapping } = useWizard();
    const [dragOver, setDragOver] = useState<number | null>(null);

    useEffect(() => {
        document.body.style.overflow = open ? "hidden" : "";
        return () => {
            document.body.style.overflow = "";
        };
    }, [open]);

    const rows: MappingEntry[] = mapping ?? [];
    const idNamings = matchData?.idNamings ?? {};
    const allIds = matchData?.uniqueIds ?? [];
    const usedIds = new Set(rows.map((m) => m.lineItemId).filter(Boolean) as string[]);
    const pool = allIds.filter((id) => !usedIds.has(id));
    const matched = rows.filter((m) => m.lineItemId).length;
    const unmatched = rows.filter((m) => !m.lineItemId);

    function assign(idx: number, id: string) {
        setMapping(
            rows.map((m, i) => {
                if (i === idx) {
                    return { ...m, lineItemId: id, namingSample: idNamings[id]?.naming, autoMatched: false };
                }
                if (m.lineItemId === id) {
                    return { ...m, lineItemId: undefined, namingSample: undefined, autoMatched: false };
                }
                return m;
            })
        );
    }
    function remove(idx: number) {
        setMapping(
            rows.map((m, i) =>
                i === idx ? { ...m, lineItemId: undefined, namingSample: undefined, autoMatched: false } : m
            )
        );
    }

    return (
        <div
            className={`match-overlay${open ? " visible" : ""}`}
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div className="match-modal">
                <div className="match-modal-head">
                    <div className="match-modal-icon">
                        <IconLink2 />
                    </div>
                    <div>
                        <div className="match-modal-title">Line Item Matching</div>
                        <div className="match-modal-desc">
                            Left — tactics from the media plan. Right — all Line Item IDs found in BQ. Drag the
                            right ID onto a tactic, or click "Re-run" to let the system try to match
                            automatically.
                        </div>
                    </div>
                </div>

                <div className="match-modal-body">
                    {matchData && matchData.warnings.length > 0 && (
                        <div className="match-warning visible">
                            <IconInfo size={14} />
                            <span>{matchData.warnings.join(" · ")}</span>
                        </div>
                    )}

                    {!matchData ? (
                        <div className="match-empty">
                            {running
                                ? "Analyzing data…"
                                : "Connect the Media Plan and BQ export, then click \"Run Matching\""}
                        </div>
                    ) : (
                        <div className="match-layout">
                            <div className="match-tactics-panel">
                                <div className="match-panel-label">
                                    <span>Tactics from media plan</span>
                                    <span>
                                        {matched}/{rows.length}
                                    </span>
                                </div>
                                {rows.length === 0 && (
                                    <div className="match-empty">No tactics found under the "Media" cell</div>
                                )}
                                {rows.map((row, idx) => {
                                    const hasId = !!row.lineItemId;
                                    const naming = hasId ? idNamings[row.lineItemId as string]?.naming ?? "" : "";
                                    return (
                                        <div
                                            key={row.tacticNum}
                                            className={`match-tactic-row${hasId ? " has-id" : ""}${
                                                dragOver === idx ? " drag-over" : ""
                                            }`}
                                            onDragOver={(e) => {
                                                e.preventDefault();
                                                setDragOver(idx);
                                            }}
                                            onDragLeave={() => setDragOver((d) => (d === idx ? null : d))}
                                            onDrop={(e) => {
                                                e.preventDefault();
                                                setDragOver(null);
                                                const id = e.dataTransfer.getData("text/plain");
                                                if (id) assign(idx, id);
                                            }}
                                        >
                                            <span className="match-tactic-num">{row.tacticNum}</span>
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div className="match-tactic-name">{row.tacticName}</div>
                                                {hasId && naming && (
                                                    <div
                                                        title={naming}
                                                        style={{
                                                            fontSize: "10px",
                                                            color: "var(--text-muted)",
                                                            fontFamily: "'DM Mono', monospace",
                                                            marginTop: "2px",
                                                            overflow: "hidden",
                                                            textOverflow: "ellipsis",
                                                            whiteSpace: "nowrap",
                                                        }}
                                                    >
                                                        {abbreviateNaming(naming)}
                                                    </div>
                                                )}
                                            </div>
                                            <div className="match-tactic-badge">
                                                <span className={`match-id-pill${hasId ? "" : " empty"}`}>
                                                    {hasId ? row.lineItemId : "drop here"}
                                                </span>
                                                {hasId && (
                                                    <button
                                                        className="match-remove-btn"
                                                        title="Remove ID"
                                                        onClick={() => remove(idx)}
                                                    >
                                                        ×
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>

                            <div className="match-ids-panel">
                                <div className="match-panel-label">
                                    <span>Line Item IDs from BQ</span>
                                    <span>{allIds.length} unique</span>
                                </div>
                                <div className="match-ids-pool">
                                    <div className="match-ids-pool-inner">
                                        {allIds.length === 0 ? (
                                            <div className="match-drag-hint">No IDs found in the BQ export</div>
                                        ) : pool.length === 0 ? (
                                            <div className="match-drag-hint" style={{ padding: "24px" }}>
                                                All IDs assigned ✓
                                            </div>
                                        ) : (
                                            <>
                                                {pool.map((id) => {
                                                    const info = idNamings[id] ?? { naming: "", channel: "", tactic: "" };
                                                    return (
                                                        <div
                                                            key={id}
                                                            className="match-id-card"
                                                            draggable
                                                            title={info.naming}
                                                            onDragStart={(e) =>
                                                                e.dataTransfer.setData("text/plain", id)
                                                            }
                                                        >
                                                            <span className="match-id-card-num">{id}</span>
                                                            {info.channel && (
                                                                <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                                                                    <span style={{ opacity: 0.6 }}>ch:</span> {info.channel}
                                                                </div>
                                                            )}
                                                            {info.tactic && (
                                                                <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                                                                    <span style={{ opacity: 0.6 }}>tactic:</span>{" "}
                                                                    {info.tactic}
                                                                </div>
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                                <div className="match-drag-hint">← Drag onto a tactic</div>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                <div className="match-modal-foot">
                    <div className="match-modal-foot-left">
                        <button className="btn-match-run" disabled={running} onClick={onRun}>
                            {running ? <IconSpinner /> : <IconRefresh />}
                            {matchData ? "Re-run" : "Run Matching"}
                        </button>
                        <span className="match-stats">
                            {matchData
                                ? `${matched}/${rows.length} matched · ${allIds.length} unique IDs in BQ`
                                : ""}
                        </span>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "8px" }}>
                        {unmatched.length > 0 && (
                            <div
                                style={{
                                    fontSize: "11px",
                                    color: "var(--orange)",
                                    textAlign: "right",
                                    maxWidth: "300px",
                                    lineHeight: 1.4,
                                }}
                            >
                                ⚠ {unmatched.length} tactic(s) without an ID — their data will show as 0:{" "}
                                {unmatched.map((m) => m.tacticName).join(", ")}
                            </div>
                        )}
                        <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                            <button className="btn-match-cancel" onClick={onClose}>
                                Cancel
                            </button>
                            <button
                                className="btn-match-confirm"
                                disabled={!matchData || rows.length === 0}
                                onClick={onConfirm}
                            >
                                <IconCheck />
                                Confirm Mapping
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
