import { useEffect, useMemo, useState } from "react";
import type { LineItemMatchResult, MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { extractTacticBudgets, namingTail, normalizeRateType, type TacticBudget } from "../lib/mediaPlanBudget";
import { IconCheck, IconInfo, IconLink2, IconRefresh, IconSpinner } from "./icons";

const RATE_TYPES = ["CPM", "CPC", "CPV"] as const;

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

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

function compactUnits(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace(/\.0$/, "")}M`;
    if (n >= 10_000) return `${Math.round(n / 1000)}K`;
    return new Intl.NumberFormat("en-US").format(n);
}

/** One compact line for the left panel, e.g. "$64,000 · 9.1M @ $7 CPM". */
function budgetLine(b: TacticBudget): string {
    const seg: string[] = [];
    if (b.amount > 0) seg.push(usd.format(Math.round(b.amount)));
    if (b.units > 0) {
        const rate = b.rateType ? ` @ $${b.unitPrice} ${b.rateType.toUpperCase()}` : "";
        seg.push(`${compactUnits(b.units)}${rate}`);
    }
    return seg.join(" · ");
}

export function MatchModal({ open, matchData, running, onClose, onRun, onConfirm }: Props) {
    const { mapping, setMapping, mediaPlan, reportType } = useWizard();
    const isEom = reportType === "EOM";
    const [dragOver, setDragOver] = useState<number | null>(null);

    // Budget/volume per tactic, aligned to the mapping order so duplicated
    // tactic labels ("Programmatic Display" twice) each get their own row.
    const budgets = useMemo(
        () => extractTacticBudgets(mediaPlan?.sheetRows ?? null, (mapping ?? []).map((m) => m.tacticName)),
        [mediaPlan, mapping]
    );

    // Writes the media-plan rate/price into real mapping state for any row that doesn't have its own
    // yet. The <select>/<input> `value`s below fall back to the same numbers for *display*, but a
    // fallback shown in a controlled input is never actually submitted unless the user touches the
    // control — so without this, a row whose rate type already looks right would silently send
    // rateType/unitPrice as undefined.
    useEffect(() => {
        if (!isEom || !mapping || mapping.length === 0) {
            return;
        }
        let changed = false;
        const next = mapping.map((m, i) => {
            const patch: Partial<Pick<MappingEntry, "rateType" | "unitPrice">> = {};
            if (m.rateType === undefined) {
                const rateType = normalizeRateType(budgets[i]?.rateType);
                if (rateType) {
                    patch.rateType = rateType;
                }
            }
            if (m.unitPrice === undefined && budgets[i]?.unitPrice) {
                patch.unitPrice = budgets[i]!.unitPrice;
            }
            if (Object.keys(patch).length === 0) {
                return m;
            }
            changed = true;
            return { ...m, ...patch };
        });
        if (changed) {
            setMapping(next);
        }
    }, [isEom, budgets, mapping, setMapping]);

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
    function updateRate(idx: number, patch: Partial<Pick<MappingEntry, "rateType" | "unitPrice" | "monthlyBudget">>) {
        setMapping(rows.map((m, i) => (i === idx ? { ...m, ...patch } : m)));
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
                                                {budgets[idx] && budgetLine(budgets[idx] as TacticBudget) && (
                                                    <div
                                                        className="match-tactic-meta"
                                                        title="Planned budget · volume from the media plan"
                                                    >
                                                        {budgetLine(budgets[idx] as TacticBudget)}
                                                    </div>
                                                )}
                                                {isEom && (
                                                    <div className="match-rate-row">
                                                        <select
                                                            className="match-rate-row__select"
                                                            title="Rate type"
                                                            value={row.rateType ?? normalizeRateType(budgets[idx]?.rateType) ?? ""}
                                                            onChange={(e) =>
                                                                updateRate(idx, {
                                                                    rateType: (e.target.value || undefined) as
                                                                        | MappingEntry["rateType"]
                                                                        | undefined,
                                                                })
                                                            }
                                                        >
                                                            <option value="">Rate…</option>
                                                            {RATE_TYPES.map((rt) => (
                                                                <option key={rt} value={rt}>
                                                                    {rt}
                                                                </option>
                                                            ))}
                                                        </select>
                                                        <input
                                                            type="number"
                                                            className="match-rate-row__input"
                                                            title="Monthly budget"
                                                            placeholder="Monthly budget"
                                                            value={row.monthlyBudget ?? ""}
                                                            onChange={(e) =>
                                                                updateRate(idx, {
                                                                    monthlyBudget: e.target.value === "" ? undefined : Number(e.target.value),
                                                                })
                                                            }
                                                        />
                                                        <input
                                                            type="number"
                                                            className="match-rate-row__input"
                                                            title="Final unit price"
                                                            placeholder="Final unit price"
                                                            value={row.unitPrice ?? budgets[idx]?.unitPrice ?? ""}
                                                            onChange={(e) =>
                                                                updateRate(idx, {
                                                                    unitPrice: e.target.value === "" ? undefined : Number(e.target.value),
                                                                })
                                                            }
                                                        />
                                                    </div>
                                                )}
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
                                                    const tail = namingTail(info.naming, id);
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
                                                            {tail && (
                                                                <span className="match-id-card-tail" title={info.naming}>
                                                                    {tail}
                                                                </span>
                                                            )}
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
