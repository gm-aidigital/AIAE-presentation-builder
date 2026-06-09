import { AppShell } from "@/app/AppShell";
import { PageHeader } from "@/shared/ui/PageHeader";
import { WizardProvider } from "@/shared/wizard/WizardContext";
import { BriefStep } from "./BriefStep";
import { ReportTypeStep } from "./ReportTypeStep";
import { MediaPlanStep } from "./MediaPlanStep";
import { ElevateStep } from "./ElevateStep";
import { LineItemMatchingStep } from "./LineItemMatchingStep";
import { PlaceholderPreviewStep } from "./PlaceholderPreviewStep";
import { GenerateStep } from "./GenerateStep";
import "./report-constructor.css";

/** The full Report Constructor wizard (brief → connect → match → preview → generate). */
export function ReportConstructorPage() {
    return (
        <WizardProvider>
            <AppShell appName="Report Constructor">
                <PageHeader
                    title="Report Constructor"
                    subtitle="Turn a campaign Google Sheet into a generated Slides deck."
                />
                <div className="rc">
                    <BriefStep />
                    <ReportTypeStep />
                    <MediaPlanStep />
                    <ElevateStep />
                    <LineItemMatchingStep />
                    <PlaceholderPreviewStep />
                    <GenerateStep />
                </div>
            </AppShell>
        </WizardProvider>
    );
}
