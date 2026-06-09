import type { ReportType } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { StepCard } from "./StepCard";

const OPTIONS: ReportType[] = ["EOC", "EOM"];

export function ReportTypeStep() {
    const { reportType, setReportType } = useWizard();
    return (
        <StepCard
            index={2}
            title="Report type"
            description="Cosmetic — fills the {{report_type}} placeholder only; the catalog is identical for both."
            done
        >
            <div className="rc-segmented" role="group" aria-label="Report type">
                {OPTIONS.map((opt) => (
                    <button
                        key={opt}
                        type="button"
                        className={`rc-segmented__opt${reportType === opt ? " rc-segmented__opt--active" : ""}`}
                        onClick={() => setReportType(opt)}
                    >
                        {opt}
                    </button>
                ))}
            </div>
        </StepCard>
    );
}
