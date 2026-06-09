import type { Source } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { usePreviewPlaceholders } from "../api/usePreviewPlaceholders";
import { StepCard } from "./StepCard";

const SOURCE_LABEL: Record<Source, string> = {
    adj: "manual",
    sheet: "sheet",
    ui: "ui",
    claude: "claude",
    not_found: "—",
};

export function PlaceholderPreviewStep() {
    const { brief, reportType, mediaPlan, elevate, mapping } = useWizard();
    const preview = usePreviewPlaceholders();
    const ready = !!mediaPlan && !!elevate;

    function run() {
        if (!mediaPlan || !elevate) return;
        preview.mutate({
            brief,
            reportType,
            sheetRows: mediaPlan.sheetRows,
            adjRows: elevate.adjRows,
            audienceRows: mediaPlan.audienceRows,
            estimatesRows: mediaPlan.estimatesRows,
            geoRows: mediaPlan.geoRows,
            lineItemMapping: mapping ?? undefined,
        });
    }

    const data = preview.data;
    return (
        <StepCard
            index={6}
            title="Placeholder preview"
            description="Optional — compute the placeholder→value map (no Drive writes)."
        >
            <button
                type="button"
                className="rc-btn rc-btn--secondary"
                disabled={!ready || preview.isPending}
                onClick={run}
            >
                {preview.isPending ? "Computing…" : data ? "Refresh preview" : "Preview placeholders"}
            </button>
            {!ready && <p className="rc-muted">Connect both sheets first.</p>}
            {preview.isError && <p className="rc-error" role="alert">{preview.error?.message}</p>}

            {data && (
                <div>
                    <p className="rc-stats">
                        {data.stats.found} / {data.stats.total} placeholders resolved
                    </p>
                    {data.sections.map((section) => (
                        <details key={section.title} className="rc-section">
                            <summary>
                                {section.title}
                                <span className="rc-section__count">{section.placeholders.length}</span>
                            </summary>
                            <table className="rc-ph">
                                <tbody>
                                    {section.placeholders.map((ph) => (
                                        <tr key={ph.key}>
                                            <td className="rc-ph__label">{ph.label}</td>
                                            <td>{ph.value}</td>
                                            <td>
                                                <span className={`rc-badge rc-badge--${ph.source}`}>
                                                    {SOURCE_LABEL[ph.source]}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </details>
                    ))}
                </div>
            )}
        </StepCard>
    );
}
