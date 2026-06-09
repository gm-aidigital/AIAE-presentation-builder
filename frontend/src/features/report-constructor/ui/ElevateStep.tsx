import { useState } from "react";
import { ELEVATE_TAB, readSheetTab } from "@/shared/api/sheets";
import { useWizard } from "@/shared/wizard/WizardContext";
import { SheetConnectStep } from "./SheetConnectStep";

/** Connect the Elevate / BigQuery-export sheet (Basic tab). */
export function ElevateStep() {
    const { elevate, connectElevate, disconnectElevate } = useWizard();
    const [pulling, setPulling] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function pull(url: string) {
        setPulling(true);
        setError(null);
        try {
            const basic = await readSheetTab(url, ELEVATE_TAB);
            if (!basic.ok) {
                setError(
                    basic.error === "tab_not_found"
                        ? `Tab "${ELEVATE_TAB}" not found in this sheet.`
                        : basic.error || "Could not read the Elevate sheet."
                );
                return;
            }
            connectElevate({
                title: basic.title ?? "",
                sheetId: basic.sheetId ?? "",
                rows: basic.rows,
                cols: basic.cols,
                headers: basic.headers,
                preview: basic.preview,
                adjRows: basic.rawRows,
            });
        } catch (e) {
            setError(e instanceof Error ? e.message : "Could not read the Elevate sheet.");
        } finally {
            setPulling(false);
        }
    }

    return (
        <SheetConnectStep
            index={4}
            title="Connect the Elevate / BigQuery sheet"
            description="Reads the Basic tab — provides BQ metrics and gates chart generation."
            sheet={elevate}
            pulling={pulling}
            error={error}
            onPull={pull}
            onDisconnect={disconnectElevate}
        />
    );
}
