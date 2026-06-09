import { useState } from "react";
import { useWizard } from "@/shared/wizard/WizardContext";
import { useCreateReportJob, useReportJob } from "../api/useReportJob";
import { StepCard } from "./StepCard";

export function GenerateStep() {
    const { brief, reportType, mediaPlan, elevate, mapping, matchConfirmed } = useWizard();
    const create = useCreateReportJob();
    const [jobId, setJobId] = useState<number | null>(null);
    const job = useReportJob(jobId);

    const briefOk = brief.trim().length > 0;
    const inputsReady = briefOk && !!mediaPlan && !!elevate;
    const ready = inputsReady && matchConfirmed;

    function start() {
        if (!ready || !mediaPlan || !elevate) return;
        create.mutate(
            {
                brief,
                reportType,
                sheetRows: mediaPlan.sheetRows,
                adjRows: elevate.adjRows,
                audienceRows: mediaPlan.audienceRows,
                estimatesRows: mediaPlan.estimatesRows,
                geoRows: mediaPlan.geoRows,
                lineItemMapping: mapping ?? undefined,
                bqSheetId: elevate.sheetId, // presence-gate for the chart step
            },
            { onSuccess: (id) => setJobId(id) }
        );
    }

    const data = job.data;
    const running = jobId != null && (!data || data.status === "queued" || data.status === "running");
    const done = data?.status === "done";
    const errored = data?.status === "error" || (jobId != null && job.isError);

    return (
        <StepCard
            index={7}
            title="Generate report"
            description="Builds the Google Slides deck (stub mode returns a mocked result without a real deck)."
            done={done}
        >
            {!ready && (
                <ul className="rc-gate">
                    <li className={briefOk ? "rc-gate--ok" : ""}>Campaign brief</li>
                    <li className={mediaPlan ? "rc-gate--ok" : ""}>Media Plan connected</li>
                    <li className={elevate ? "rc-gate--ok" : ""}>Elevate / BQ connected</li>
                    <li className={matchConfirmed ? "rc-gate--ok" : ""}>Line items confirmed</li>
                </ul>
            )}
            {inputsReady && !matchConfirmed && (
                <p className="rc-lock" role="status">
                    Сначала подтверди маппинг line items
                </p>
            )}

            {jobId == null && (
                <button
                    type="button"
                    className="rc-btn rc-btn--primary"
                    disabled={!ready || create.isPending}
                    onClick={start}
                >
                    {create.isPending ? "Starting…" : "Generate report"}
                </button>
            )}
            {create.isError && <p className="rc-error" role="alert">{create.error?.message}</p>}

            {running && (
                <div className="rc-progress">
                    <div className="rc-progress__bar">
                        <span style={{ width: `${((data?.step ?? 0) / (data?.total || 7)) * 100}%` }} />
                    </div>
                    <p className="rc-progress__label">
                        {data ? `Step ${data.step} / ${data.total} — ${data.label ?? "Working…"}` : "Starting…"}
                    </p>
                </div>
            )}

            {done && data && (
                <div className="rc-result">
                    <p className="rc-result__ok">Report ready ✓</p>
                    {data.slideUrl ? (
                        <a className="rc-btn rc-btn--primary" href={data.slideUrl} target="_blank" rel="noreferrer">
                            Open the deck
                        </a>
                    ) : (
                        <p className="rc-muted">
                            No deck URL — running in stub mode. Add a Google service account for a real deck.
                        </p>
                    )}
                    {data.warnings.length > 0 && (
                        <details className="rc-warn">
                            <summary>{data.warnings.length} warning(s)</summary>
                            <ul>
                                {data.warnings.map((w, i) => (
                                    <li key={i}>{w}</li>
                                ))}
                            </ul>
                        </details>
                    )}
                    <button type="button" className="rc-btn rc-btn--secondary" onClick={() => setJobId(null)}>
                        Generate again
                    </button>
                </div>
            )}

            {errored && (
                <div className="rc-result">
                    <p className="rc-error" role="alert">
                        {data?.error ?? job.error?.message ?? "Generation failed."}
                    </p>
                    <button type="button" className="rc-btn rc-btn--secondary" onClick={() => setJobId(null)}>
                        Try again
                    </button>
                </div>
            )}
        </StepCard>
    );
}
