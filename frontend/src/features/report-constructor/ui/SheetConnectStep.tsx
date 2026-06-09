import { useState } from "react";
import { isGoogleSheetUrl } from "@/shared/api/sheets";
import type { ConnectedSheet } from "@/shared/wizard/WizardContext";
import { StepCard } from "./StepCard";

interface Props {
    index: number;
    title: string;
    description: string;
    sheet: ConnectedSheet | null;
    pulling: boolean;
    error: string | null;
    onPull(url: string): void;
    onDisconnect(): void;
}

const MAX_PREVIEW_COLS = 8;
const MAX_PREVIEW_ROWS = 4;

/** Presentational connect card: URL input + Pull, or connected status + preview. */
export function SheetConnectStep(props: Props) {
    const { index, title, description, sheet, pulling, error, onPull, onDisconnect } = props;
    const [url, setUrl] = useState("");
    const canPull = isGoogleSheetUrl(url) && !pulling;

    if (sheet) {
        const headerKey = sheet.headers.join("");
        const body = sheet.preview
            .filter((row) => row.join("") !== headerKey)
            .slice(0, MAX_PREVIEW_ROWS);
        const hidden = Math.max(0, sheet.rows - body.length - 1);
        const cols = sheet.headers.slice(0, MAX_PREVIEW_COLS);

        return (
            <StepCard index={index} title={title} description={description} done>
                <div className="rc-status">
                    <div>
                        <strong className="rc-status__title">{sheet.title || "Connected sheet"}</strong>
                        <span className="rc-status__meta">
                            {sheet.rows} rows · {sheet.cols} cols
                        </span>
                    </div>
                    <button type="button" className="rc-btn rc-btn--secondary" onClick={onDisconnect}>
                        Disconnect
                    </button>
                </div>
                {cols.length > 0 && (
                    <div className="rc-preview">
                        <table>
                            <thead>
                                <tr>
                                    {cols.map((h, i) => (
                                        <th key={i}>{h || "—"}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {body.map((row, ri) => (
                                    <tr key={ri}>
                                        {cols.map((_, ci) => (
                                            <td key={ci}>{row[ci] ?? ""}</td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        {hidden > 0 && <p className="rc-preview__more">+{hidden} more rows</p>}
                    </div>
                )}
            </StepCard>
        );
    }

    return (
        <StepCard index={index} title={title} description={description}>
            <div className="rc-field">
                <input
                    className="rc-input"
                    type="url"
                    placeholder="https://docs.google.com/spreadsheets/d/…"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                />
                <button
                    type="button"
                    className="rc-btn rc-btn--primary"
                    disabled={!canPull}
                    onClick={() => onPull(url)}
                >
                    {pulling ? "Pulling…" : "Pull data"}
                </button>
            </div>
            {error && <p className="rc-error" role="alert">{error}</p>}
        </StepCard>
    );
}
