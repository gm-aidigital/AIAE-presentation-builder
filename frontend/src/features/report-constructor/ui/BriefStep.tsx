import { useWizard } from "@/shared/wizard/WizardContext";
import { StepCard } from "./StepCard";

export function BriefStep() {
    const { brief, setBrief } = useWizard();
    return (
        <StepCard
            index={1}
            title="Campaign brief"
            description="Free-text context for the report."
            done={brief.trim().length > 0}
        >
            <textarea
                className="rc-textarea"
                rows={4}
                placeholder="Describe the campaign, goals, audience…"
                value={brief}
                onChange={(e) => setBrief(e.target.value)}
            />
        </StepCard>
    );
}
