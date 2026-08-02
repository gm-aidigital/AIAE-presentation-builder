import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { evenPacedBudget, firstDeliveryDateByLineItem, type EvenPacing } from "../lib/evenPacing";
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

/**
 * The media plan's own budget/volume for a tactic, e.g. "plan: $1,500 · 250K units".
 * The plan's rate and buy type are omitted here — they have their own cells in the row.
 */
function planLine(b: TacticBudget | undefined): string {
    if (!b) return "";
    const seg: string[] = [];
    if (b.amount > 0) seg.push(usd.format(Math.round(b.amount)));
    if (b.units > 0) seg.push(`${compactUnits(b.units)} units`);
    return seg.length > 0 ? `plan: ${seg.join(" · ")}` : "";
}

const money = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** How an evenly-paced row got its number, e.g. "120d × $83.33 → 30d in month". */
function evenLine(even: EvenPacing): string {
    return `${even.flightDays}d × $${money.format(even.daily)} → ${even.windowDays}d`;
}

/**
 * Step-2 dialog where the user enters, per tactic, the monthly budget being paced,
 * the buy type and the final negotiated rate. These drive the EOM deck's pacing
 * figures, so the wizard will not advance until every tactic is filled in.
 *
 * "Evenly paced" replaces the typed budgets with derived ones: each tactic's plan spend
 * spread across the days it is really live (first delivery in the raw data → media-plan
 * Flight End) and billed to the reporting window.
 */
