import { AppShell } from "./app/AppShell";
import { TemplateProfilePanel } from "./features/_template";
import { PageHeader } from "./shared/ui/PageHeader";
import "./App.css";

export default function App() {
    return (
        <AppShell appName="Replit MVP">
            <PageHeader
                title="Home"
                subtitle="Compose features from src/features/ — this page uses the _template example."
            />

            <section className="app">
                <TemplateProfilePanel />

                <p className="app__hint">
                    Copy <code>src/features/_template/</code> when adding your first real feature.
                </p>
            </section>
        </AppShell>
    );
}
