import { useEffect, useMemo } from "react";
import type { MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { extractTacticBudgets, normalizeRateType, type TacticBudget } from "../lib/mediaPlanBudget";
import { estimatedUnits, pacingComplete, pacingRowComplete } from "../lib/pacing";
import { IconBolt, IconCheck } from "./icons";

const RATE_TYPES = ["CPM", "CPC", "CPV"] as const;

/** Unit the rate is charged per — shown under the rate field so the number is unambiguous. */
const RATE_UNIT: Record<string, string> = {
    CPM: "per 1,000 impressions",
    CPC: "per click",
    CPV: "per view",
};

interface Props {
    open: boolean;
    onClose(): void;
    onConfirm(): void;
}

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });
const grouped = new Intl.NumberFormat("en-US");

function compactUnits(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace(/\.0$/, "")}M`;
    if (n >= 10_000) return `${Math.round(n / 1000)}K`;
    return grouped.format(Math.round(n));
}

/** The media plan's own figures for a tactic, e.g. "plan: $1,500 · 250K @ $6 CPM". */
function planLine(b: TacticBudget | undefined): string {
    if (!b) return "";
    const seg: string[] = [];
    if (b.amount > 0) seg.push(usd.format(Math.round(b.amount)));
    if (b.units > 0) {
        const rate = b.rateType ? ` @ $${b.unitPrice} ${b.rateType.toUpperCase()}` : "";
        seg.push(`${compactUnits(b.units)}${rate}`);
    }
    return seg.length > 0 ? `plan: ${seg.join(" · ")}` : "";
}

/**
 * Step-2 dialog where the user enters, per tactic, the monthly budget being paced,
 * the buy type and the final negotiated rate. These drive the EOM deck's pacing
 * figures, so the wizard will not advance until every tactic is filled in.
 */
export function PacingModal({ open, onClose, onConfirm }: Props) {
    const { mapping, setPacing, mediaPlan } = useWizard();
    const rows: MappingEntry[] = useMemo(() => mapping ?? [], [mapping]);

    // Media-plan figures aligned to the mapping order, so repeated tactic names each keep their own row.
    const budgets = useMemo(
        () => extractTacticBudgets(mediaPlan?.sheetRows ?? null, rows.map((m) => m.tacticName)),
        [mediaPlan, rows]
    );

    // Seed buy type and rate from the media plan for rows the user hasn't touched. A value shown only as
    // an input fallback is never submitted, so the defaults are written into real state instead.
    useEffect(() => {
        if (!open || rows.length === 0) return;
        let changed = false;
        const next = rows.map((m, i) => {
            const patch: Partial<Pick<MappingEntry, "rateType" | "unitPrice">> = {};
            if (m.rateType === undefined) {
                const rateType = normalizeRateType(budgets[i]?.rateType);
                if (rateType) patch.rateType = rateType;
            }
            if (m.unitPrice === undefined && budgets[i]?.unitPrice) {
                patch.unitPrice = budgets[i]!.unitPrice;
            }
            if (Object.keys(patch).length === 0) return m;
            changed = true;
            return { ...m, ...patch };
        });
        if (changed) setPacing(next);
    }, [open, rows, budgets, setPacing]);

    useEffect(() => {
        document.body.style.overflow = open ? "hidden" : "";
        return () => {
            document.body.style.overflow = "";
        };
    }, [open]);

    function update(idx: number, patch: Partial<Pick<MappingEntry, "rateType" | "unitPrice" | "monthlyBudget">>) {
        setPacing(rows.map((m, i) => (i === idx ? { ...m, ...patch } : m)));
    }

    const ready = rows.filter(pacingRowComplete).length;
    const allReady = pacingComplete(rows);

    return (
        <div
            className={`match-overlay${open ? " visible" : ""}`}
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div className="match-modal pacing-modal">
                <div className="match-modal-head">
                    <div className="match-modal-icon">
                        <IconBolt />
                    </div>
                    <div>
                        <div className="match-modal-title">Pacing &amp; rates</div>
                        <div className="match-modal-desc">
                            For every tactic, enter the budget being paced this month, how it's bought and the
                            final rate. These drive the pacing figures in the deck — the media plan's own numbers
                            are shown as a reference under each tactic.
                        </div>
                    </div>
                </div>

                <div className="match-modal-body">
                    {rows.length === 0 ? (
                        <div className="match-empty">Match the line items first — pacing is entered per tactic.</div>
                    ) : (
                        <div className="pacing-table">
                            <div className="pacing-table__head">
                                <span>Tactic</span>
                                <span>Monthly budget</span>
                                <span>Buy type</span>
                                <span>Rate</span>
                            </div>
                            {rows.map((row, idx) => {
                                const done = pacingRowComplete(row);
                                const units = estimatedUnits(row);
                                const plan = planLine(budgets[idx]);
                                return (
                                    <div
                                        key={row.tacticNum}
                                        className={`pacing-row${done ? " pacing-row--done" : ""}`}
                                    >
                                        <div className="pacing-row__tactic">
                                            <span className="match-tactic-num">{row.tacticNum}</span>
                                            <div className="pacing-row__names">
                                                <div className="match-tactic-name">{row.tacticName}</div>
                                                {plan && <div className="match-tactic-meta">{plan}</div>}
                                            </div>
                                        </div>

                                        <label className="pacing-field">
                                            <span className="pacing-field__prefix">$</span>
                                            <input
                                                type="number"
                                                min="0"
                                                className="pacing-field__input"
                                                placeholder="0"
                                                aria-label={`Monthly budget for ${row.tacticName}`}
                                                value={row.monthlyBudget ?? ""}
                                                onChange={(e) =>
                                                    update(idx, {
                                                        monthlyBudget:
                                                            e.target.value === "" ? undefined : Number(e.target.value),
                                                    })
                                                }
                                            />
                                        </label>

                                        <select
                                            className="pacing-select"
                                            aria-label={`Buy type for ${row.tacticName}`}
                                            value={row.rateType ?? ""}
                                            onChange={(e) =>
                                                update(idx, {
                                                    rateType: (e.target.value || undefined) as
                                                        | MappingEntry["rateType"]
                                                        | undefined,
                                                })
                                            }
                                        >
                                            <option value="">Select…</option>
                                            {RATE_TYPES.map((rt) => (
                                                <option key={rt} value={rt}>
                                                    {rt}
                                                </option>
                                            ))}
                                        </select>

                                        <div className="pacing-row__rate">
                                            <label className="pacing-field">
                                                <span className="pacing-field__prefix">$</span>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="0.01"
                                                    className="pacing-field__input"
                                                    placeholder="0.00"
                                                    aria-label={`Rate for ${row.tacticName}`}
                                                    value={row.unitPrice ?? ""}
                                                    onChange={(e) =>
                                                        update(idx, {
                                                            unitPrice:
                                                                e.target.value === ""
                                                                    ? undefined
                                                                    : Number(e.target.value),
                                                        })
                                                    }
                                                />
                                            </label>
                                            <div className="pacing-row__hint">
                                                {units > 0
                                                    ? `≈ ${compactUnits(units)} ${
                                                          row.rateType === "CPC"
                                                              ? "clicks"
                                                              : row.rateType === "CPV"
                                                                ? "views"
                                                                : "imps"
                                                      }`
                                                    : RATE_UNIT[row.rateType ?? ""] ?? ""}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="match-modal-foot">
                    <span className="match-stats">
                        {rows.length > 0 ? `${ready}/${rows.length} tactics ready` : ""}
                    </span>
                    <div className="pacing-foot__actions">
                        {!allReady && rows.length > 0 && (
                            <span className="pacing-foot__warn">
                                Fill the budget, buy type and rate for every tactic
                            </span>
                        )}
                        <button className="btn-match-cancel" onClick={onClose}>
                            Cancel
                        </button>
                        <button className="btn-match-confirm" disabled={!allReady} onClick={onConfirm}>
                            <IconCheck />
                            Confirm pacing
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
