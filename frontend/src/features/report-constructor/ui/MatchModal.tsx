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
                            Слева — тактики из медиаплана. Справа — все Line Item ID найденные в BQ. Перетащи
                            нужный ID на тактику или нажми «Re-run» чтобы система попробовала сопоставить
                            автоматически.
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
                                ? "Анализируем данные…"
                                : "Подключи Media Plan и BQ-выгрузку, затем нажми «Run Matching»"}
                        </div>
                    ) : (
                        <div className="match-layout">
                            <div className="match-tactics-panel">
                                <div className="match-panel-label">
                                    <span>Тактики из медиаплана</span>
                                    <span>
                                        {matched}/{rows.length}
                                    </span>
                                </div>
                                {rows.length === 0 && (
                                    <div className="match-empty">Тактики не найдены под ячейкой «Media»</div>
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
                                                        title="Убрать ID"
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
                                    <span>Line Item IDs из BQ</span>
                                    <span>{allIds.length} уникальных</span>
                                </div>
                                <div className="match-ids-pool">
                                    <div className="match-ids-pool-inner">
                                        {allIds.length === 0 ? (
                                            <div className="match-drag-hint">ID не найдены в BQ-выгрузке</div>
                                        ) : pool.length === 0 ? (
                                            <div className="match-drag-hint" style={{ padding: "24px" }}>
                                                Все ID распределены ✓
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
                                                <div className="match-drag-hint">← Перетащи на тактику</div>
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
                                ⚠ {unmatched.length} тактика без ID — данные будут показаны как 0:{" "}
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
