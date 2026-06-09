import { useEffect, useRef, useState } from "react";
import { ErrorAlert } from "@/shared/ui/ErrorAlert";
import { LoadingBlock } from "@/shared/ui/LoadingBlock";
import type { LineItemMatchResult, MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { useMatchLineItems } from "../api/useMatchLineItems";
import { StepCard } from "./StepCard";

/** Display-only: split on `_`, show segments 2..4; else truncate to 55. */
function abbreviateNaming(naming: string): string {
    if (!naming) return "—";
    const segs = naming.split("_");
    if (segs.length >= 5) return segs.slice(2, 5).join(" · ");
    return naming.length > 55 ? `${naming.slice(0, 55)}…` : naming;
}

export function LineItemMatchingStep() {
    const { mediaPlan, elevate, matchConfirmed, setMapping, confirmMatch } = useWizard();
    const match = useMatchLineItems();
    const [result, setResult] = useState<LineItemMatchResult | null>(null);
    const [draft, setDraft] = useState<MappingEntry[]>([]);
    const [open, setOpen] = useState(false);
    const ranFor = useRef<string>("");

    const bothConnected = !!mediaPlan && !!elevate;
    const runKey = mediaPlan && elevate ? `${mediaPlan.sheetId}|${elevate.sheetId}` : "";

    // Auto-run the match once per connected sheet pair; cache the result.
    useEffect(() => {
        if (!mediaPlan || !elevate) {
            setResult(null);
            ranFor.current = "";
            return;
        }
        if (ranFor.current === runKey || match.isPending) return;
        ranFor.current = runKey;
        match.mutate(
            { bqRows: elevate.adjRows, planRows: mediaPlan.sheetRows },
            {
                onSuccess: (r) => {
                    setResult(r);
                    setDraft(r.mapping);
                    setMapping(r.mapping);
                },
            }
        );
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [runKey]);

    function assign(tacticNum: number, id: string) {
        setDraft((prev) =>
            prev.map((m) => {
                if (m.tacticNum === tacticNum) {
                    return { ...m, lineItemId: id, namingSample: result?.idNamings[id]?.naming };
                }
                if (m.lineItemId === id) {
                    return { ...m, lineItemId: undefined, namingSample: undefined };
                }
                return m;
            })
        );
    }

    function clearTactic(tacticNum: number) {
        setDraft((prev) =>
            prev.map((m) =>
                m.tacticNum === tacticNum ? { ...m, lineItemId: undefined, namingSample: undefined } : m
            )
        );
    }

    function confirm() {
        setMapping(draft);
        confirmMatch();
        setOpen(false);
    }

    const assignedIds = new Set(draft.map((m) => m.lineItemId).filter(Boolean) as string[]);
    const pool = (result?.uniqueIds ?? []).filter((id) => !assignedIds.has(id));

    return (
        <StepCard
            index={5}
            title="Match line items"
            description="Auto-maps media-plan tactics to BigQuery line-item IDs; drag to fix, then confirm."
            done={matchConfirmed}
        >
            {!bothConnected && <p className="rc-muted">Connect both sheets to match line items.</p>}
            {bothConnected && match.isPending && <LoadingBlock label="Matching line items…" />}
            {bothConnected && match.isError && (
                <ErrorAlert message={match.error?.message ?? "Matching failed."} />
            )}

            {result && (
                <div className="rc-match-summary">
                    <span className="rc-stats">
                        {result.autoMatchCount} / {result.mapping.length} auto-matched
                    </span>
                    {result.warnings.length > 0 && (
                        <span className="rc-muted">{result.warnings.length} warning(s)</span>
                    )}
                    <button type="button" className="rc-btn rc-btn--secondary" onClick={() => setOpen(true)}>
                        {matchConfirmed ? "Review mapping" : "Review & edit"}
                    </button>
                    {!matchConfirmed && (
                        <button
                            type="button"
                            className="rc-btn rc-btn--primary"
                            disabled={draft.length === 0}
                            onClick={confirm}
                        >
                            Confirm mapping
                        </button>
                    )}
                </div>
            )}

            {open && result && (
                <div className="rc-modal-overlay" role="dialog" aria-modal="true" onClick={() => setOpen(false)}>
                    <div className="rc-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="rc-modal__head">
                            <h3>Line-item matching</h3>
                            <button type="button" className="rc-link-btn" onClick={() => setOpen(false)}>
                                Close
                            </button>
                        </div>

                        <div className="rc-match">
                            <div>
                                {draft.map((m) => (
                                    <div
                                        key={m.tacticNum}
                                        className="rc-tactic"
                                        onDragOver={(e) => e.preventDefault()}
                                        onDrop={(e) => {
                                            e.preventDefault();
                                            const id = e.dataTransfer.getData("text/plain");
                                            if (id) assign(m.tacticNum, id);
                                        }}
                                    >
                                        <div className="rc-tactic__name">
                                            {m.tacticNum}. {m.tacticName}
                                        </div>
                                        <div className="rc-dropzone">
                                            {m.lineItemId ? (
                                                <>
                                                    <span>
                                                        <strong>{m.lineItemId}</strong> ·{" "}
                                                        {abbreviateNaming(m.namingSample ?? "")}
                                                    </span>
                                                    <button
                                                        type="button"
                                                        className="rc-link-btn"
                                                        onClick={() => clearTactic(m.tacticNum)}
                                                    >
                                                        Remove
                                                    </button>
                                                </>
                                            ) : (
                                                <span>
                                                    {m.expectedChannel
                                                        ? `Drop an ID (channel: ${m.expectedChannel})`
                                                        : "Drop an ID here"}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>

                            <div className="rc-pool">
                                <h4>Unused IDs ({pool.length})</h4>
                                {pool.length === 0 && <p className="rc-muted">All IDs assigned.</p>}
                                {pool.map((id) => {
                                    const naming = result.idNamings[id];
                                    return (
                                        <div
                                            key={id}
                                            className="rc-chip"
                                            draggable
                                            onDragStart={(e) => e.dataTransfer.setData("text/plain", id)}
                                        >
                                            <strong>{id}</strong>
                                            <span className="rc-chip__channel">
                                                {" · "}
                                                {abbreviateNaming(naming?.naming ?? "")}
                                                {naming?.channel ? ` · ${naming.channel}` : ""}
                                            </span>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        <div className="rc-match-summary" style={{ marginTop: "16px" }}>
                            <span className="rc-muted">
                                Unmatched tactics are allowed — they produce no data plus a warning.
                            </span>
                            <button
                                type="button"
                                className="rc-btn rc-btn--primary"
                                disabled={draft.length === 0}
                                onClick={confirm}
                            >
                                Confirm mapping
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </StepCard>
    );
}
