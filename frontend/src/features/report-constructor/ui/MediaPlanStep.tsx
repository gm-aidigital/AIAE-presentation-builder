import { useState } from "react";
import { MEDIA_PLAN_PRIMARY_TAB, readSheetTab } from "@/shared/api/sheets";
import { useWizard } from "@/shared/wizard/WizardContext";
import { SheetConnectStep } from "./SheetConnectStep";

/** Connect the Media Plan sheet: Proposal (awaited) + optional tabs (fire-and-forget). */
export function MediaPlanStep() {
    const { mediaPlan, connectMediaPlan, updateMediaPlanTabs, disconnectMediaPlan } = useWizard();
    const [pulling, setPulling] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function pull(url: string) {
        setPulling(true);
        setError(null);
        try {
            const proposal = await readSheetTab(url, MEDIA_PLAN_PRIMARY_TAB);
            if (!proposal.ok) {
                setError(
                    proposal.error === "tab_not_found"
                        ? `Tab "${MEDIA_PLAN_PRIMARY_TAB}" not found in this sheet.`
                        : proposal.error || "Could not read the Media Plan sheet."
                );
                return;
            }
            connectMediaPlan({
                title: proposal.title ?? "",
                sheetId: proposal.sheetId ?? "",
                rows: proposal.rows,
                cols: proposal.cols,
                headers: proposal.headers,
                preview: proposal.preview,
                sheetRows: proposal.rawRows,
                audienceRows: [],
                estimatesRows: [],
                geoRows: [],
            });
            // Optional tabs — fire-and-forget; failures are swallowed (legacy behavior).
            void loadOptionalTabs(url);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Could not read the Media Plan sheet.");
        } finally {
            setPulling(false);
        }
    }

    function loadOptionalTabs(url: string) {
        readSheetTab(url, "Audience&Inventory")
            .then((r) => {
                if (r.ok && r.rawRows) updateMediaPlanTabs({ audienceRows: r.rawRows });
            })
            .catch((err) => console.warn("Audience&Inventory:", err));

        readSheetTab(url, "Estimates")
            .then(async (r) => {
                if (r.ok && r.rawRows) {
                    updateMediaPlanTabs({ estimatesRows: r.rawRows });
                    return;
                }
                if (r.error === "tab_not_found") {
                    // Legacy fallback: Estimates → Proposal.
                    const fb = await readSheetTab(url, MEDIA_PLAN_PRIMARY_TAB).catch(() => null);
                    if (fb && fb.ok) updateMediaPlanTabs({ estimatesRows: fb.rawRows });
                }
            })
            .catch((err) => console.warn("Estimates:", err));

        readSheetTab(url, "Geo")
            .then((r) => {
                if (r.ok && r.rawRows) updateMediaPlanTabs({ geoRows: r.rawRows });
            })
            .catch((err) => console.warn("Geo:", err));
    }

    return (
        <SheetConnectStep
            index={3}
            title="Connect the Media Plan sheet"
            description="Reads Proposal (+ optional Audience&Inventory, Estimates, Geo)."
            sheet={mediaPlan}
            pulling={pulling}
            error={error}
            onPull={pull}
            onDisconnect={disconnectMediaPlan}
        />
    );
}
