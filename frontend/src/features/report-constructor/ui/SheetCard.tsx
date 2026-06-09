import { useState, type ReactNode } from "react";
import { isGoogleSheetUrl } from "@/shared/api/sheets";
import type { ConnectedSheet } from "@/shared/wizard/WizardContext";
import { IconRefresh, IconSheet, IconSpinner, IconX } from "./icons";

interface Props {
    num: string;
    id: string;
    title: string;
    desc: string;
    hint: ReactNode;
    authBanner?: ReactNode;
    sheet: ConnectedSheet | null;
    pulling: boolean;
    requiredError: boolean;
    requiredMsg: string;
    footer?: ReactNode;
    onPull(url: string): void;
    onDisconnect(): void;
}

function PreviewTable({ sheet }: { sheet: ConnectedSheet }) {
    const { headers, preview, rows: total } = sheet;
    const cols = headers.length || (preview[0] ? preview[0].length : 0);
    if (!preview || preview.length === 0 || cols === 0) return null;
    const dataRows = preview[0] && preview[0][0] === headers[0] ? preview.slice(1) : preview;
    const shown = dataRows.slice(0, 4);
    return (
        <div className="preview-wrap">
            <table className="preview-table">
                <thead>
                    <tr>
                        {Array.from({ length: cols }, (_, i) => (
                            <th key={i}>{headers[i] || ""}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {shown.map((row, ri) => (
                        <tr key={ri}>
                            {Array.from({ length: cols }, (_, ci) => (
                                <td key={ci}>{row[ci] || ""}</td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
            {total > 5 && <div className="preview-more">+ {total - 5} more rows</div>}
        </div>
    );
}

/** Reusable connect card (sections 02 Media Plan & 03 Elevate). */
export function SheetCard(props: Props) {
    const { num, id, title, desc, hint, authBanner, sheet, pulling, requiredError, requiredMsg, footer, onPull, onDisconnect } = props;
    const [url, setUrl] = useState("");
    const canPull = isGoogleSheetUrl(url) && !pulling;

    return (
        <div className="section-card" id={id}>
            <div className="card-header">
                <div className="card-num">{num}</div>
                <div>
                    <div className="card-title">{title}</div>
                    <div className="card-desc">{desc}</div>
                </div>
            </div>
            <div className="card-body">
                <div className="sheets-block">
                    {authBanner}
                    <div className="sheets-hint">{hint}</div>
                    <div className="input-row">
                        <input
                            type="text"
                            className="input-field"
                            placeholder="https://docs.google.com/spreadsheets/d/…"
                            value={url}
                            onChange={(e) => setUrl(e.target.value)}
                        />
                        <button className="btn-connect" disabled={!canPull} onClick={() => onPull(url)}>
                            {pulling ? <IconSpinner /> : <IconRefresh />}
                            {pulling ? "Fetching…" : "Pull data"}
                        </button>
                    </div>
                    <div className={`sheets-status${sheet ? " visible" : ""}`}>
                        <div className="sheets-status-icon">
                            <IconSheet />
                        </div>
                        <div className="sheets-status-text">
                            <div className="sheets-status-name">
                                {sheet ? `${sheet.title} · ${sheet.tab}` : "—"}
                            </div>
                            <div className="sheets-status-meta">
                                {sheet
                                    ? `${sheet.rows} rows · ${sheet.cols} cols · ${sheet.tabsCount} sheet(s)`
                                    : "—"}
                            </div>
                        </div>
                        <button
                            className="btn-disconnect"
                            onClick={() => {
                                setUrl("");
                                onDisconnect();
                            }}
                        >
                            <IconX />
                        </button>
                    </div>
                    {sheet && <PreviewTable sheet={sheet} />}
                    {footer}
                </div>
                <div className={`field-error${requiredError ? " visible" : ""}`}>{requiredMsg}</div>
            </div>
        </div>
    );
}