export function PacingModal({ open, onClose, onConfirm }: Props) {
    const { mapping, setPacing, mediaPlan, elevate, dateStart, dateEnd } = useWizard();
    const rows: MappingEntry[] = useMemo(() => mapping ?? [], [mapping]);
    const seeded = useRef(false);
    const [evenly, setEvenly] = useState(false);
    // Budgets typed by hand before "Evenly paced" was switched on, so switching it back off
    // returns the user's own numbers instead of leaving the derived ones behind.
    const manual = useRef<Record<number, number | undefined>>({});

    // Media-plan figures aligned to the mapping order, so repeated tactic names each keep their own row.
    const budgets = useMemo(
        () => extractTacticBudgets(mediaPlan?.sheetRows ?? null, rows.map((m) => m.tacticName)),
        [mediaPlan, rows]
    );

    // First day each matched line item actually delivered — the real flight start, which is
    // what the even spread has to divide by (a tactic that launched late spends faster).
    const firstDates = useMemo(
        () => (open ? firstDeliveryDateByLineItem(elevate?.adjRows ?? null) : {}),
        [open, elevate]
    );

    const evens = useMemo(
        () =>
            rows.map((row, i) =>
                evenPacedBudget({
                    spendPlan: budgets[i]?.amount ?? 0,
                    firstDate: row.lineItemId ? firstDates[row.lineItemId.trim()] ?? null : null,
                    flightEnd: budgets[i]?.flightEnd ?? null,
                    windowStart: dateStart,
                    windowEnd: dateEnd,
                })
            ),
        [rows, budgets, firstDates, dateStart, dateEnd]
    );

    const evenAvailable = evens.some((e) => e !== null);

    // While the toggle is on the derived numbers own the budget column, including after the
    // reporting window or the mapping changes underneath.
    useEffect(() => {
        if (!open || !evenly) return;
        let changed = false;
        const next = rows.map((m, i) => {
            const budget = evens[i]?.budget;
            if (budget === undefined || m.monthlyBudget === budget) return m;
            changed = true;
            return { ...m, monthlyBudget: budget };
        });
        if (changed) setPacing(next);
    }, [open, evenly, rows, evens, setPacing]);

    const toggleEvenly = useCallback(() => {
        if (!evenly) {
            manual.current = Object.fromEntries(rows.map((m) => [m.tacticNum, m.monthlyBudget]));
            setEvenly(true);
            return;
        }
        setEvenly(false);
        setPacing(rows.map((m) => ({ ...m, monthlyBudget: manual.current[m.tacticNum] })));
    }, [evenly, rows, setPacing]);

    // Buy type and rate come from the media plan's "Rate Type" / "Unit Price" columns. The buy type is
    // authoritative (the row shows it read-only, so state must always match what is displayed); the rate is
    // only seeded, since the user may override it with the final negotiated one.
    useEffect(() => {
        if (!open) {
            seeded.current = false;
            setEvenly(false);
            return;
        }
        if (rows.length === 0) return;
        let changed = false;
        const next = rows.map((m, i) => {
            const patch: Partial<Pick<MappingEntry, "rateType" | "unitPrice">> = {};
            const rateType = normalizeRateType(budgets[i]?.rateType);
            if (rateType && m.rateType !== rateType) patch.rateType = rateType;
            // Seeded once per opening: after that an empty rate field is the user clearing it to
            // retype, and refilling it mid-edit would fight them.
            if (!seeded.current && m.unitPrice === undefined && budgets[i]?.unitPrice) {
                patch.unitPrice = budgets[i]!.unitPrice;
            }
            if (Object.keys(patch).length === 0) return m;
            changed = true;
            return { ...m, ...patch };
        });
        seeded.current = true;
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
                            Enter the budget being paced this month for every tactic. Buy type and rate are read
                            from the media plan (Rate Type / Unit Price) — the buy type is fixed, the rate comes
                            pre-filled and can be overwritten if the final negotiated one differs.
                        </div>
                    </div>
                </div>

                {rows.length > 0 && (
                    <div className="pacing-even">
                        <label className="pacing-even__toggle">
                            <input
                                type="checkbox"
                                checked={evenly}
                                disabled={!evenAvailable}
                                onChange={toggleEvenly}
                            />
                            <span className="pacing-even__label">Evenly paced</span>
                        </label>
                        <span className="pacing-even__note">
                            {evenAvailable
                                ? "Fills each budget from the plan spend spread over the tactic's live days — first day with delivery in the raw data through the plan's Flight End — billed to the reporting window."
                                : "Needs a Flight End in the media plan, matched line items with delivery in the raw data, and confirmed flight dates."}
                        </span>
                    </div>
                )}

                <div className="match-modal-body">
                    {rows.length === 0 ? (
                        <div className="match-empty">Match the line items first — pacing is entered per tactic.</div>
                    ) : (
                        <div className="pacing-table">
                            <div className="pacing-table__head">
                                <span>Tactic</span>
                                <span>Monthly budget</span>
                                <span>Buy type</span>
                                <span>Rate (editable)</span>
                            </div>
                            {rows.map((row, idx) => {
                                const done = pacingRowComplete(row);
                                const units = estimatedUnits(row);
                                const plan = planLine(budgets[idx]);
                                const planRateType = normalizeRateType(budgets[idx]?.rateType);
                                const planPrice = budgets[idx]?.unitPrice ?? 0;
                                const even = evens[idx];
                                const evenRow = evenly && !!even;
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

                                        <div className="pacing-row__budget">
                                            <label className="pacing-field">
                                                <span className="pacing-field__prefix">$</span>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    className="pacing-field__input"
                                                    placeholder="0"
                                                    readOnly={evenRow}
                                                    title={
                                                        evenRow
                                                            ? "Derived from the even pacing — turn the toggle off to type a budget"
                                                            : undefined
                                                    }
                                                    aria-label={`Monthly budget for ${row.tacticName}`}
                                                    value={row.monthlyBudget ?? ""}
                                                    onChange={(e) =>
                                                        update(idx, {
                                                            monthlyBudget:
                                                                e.target.value === ""
                                                                    ? undefined
                                                                    : Number(e.target.value),
                                                        })
                                                    }
                                                />
                                            </label>
                                            <div className="pacing-row__hint">
                                                {evenRow
                                                    ? evenLine(even)
                                                    : evenly
                                                      ? "no even pacing — enter by hand"
                                                      : ""}
                                            </div>
                                        </div>

                                        {planRateType ? (
                                            <div
                                                className="pacing-buytype"
                                                title="Read from the media plan's Rate Type column"
                                            >
                                                <span className="pacing-buytype__value">{planRateType}</span>
                                                <span className="pacing-buytype__src">from plan</span>
                                            </div>
                                        ) : (
                                            <select
                                                className="pacing-select pacing-select--missing"
                                                aria-label={`Buy type for ${row.tacticName}`}
                                                title="No Rate Type in the media plan for this tactic — pick one"
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
                                        )}

                                        <div className="pacing-row__rate">
                                            <label className="pacing-field">
                                                <span className="pacing-field__prefix">$</span>
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="0.01"
                                                    className="pacing-field__input"
                                                    placeholder={planPrice > 0 ? planPrice.toFixed(2) : "0.00"}
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
