import { useState } from "react";
import type { PreviewResult, Source } from "@/shared/api/types";
import { IconChevron, IconEye } from "./icons";

interface Props {
    data: PreviewResult;
    onClose(): void;
}

function srcClass(source: Source): string {
    return source === "adj" ? "adj" : source === "sheet" ? "sheet" : "none";
}
function srcLabel(source: Source): string {
    return source === "adj" ? "adj ↑" : source === "sheet" ? "sheet" : "—";
}
function groupTitle(key: string): string {
    if (key === "sheet") return "Media Plan";
    if (key === "adj") return "Adjustments";
    return key;
}

export function PreviewPanel({ data, onClose }: Props) {
    const [open, setOpen] = useState<Set<number>>(() => new Set([0]));
    const [labelsOpen, setLabelsOpen] = useState(false);

    const allOk = data.stats.found === data.stats.total;
    const labelGroups = Object.entries(data.allLabels).filter(([, list]) => list.length > 0);

    function toggle(i: number) {
        setOpen((prev) => {
            const next = new Set(prev);
            if (next.has(i)) next.delete(i);
            else next.add(i);
            return next;
        });
    }

    return (
        <div className="preview-fullpanel visible" id="preview-panel">
            <div className="preview-fp-head">
                <div className="preview-fp-title">
                    <IconEye size={18} />
                    Placeholder Map
                </div>
                <div className="preview-fp-meta">
                    <span className={`preview-fp-badge ${allOk ? "ok" : "warn"}`}>
                        {data.stats.found}/{data.stats.total} mapped
                    </span>
                    <button className="btn-signout" onClick={onClose}>
                        Close ×
                    </button>
                </div>
            </div>

            <div className="preview-sections">
                {data.sections.map((section, si) => {
                    const phs = section.placeholders;
                    const found = phs.filter((p) => p.source !== "not_found").length;
                    const total = phs.length;
                    const isOpen = open.has(si);
                    const badgeCls = found === 0 ? "ps-badge-none" : found === total ? "ps-badge-ok" : "ps-badge-warn";
                    return (
                        <div className="preview-section" key={section.title}>
                            <div className={`ps-head${isOpen ? " open" : ""}`} onClick={() => toggle(si)}>
                                <div className="ps-head-left">
                                    <span className="ps-head-title">{section.title}</span>
                                    <span className="ps-head-count">{total} placeholders</span>
                                </div>
                                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                    <span className={badgeCls}>
                                        {found}/{total}
                                    </span>
                                    <IconChevron className="ps-chevron" />
                                </div>
                            </div>
                            <div className={`ps-body${isOpen ? " open" : ""}`}>
                                <table className="ph-table">
                                    <tbody>
                                        {phs.map((ph) => {
                                            const hasVal = ph.source !== "not_found";
                                            return (
                                                <tr key={ph.key} title={`Looks for: ${ph.label}`}>
                                                    <td className="ph-col-key">{ph.key}</td>
                                                    <td className={`ph-col-val${hasVal ? "" : " missing"}`}>
                                                        {hasVal ? ph.value : "— not found"}
                                                    </td>
                                                    <td className="ph-col-src">
                                                        <span className={`ph-src-chip ${srcClass(ph.source)}`}>
                                                            {srcLabel(ph.source)}
                                                        </span>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    );
                })}

                {labelGroups.length > 0 && (
                    <div className="labels-accordion">
                        <div className="la-head" onClick={() => setLabelsOpen((v) => !v)}>
                            All labels found in your sheets
                            <IconChevron
                                className=""
                            />
                        </div>
                        <div className={`la-body${labelsOpen ? " open" : ""}`}>
                            {labelGroups.map(([key, list]) => (
                                <div key={key}>
                                    <div className="la-sub">
                                        {groupTitle(key)} ({list.length} labels)
                                    </div>
                                    <div className="la-chips">
                                        {list.map((l, i) => (
                                            <span className="la-chip" key={i} title={l.value}>
                                                {l.label}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
