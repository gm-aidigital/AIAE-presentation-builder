import { UserButton } from "@clerk/clerk-react";
import "./app-shell.css";

interface Props {
    appName: string;
}

/** Top app header — Elevate layout (no left sidebar). */
export function AppHeader({ appName }: Props) {
    return (
        <header className="app-header">
            <span className="app-header__brand">{appName}</span>
            <div className="app-header__actions">
                <UserButton afterSignOutUrl="/login" />
            </div>
        </header>
    );
}
