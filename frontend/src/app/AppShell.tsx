import { ReactNode } from "react";
import { AppHeader } from "../shared/ui/AppHeader";
import "../shared/ui/app-shell.css";

interface Props {
    children: ReactNode;
    appName?: string;
}

/** Content shell below the top header — max-width centered product surface. */
export function AppShell({ children, appName = "Replit MVP" }: Props) {
    return (
        <div className="app-shell">
            <AppHeader appName={appName} />
            <main className="app-shell__main">{children}</main>
        </div>
    );
}
